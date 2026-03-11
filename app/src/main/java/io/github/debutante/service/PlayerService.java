package io.github.debutante.service;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;

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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import io.github.debutante.BuildConfig;
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
import io.github.debutante.receivers.ChangeMediaItemBroadcastReceiver;
import io.github.debutante.receivers.SyncAccountBroadcastReceiver;
import okhttp3.HttpUrl;

public class PlayerService extends MediaBrowserServiceCompat {
    public static final String ACTION_PAUSE = PlayerService.class.getSimpleName() + "-ACTION_PAUSE";
    public static final String ACTION_PLAY = PlayerService.class.getSimpleName() + "-ACTION_PLAY";
    public static final String ACTION_MEDIA_BUTTON = "android.intent.action.MEDIA_BUTTON";
    public static final String ACTION_WAKE = PlayerService.class.getSimpleName() + "-ACTION_WAKE";
    public static final String ACTION_PREPARE = PlayerService.class.getSimpleName() + "-ACTION_PREPARE";
    public static final String ACTION_STOP = PlayerService.class.getSimpleName() + "-ACTION_STOP";
    private static final int STOP_SERVICE_REQUEST_CODE = 1;

    private static final Object GLOBAL_LOCK = new Object();
    public static final MediaItem EMPTY_MEDIA_ITEM = new MediaItem.Builder().setUri("content://" + BuildConfig.APPLICATION_ID + ".fileprovider/public/empty.mp3").build();
    private PowerManager.WakeLock wakeLock;
    private final AtomicBoolean startLock = new AtomicBoolean(false);
    private boolean startedOnce = false;
    private static String sessionId = UUID.randomUUID().toString();
    private final AtomicBoolean startedLock = new AtomicBoolean(false);
    private final StopBroadcastReceiver stopBroadcastReceiver = new StopBroadcastReceiver();
    private SyncAccountBroadcastReceiver syncAccountBroadcastReceiver;
    private PlayerWrapper playerWrapper;
    private ExoPlayerListener exoPlayerListener;
    private CastPlayerListener castPlayerListener;
    private NullSafeMediaMetadataProvider nullSafeMediaMetadataProvider;
    private Player.Listener onReady;

    public PlayerService() {
    }

    public static void invalidateSession() {
        sessionId = Obj.tap(UUID.randomUUID().toString(), s -> L.d("Creating new session id: " + s));
    }

    public static String currentSessionId() {
        return sessionId;
    }

    @Override
    public IBinder onBind(Intent intent) {
        L.i("Binding to media service, action: " + Optional.ofNullable(intent).map(Intent::getAction).orElse("<none>"));

        if (intent != null && PlayerService.class.getName().equals(intent.getAction())) {
            return new LocalBinder<>(this);
        }

        return super.onBind(intent);
    }

    public static boolean hasEnqueuedMediaItems(Player p) {
        int mediaItemCount = p.getMediaItemCount();
        if (mediaItemCount > 1) {
            return true;
        } else if (mediaItemCount == 1) {
            return p.getMediaItemAt(0) != EMPTY_MEDIA_ITEM;
        } else {
            return false;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        synchronized (GLOBAL_LOCK) {
            int startCommand = super.onStartCommand(intent, flags, startId);
            if (intent != null) {
                L.i("PlayerService.onStartCommand: " + intent.getAction() + " " + L.toString(intent.getExtras()));
            }

            if (!startedLock.getAndSet(true)) {
                L.i("Registering stop service receiver");
                registerReceiver(stopBroadcastReceiver, new IntentFilter(ACTION_STOP), DeviceHelper.doNotRequireReceiverFlags() ? 0 : RECEIVER_EXPORTED);

                PendingIntent deleteIntent = PendingIntent.getBroadcast(this, STOP_SERVICE_REQUEST_CODE, new Intent(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                Notification notification = BaseForegroundService.buildNotification(this, getActivityIntent(), R.string.player_service_notification_content, false, deleteIntent);

                int notificationId = DeviceHelper.canShareNotificationId() ? Debutante.NOTIFICATION_ID : Debutante.NOTIFICATION_ID - 1;
                if (DeviceHelper.needsForegroundServiceTypeOnStart()) {
                    startForeground(notificationId, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                } else {
                    startForeground(notificationId, notification);
                }
            }

            handleStartIntent(intent);

            return startCommand;
        }
    }

    @NonNull
    private static String getRootId() {
        return MediaBrowserHelper.ROOT_ID + "?_sid=" + sessionId;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        L.i("Creating media service");
        d().releaseMediaSession();
        MediaSessionCompat mediaSession = d().updateMediaSession(w -> {
            if (!w.isRegistered()) {
                L.i("Registering MSC");
                setSessionToken(w.getMediaSession().getSessionToken());
                w.setRegistered(true);
            }
            return w;
        });
        L.i("Initializing player");
        final ExoPlayer exoPlayer = d().exoPlayer();
        onReady = new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                L.d("Playback state changed: " + stateToString(playbackState));
                if (stateIsReady(playbackState) && exoPlayerListener == null) {
                    synchronized (this) {
                        MediaSessionCompat registeredMediaSession = d().updateMediaSession(w -> {
                            if (!w.isWithActivityIntent()) {
                                L.i("Setting MSC activity");
                                w.getMediaSession().setSessionActivity(PendingIntent.getActivity(PlayerService.this, 0, new Intent(PlayerService.this, MainActivity.class).putExtra(MainActivity.OPEN_PLAYER_KEY, true), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
                                w.setWithActivityIntent(true);
                            }

                            return w;
                        });
                        PlayerNotificationManager playerNotificationManager = Obj.tap(new PlayerNotificationManager.Builder(PlayerService.this, Debutante.NOTIFICATION_ID, Debutante.createNotificationChannel(PlayerService.this, Debutante.NOTIFICATION_CHANNEL_ID))
                                .setMediaDescriptionAdapter(new MediaDescriptionAdapter(PlayerService.this, d()::picasso))
                                .setNotificationListener(new MediaSessionNotificationListener(PlayerService.this, playerWrapper))
                                .build(), p -> {
                            p.setMediaSessionToken(registeredMediaSession.getSessionToken());
                            p.setUseChronometer(true);
                            p.setUsePreviousActionInCompactView(true);
                            p.setUseNextActionInCompactView(true);
                        });
                        exoPlayerListener = new ExoPlayerListener(PlayerService.this, exoPlayer, d().downloadManager(), playerNotificationManager, d().appConfig().getSongsToPreload(), playerWrapper);
                        exoPlayer.addListener(exoPlayerListener);
                        MediaItem currentMediaItem = exoPlayer.getCurrentMediaItem();
                        if (currentMediaItem != null && currentMediaItem != EMPTY_MEDIA_ITEM) {
                            ChangeMediaItemBroadcastReceiver.broadcast(PlayerService.this, currentMediaItem.mediaId);
                        }
                        L.i("Activating MSC");
                        mediaSession.setActive(true);
                        notifyChildrenChanged(getRootId(), new Bundle());
                    }
                } else {
                    mediaSession.setActive(playbackState != 0);
                }
            }

            private boolean stateIsReady(int playbackState) {
                switch (playbackState) {
                    case 1:
                    case 3:
                        return true;
                    default:
                        return false;
                }
            }

            private String stateToString(int playbackState) {
                switch (playbackState) {
                    case 0:
                        return "NONE";
                    case 1:
                        return "IDLE";
                    case 2:
                        return "BUFFERING";
                    case 3:
                        return "READY";
                    case 4:
                        return "ENDED";
                    default:
                        return "UNKNOWN";
                }
            }
        };
        exoPlayer.addListener(onReady);

        playerWrapper = new PlayerWrapper(this, exoPlayer, d().castPlayer(), d().repository(), d().appConfig());
        nullSafeMediaMetadataProvider = new NullSafeMediaMetadataProvider(mediaSession);
        MediaPlaybackPreparer playbackPreparer = new MediaPlaybackPreparer(this, mediaSession, playerWrapper, d().repository());

        castPlayerListener = new CastPlayerListener(this, d().castPlayer(), d().sharedInstance().getPrecacheManager(), d().mediaItemConverter(), playerWrapper);
        d().castPlayer().addListener(castPlayerListener);

        syncAccountBroadcastReceiver = new SyncAccountBroadcastReceiver(d().okHttpClient(), playerWrapper, d().gson(), d().repository());
        registerReceiver(syncAccountBroadcastReceiver, Obj.tap(new IntentFilter(), f -> {
                    f.addAction(SyncAccountBroadcastReceiver.ACTION);
                    f.addAction(SyncAccountBroadcastReceiver.FORCE_STOP_ACTION);
                }), DeviceHelper.doNotRequireReceiverFlags() ? 0 : RECEIVER_EXPORTED
        );

        MediaSessionConnector mediaSessionConnector = Obj.tap(new MediaSessionConnector(mediaSession), m -> {
            m.setPlayer(playerWrapper.player());
            MediaQueueNavigator queueNavigator = new MediaQueueNavigator(this, mediaSession, d().appConfig(), s -> new File(d().cacheDir(), okhttp3.Cache.Companion.key(HttpUrl.get(s)) + ".1"));
            m.setQueueNavigator(queueNavigator);
            m.setQueueEditor(playbackPreparer);
            m.setPlaybackPreparer(playbackPreparer);
            m.setMediaMetadataProvider(nullSafeMediaMetadataProvider);
        });
        d().castPlayer().setSessionAvailabilityListener(new CastSessionAvailabilityListener(this, playerWrapper, mediaSessionConnector, d().appConfig()));

        exoPlayer.setMediaItem(EMPTY_MEDIA_ITEM);
        exoPlayer.setPlayWhenReady(false);
        exoPlayer.prepare();
        L.i("Player initialized");
    }

    private void prepreFromStoredStatus(Player p) {
        PlayerState.loadMediaItems(this, Optional.empty()).ifPresent(mi -> {
            MediaBrowserCompat.MediaItem parentMediaItem = mi.getLeft();
            List<MediaBrowserCompat.MediaItem> mediaItems = mi.getRight();
            Optional<Pair<String, Long>> mediaIdAndPosition = PlayerState.loadCurrentMediaItemPosition(this);
            String currentMediaItemId = PlayerState.loadCurrentMediaItemId(this, Optional.empty()).orElse(parentMediaItem.getMediaId());
            Long position = mediaIdAndPosition.filter(kv -> kv.getKey().equals(currentMediaItemId)).map(Pair::getValue).orElse(C.TIME_UNSET);
            L.d("Loaded " + CollectionUtils.size(mediaItems) + " media items, start playing from " + position);
            playerWrapper.newPlayerPreparer().prepare(parentMediaItem, mediaItems, currentMediaItemId, position, p::play, e -> L.e("Can't prepare and play", e), true, false, true);
        });
    }

    public PlayerWrapper playerWrapper() {
        return playerWrapper;
    }

    @Override
    public boolean stopService(Intent name) {
        startedOnce = false;
        return Obj.tap(super.stopService(name), r -> unregisterStopReceiver());
    }

    private void acquireLock() {
        wakeLock.acquire(Duration.ofHours(1).toMillis());
    }

    private void handleStartIntent(Intent intent) {
        MediaSessionCompat mediaSession = d().getSafeMediaSession();

        mediaSession.setActive(!playerWrapper.isCasting());

        if (!startLock.getAndSet(true)) {
            L.i("Starting foreground player service, action: " + Optional.ofNullable(intent).map(Intent::getAction).orElse("<none>"));
            new Scheduler(PlayerService.this).scheduleWatchdog();
        }

        if (wakeLock == null) {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    Debutante.TAG + "::" + getClass().getSimpleName());
        }

        String action = intent != null ? intent.getAction() : null;

        boolean playButtonPressed = false;
        boolean pauseButtonPressed = false;
        if (ACTION_MEDIA_BUTTON.equals(action)) {
            KeyEvent keyEvent = DeviceHelper.hasTypeSafeGetParcelableExtra() ? intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent.class) : intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            playButtonPressed = keyEvent != null && keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY;
            pauseButtonPressed = keyEvent != null && keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PAUSE;
            L.i("Action is from media button: " + keyEvent + " play:" + playButtonPressed + ", pause:" + pauseButtonPressed);
        }

        if (ACTION_WAKE.equals(action)) {
            releaseLock();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            acquireLock();
        } else if (pauseButtonPressed || ACTION_PAUSE.equals(action)) {
            playerWrapper.activePlayer().pause();
            releaseLock();
        } else if (playButtonPressed || ACTION_PLAY.equals(action)) {
            releaseLock();
            L.i("Active player (play): " + playerWrapper.activePlayer().getClass().getSimpleName() + ", prepare and play (from button: " + playButtonPressed + ", started once: " + startedOnce + ", playing " + playerWrapper.activePlayer().isPlaying() + ")");
            if (startedOnce && !playerWrapper.activePlayer().isPlaying()) {
                Player p = playerWrapper.activePlayer();
                if (hasEnqueuedMediaItems(p)) {
                    L.d("Player has items enqueued");
                    p.play();
                } else {
                    L.d("Player has not items enqueued");
                    prepreFromStoredStatus(p);
                }
            } else {
                startedOnce = true;
                final Handler handler = new Handler(getMainLooper());
                handler.post(() -> Obj.tap(playerWrapper.activePlayer(), p -> {
                    if (hasEnqueuedMediaItems(p)) {
                        L.d("Player has items enqueued");
                        p.setPlayWhenReady(true);
                        p.prepare();
                    } else {
                        L.d("Player has not items enqueued");
                        prepreFromStoredStatus(p);
                    }
                }));
            }
            if (playerWrapper.inactivePlayer().isPlaying()) {
                L.i("Inactive player (stop): " + playerWrapper.inactivePlayer().getClass().getSimpleName());
                playerWrapper.inactivePlayer().pause();
            }
            acquireLock();
        } else if (ACTION_PREPARE.equals(action)) {
            releaseLock();
            L.i("Active player (prepare): " + playerWrapper.activePlayer().getClass().getSimpleName());
            playerWrapper.activePlayer().prepare();
            acquireLock();
        }
    }

    @Override
    public void notifyChildrenChanged(@NonNull String parentId) {
        L.i("notifyChildrenChanged: " + parentId);
        super.notifyChildrenChanged(parentId);
    }

    @Override
    public void notifyChildrenChanged(@NonNull String parentId, @NonNull Bundle options) {
        L.i("notifyChildrenChanged: " + parentId + ", " + L.toString(options));
        super.notifyChildrenChanged(parentId, options);
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        L.i("onGetRoot client: " + clientPackageName + ", root hints: " + rootHints);
        if (rootHints != null) {
            if (rootHints.getBoolean(BrowserRoot.EXTRA_RECENT, false)) {
                return new BrowserRoot(MediaBrowserHelper.RECENT_ROOT, null);
            }
        }
        return new BrowserRoot(getRootId(), null);
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

        if (MediaBrowserHelper.RECENT_ROOT.equals(parentId)) {
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

    @Override
    public void onSearch(@NonNull String query, Bundle extras, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        L.i("onSearch: " + query + ", extras: " + L.toString(extras));
        result.detach();
        MediaBrowserHelper.searchFromService(this, d().repository(), query, children -> doSendResults(children, result));
    }

    @Override
    public void onCustomAction(@NonNull String action, Bundle extras, @NonNull Result<Bundle> result) {
        L.i("onCustomAction: " + action + ", extras: " + L.toString(extras));
        super.onCustomAction(action, extras, result);
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
    public void onDestroy() {
        L.i("Destroying foreground player service");
        releaseLock();
        unregisterStopReceiver();
        if (onReady != null) {
            d().exoPlayer().removeListener(onReady);
        }
        if (exoPlayerListener != null) {
            d().exoPlayer().removeListener(exoPlayerListener);
        }
        d().castPlayer().removeListener(castPlayerListener);
        d().castPlayer().setSessionAvailabilityListener(null);
        unregisterReceiver(syncAccountBroadcastReceiver);

        d().releaseMediaSession();
        super.onDestroy();
    }

    protected void doStopSelf() {
        L.i("Stopping foreground player service");
        if (playerWrapper != null && playerWrapper.activePlayer().isPlaying()) {
            playerWrapper.activePlayer().pause();
        }
        unregisterStopReceiver();
        stopForeground(DeviceHelper.canShareNotificationId() ? STOP_FOREGROUND_DETACH : STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    protected Optional<Intent> getActivityIntent() {
        Intent activityIntent = new Intent(this, MainActivity.class);
        activityIntent.putExtra(MainActivity.OPEN_PLAYER_KEY, true);
        return Optional.of(activityIntent);
    }

    private void releaseLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void unregisterStopReceiver() {
        if (startedLock.getAndSet(false)) {
            L.i("Unregistering stop service receiver");
            unregisterReceiver(stopBroadcastReceiver);
        }
    }

    protected Debutante d() {
        return (Debutante) getApplication();
    }

    private class MediaCouple {
        private final MediaSessionCompat mediaSession;
        private final PlayerWrapper playerWrapper;

        public MediaCouple(MediaSessionCompat mediaSession, PlayerWrapper playerWrapper) {
            this.mediaSession = mediaSession;
            this.playerWrapper = playerWrapper;
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

    private final class StopBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            L.i("Stopping " + PlayerService.class.getSimpleName() + " service");
            doStopSelf();
        }
    }
}
