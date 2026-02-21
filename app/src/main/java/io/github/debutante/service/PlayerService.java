package io.github.debutante.service;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
import static io.github.debutante.service.BaseForegroundService.ACTION_STOP;
import static io.github.debutante.service.BaseForegroundService.STOP_SERVICE_REQUEST_CODE;
import static io.github.debutante.service.BaseForegroundService.buildNotification;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.squareup.picasso.Picasso;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.github.debutante.Debutante;
import io.github.debutante.MainActivity;
import io.github.debutante.R;
import io.github.debutante.adapter.MediaDescriptionAdapter;
import io.github.debutante.helper.DeviceHelper;
import io.github.debutante.helper.L;
import io.github.debutante.helper.MediaBrowserHelper;
import io.github.debutante.helper.Obj;
import io.github.debutante.helper.PlayerWrapper;
import io.github.debutante.helper.Scheduler;
import io.github.debutante.listeners.CastPlayerListener;
import io.github.debutante.listeners.CastSessionAvailabilityListener;
import io.github.debutante.listeners.ExoPlayerListener;
import io.github.debutante.listeners.MediaSessionNotificationListener;
import io.github.debutante.persistence.PlayerState;
import io.github.debutante.receivers.SyncAccountBroadcastReceiver;
import okhttp3.HttpUrl;

public class PlayerService extends MediaBrowserServiceCompat {

    public static final String EXTRA_SESSION_ID = "io.github.debutante.SESSION_ID";
    public static final String ACTION_PAUSE = PlayerService.class.getSimpleName() + "-ACTION_PAUSE";
    public static final String ACTION_PLAY = PlayerService.class.getSimpleName() + "-ACTION_PLAY";
    public static final String ACTION_MEDIA_BUTTON = "android.intent.action.MEDIA_BUTTON";
    public static final String ACTION_WAKE = PlayerService.class.getSimpleName() + "-ACTION_WAKE";
    private static final int NOTIFICATION_ID = Debutante.NOTIFICATION_ID - 1;
    public static final String ACTION_PREPARE = PlayerService.class.getSimpleName() + "-ACTION_PREPARE";

    private static final Object GLOBAL_LOCK = new Object();
    private static final String RECENT_ROOT = "RECENT_ROOT_ID";
    private PowerManager.WakeLock wakeLock;
    private static String sessionId = UUID.randomUUID().toString();
    private final AtomicBoolean startLock = new AtomicBoolean(false);
    private final StopBroadcastReceiver stopBroadcastReceiver = new StopBroadcastReceiver();
    private SyncAccountBroadcastReceiver syncAccountBroadcastReceiver;
    private MediaSessionCompat mediaSession;
    private PlayerWrapper playerWrapper;
    private MediaSessionConnector mediaSessionConnector;
    private ExoPlayerListener exoPlayerListener;
    private CastPlayerListener castPlayerListener;
    private boolean startedOnce = false;
    private NullSafeMediaMetadataProvider nullSafeMediaMetadataProvider;
    private MediaPlaybackPreparer playbackPreparer;

    public static void invalidateSession() {
        sessionId = Obj.tap(UUID.randomUUID().toString(), s -> L.d("Creating new session id: " + s));
    }

    public static String currentSessionId() {
        return sessionId;
    }

    private static PlayerNotificationManager buildPlayerNotificationManager(Context context, PlayerWrapper playerWrapper, MediaSessionCompat mediaSession, Supplier<Picasso> picassoSupplier) {
        return Obj.tap(new PlayerNotificationManager.Builder(context, NOTIFICATION_ID, Debutante.createNotificationChannel(context, Debutante.NOTIFICATION_CHANNEL_ID))
                .setMediaDescriptionAdapter(new MediaDescriptionAdapter(context, picassoSupplier))
                .setNotificationListener(new MediaSessionNotificationListener(context, playerWrapper))
                .build(), p -> {
            p.setMediaSessionToken(mediaSession.getSessionToken());
            p.setUseChronometer(true);
            p.setUsePreviousActionInCompactView(true);
            p.setUseNextActionInCompactView(true);
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        L.i("Binding to media service, action: " + Optional.ofNullable(intent).map(Intent::getAction).orElse("<none>"));

        if (intent != null && PlayerService.class.getName().equals(intent.getAction())) {
            return new LocalBinder<>(this);
        }

        return super.onBind(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        L.i("Creating media service");
        mediaSession = new MediaSessionCompat(this, getClass().getName());
        mediaSession.addOnActiveChangeListener(() -> L.i("Session changing active state: " + mediaSession.isActive()));
        playerWrapper = new PlayerWrapper(this, d().exoPlayer(), d().castPlayer(), d().repository(), d().appConfig());
        mediaSession.setSessionActivity(PendingIntent.getActivity(this, BaseForegroundService.STOP_SERVICE_REQUEST_CODE, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
        nullSafeMediaMetadataProvider = new NullSafeMediaMetadataProvider(mediaSession);
        playbackPreparer = new MediaPlaybackPreparer(this, playerWrapper, d().repository());
        mediaSessionConnector = Obj.tap(new MediaSessionConnector(mediaSession), m -> {
            m.setPlayer(playerWrapper.player());
            MediaQueueNavigator queueNavigator = new MediaQueueNavigator(this, mediaSession, d().appConfig(), s -> new File(d().cacheDir(), okhttp3.Cache.Companion.key(HttpUrl.get(s)) + ".1"));
            m.setQueueNavigator(queueNavigator);
            m.setPlaybackPreparer(playbackPreparer);
            m.setMediaMetadataProvider(nullSafeMediaMetadataProvider);
            playerWrapper.player().prepare();
            m.invalidateMediaSessionQueue();
            m.invalidateMediaSessionMetadata();
            m.invalidateMediaSessionPlaybackState();
        });
        PlayerNotificationManager playerNotificationManager = buildPlayerNotificationManager(this, playerWrapper, mediaSession, d()::picasso);

        exoPlayerListener = new ExoPlayerListener(this, d().exoPlayer(), d().downloadManager(), playerNotificationManager, d().appConfig().getSongsToPreload(), playerWrapper);
        d().exoPlayer().addListener(exoPlayerListener);
        castPlayerListener = new CastPlayerListener(this, d().castPlayer(), d().sharedInstance().getPrecacheManager(), d().mediaItemConverter(), playerWrapper);
        d().castPlayer().addListener(castPlayerListener);

        d().castPlayer().setSessionAvailabilityListener(new CastSessionAvailabilityListener(this, playerWrapper, mediaSessionConnector, d().appConfig()));

        syncAccountBroadcastReceiver = new SyncAccountBroadcastReceiver(d().okHttpClient(), playerWrapper, d().gson(), d().repository());
        registerReceiver(syncAccountBroadcastReceiver, Obj.tap(new IntentFilter(), f -> {
                    f.addAction(SyncAccountBroadcastReceiver.ACTION);
                    f.addAction(SyncAccountBroadcastReceiver.FORCE_STOP_ACTION);
                }), DeviceHelper.doNotRequireReceiverFlags() ? 0 : RECEIVER_EXPORTED
        );

        mediaSession.setActive(true);
        setSessionToken(mediaSession.getSessionToken());
    }

    public MediaSessionConnector mediaSessionConnector() {
        return mediaSessionConnector;
    }

    public PlayerWrapper playerWrapper() {
        return playerWrapper;
    }

    public MediaSessionCompat mediaSession() {
        return mediaSession;
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        Bundle bundle = rootHints != null ? new Bundle(rootHints) : new Bundle();
        bundle.putString(EXTRA_SESSION_ID, currentSessionId());
        String rootId = MediaBrowserHelper.ROOT_ID + "?_sid=" + sessionId;
        L.i("onGetRoot: " + rootId + ", client: " + clientPackageName + ", root hints: " + rootHints);
        if (rootHints != null) {
            if (rootHints.getBoolean(BrowserRoot.EXTRA_RECENT, false)) {
                return new BrowserRoot(RECENT_ROOT, bundle);
            }
        }
        return new BrowserRoot(rootId, bundle);
    }

    @Override
    public void onLoadItem(String itemId, @NonNull Result<MediaBrowserCompat.MediaItem> result) {
        L.i("onLoadItem: " + itemId);
        result.detach();
        MediaBrowserHelper.loadFromService(this, d().repository(), withSessionId(itemId), result::sendResult);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        L.i("onLoadChildren: " + parentId);
        result.detach();

        if (RECENT_ROOT.equals(parentId)) {
            Optional<Pair<MediaBrowserCompat.MediaItem, List<MediaBrowserCompat.MediaItem>>> mediaItems = PlayerState.loadMediaItems(this, Optional.empty());

            if (mediaItems.isPresent()) {
                doSendResults(mediaItems.get().getValue(), result);
            } else {
                result.sendResult(Collections.emptyList());
            }
        } else {
            MediaBrowserHelper.loadChildrenFromService(this, d().repository(), withSessionId(parentId), children -> doSendResults(children, result));
        }
    }

    private void doSendResults(List<MediaBrowserCompat.MediaItem> children, Result<List<MediaBrowserCompat.MediaItem>> result) {
        L.d("doSendResults, items count: " + CollectionUtils.size(children));
        String permission = DeviceHelper.requireSpecificReadAudioPermissions() ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        int checkResult = checkSelfPermission(permission);
        if (checkResult != PackageManager.PERMISSION_GRANTED || !d().appConfig().isAccountsLocalEnabled()) {
            result.sendResult(children.stream().filter(MediaBrowserHelper::isNotLocalAccount).map(this::decorateTitle).collect(Collectors.toList()));
        } else {
            result.sendResult(children.stream().map(this::decorateTitle).collect(Collectors.toList()));
        }
    }

    private MediaBrowserCompat.MediaItem decorateTitle(MediaBrowserCompat.MediaItem mediaItem) {
        if (d().appConfig().isCarTextIconsEnabled()) {

            return new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                    .setMediaId(mediaItem.getDescription().getMediaId())
                    .setTitle(MediaBrowserHelper.getUTF8CharForIconUri(mediaItem.getDescription().getIconUri()) + " " + mediaItem.getDescription().getTitle())
                    .setDescription(mediaItem.getDescription().getDescription())
                    .build(), mediaItem.getFlags());
        } else {
            return mediaItem;
        }
    }

    @NonNull
    private String withSessionId(@NonNull String parentId) {
        if (!parentId.contains("_sid=")) {
            parentId += parentId.contains("?") ? "&_sid=" + sessionId : "?_sid=" + sessionId;
        }
        return parentId;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        synchronized (GLOBAL_LOCK) {
            int startCommand = super.onStartCommand(intent, flags, startId);

            if (!startLock.getAndSet(true)) {
                L.i("Registering stop service receiver");
                registerReceiver(stopBroadcastReceiver, new IntentFilter(ACTION_STOP), DeviceHelper.doNotRequireReceiverFlags() ? 0 : RECEIVER_EXPORTED);
                PendingIntent deleteIntent = PendingIntent.getBroadcast(this, STOP_SERVICE_REQUEST_CODE, new Intent(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                Notification notification = buildNotification(this, getActivityIntent(), R.string.player_service_notification_content, false, deleteIntent);

                if (DeviceHelper.needsForegroundServiceTypeOnStart()) {
                    startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                } else {
                    startForeground(NOTIFICATION_ID, notification);
                }

                new Scheduler(this).scheduleWatchdog();
                L.i("Starting foreground player service, action: " + Optional.ofNullable(intent).map(Intent::getAction).orElse("<none>"));
            }

            if (wakeLock == null) {
                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        Debutante.TAG + "::" + getClass().getSimpleName());
            }

            mediaSession.setActive(!playerWrapper.isCasting());

            String action = intent != null ? intent.getAction() : null;

            boolean playButtonPressed = false;
            if (ACTION_MEDIA_BUTTON.equals(action)) {
                KeyEvent keyEvent = DeviceHelper.hasTypeSafeGetParcelableExtra() ? intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent.class) : intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                playButtonPressed = keyEvent != null && keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY;
            }

            if (ACTION_WAKE.equals(action)) {
                releaseLock();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                acquireLock();
            } else if (ACTION_PAUSE.equals(action)) {
                releaseLock();
            } else if (playButtonPressed || ACTION_PLAY.equals(action)) {
                releaseLock();
                L.i("Active player (play): " + playerWrapper.activePlayer().getClass().getSimpleName() + ", prepare and play (from button: " + playButtonPressed + ")");
                if (startedOnce) {
                    playerWrapper.activePlayer().play();
                } else {
                    final Handler handler = new Handler(getMainLooper());
                    handler.post(() -> playbackPreparer.onPrepare(true));
                }
                if (playerWrapper.inactivePlayer().isPlaying()) {
                    L.i("Inactive player (stop): " + playerWrapper.inactivePlayer().getClass().getSimpleName());
                    playerWrapper.inactivePlayer().pause();
                }
                acquireLock();
                startedOnce = true;
            } else if (ACTION_PREPARE.equals(action)) {
                releaseLock();
                L.i("Active player (prepare): " + playerWrapper.activePlayer().getClass().getSimpleName());
                playerWrapper.activePlayer().prepare();
                acquireLock();
            }

            return startCommand;
        }
    }

    public boolean startedOnce() {
        return startedOnce;
    }

    private void acquireLock() {
        wakeLock.acquire(Duration.ofHours(1).toMillis());
    }

    @Override
    public boolean stopService(Intent name) {
        return Obj.tap(super.stopService(name), r -> unregisterReceiver());
    }

    @Override
    public void onDestroy() {
        L.i("Stopping foreground player service");
        unregisterReceiver();
        d().exoPlayer().removeListener(exoPlayerListener);
        d().castPlayer().removeListener(castPlayerListener);
        d().castPlayer().setSessionAvailabilityListener(null);
        unregisterReceiver(syncAccountBroadcastReceiver);
        mediaSession.release();
        releaseLock();
        super.onDestroy();
    }

    private void unregisterReceiver() {
        if (startLock.getAndSet(false)) {
            L.i("Unregistering stop service receiver");
            unregisterReceiver(stopBroadcastReceiver);
        }
    }

    protected void doStopSelf() {
        unregisterReceiver();
        stopSelf();
    }

    private Optional<Intent> getActivityIntent() {
        Intent activityIntent = new Intent(this, MainActivity.class);
        activityIntent.putExtra(MainActivity.OPEN_PLAYER_KEY, true);
        return Optional.of(activityIntent);
    }

    private void releaseLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private Debutante d() {
        return (Debutante) getApplication();
    }

    private final class StopBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            L.i("Stopping " + PlayerService.this.getClass().getSimpleName() + " service");
            doStopSelf();
        }
    }

    private static class NullSafeMediaMetadataProvider implements MediaSessionConnector.MediaMetadataProvider {
        private static final MediaMetadataCompat METADATA_EMPTY =
                new MediaMetadataCompat.Builder().build();
        private final MediaSessionConnector.DefaultMediaMetadataProvider defaultMediaMetadataProvider;
        private final MediaSessionCompat mediaSession;

        public NullSafeMediaMetadataProvider(MediaSessionCompat mediaSession) {
            this.mediaSession = mediaSession;
            defaultMediaMetadataProvider = new MediaSessionConnector.DefaultMediaMetadataProvider(mediaSession.getController(), null);
        }

        @Override
        public MediaMetadataCompat getMetadata(Player player) {
            if (mediaSession.getController().getPlaybackState() == null) {
                return METADATA_EMPTY;
            }
            try {
                return defaultMediaMetadataProvider.getMetadata(player);
            } catch (Exception e) {
                L.e("Error getting metadata", e);
                return METADATA_EMPTY;
            }
        }
    }
}
