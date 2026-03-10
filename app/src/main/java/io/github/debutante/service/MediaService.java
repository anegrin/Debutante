package io.github.debutante.service;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;

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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.github.debutante.Debutante;
import io.github.debutante.MainActivity;
import io.github.debutante.adapter.MediaDescriptionAdapter;
import io.github.debutante.helper.DeviceHelper;
import io.github.debutante.helper.L;
import io.github.debutante.helper.MediaBrowserHelper;
import io.github.debutante.helper.Obj;
import io.github.debutante.helper.PlayerWrapper;
import io.github.debutante.listeners.CastPlayerListener;
import io.github.debutante.listeners.CastSessionAvailabilityListener;
import io.github.debutante.listeners.ExoPlayerListener;
import io.github.debutante.listeners.MediaSessionNotificationListener;
import io.github.debutante.persistence.PlayerState;
import io.github.debutante.receivers.SyncAccountBroadcastReceiver;
import okhttp3.HttpUrl;

public class MediaService extends MediaBrowserServiceCompat {
    public static final int DEACTIVATE_SESSION_REQUEST_CODE = 1;
    public static final String ACTION_DEACTIVATE_SESSION = MediaService.class.getSimpleName() + "-ACTION_DEACTIVATE_SESSION";

    public static final String EXTRA_SESSION_ID = "io.github.debutante.SESSION_ID";

    private static String sessionId = UUID.randomUUID().toString();
    private final DeactivateSessionBroadcastReceiver deactivateSessionBroadcastReceiver = new DeactivateSessionBroadcastReceiver();
    private SyncAccountBroadcastReceiver syncAccountBroadcastReceiver;
    private MediaSessionCompat mediaSession;
    private PlayerWrapper playerWrapper;
    private ExoPlayerListener exoPlayerListener;
    private CastPlayerListener castPlayerListener;
    private NullSafeMediaMetadataProvider nullSafeMediaMetadataProvider;

    public static void invalidateSession() {
        sessionId = Obj.tap(UUID.randomUUID().toString(), s -> L.d("Creating new session id: " + s));
    }

    public static String currentSessionId() {
        return sessionId;
    }

    private static PlayerNotificationManager buildPlayerNotificationManager(Context context, PlayerWrapper playerWrapper, MediaSessionCompat mediaSession, Supplier<Picasso> picassoSupplier) {
        return Obj.tap(new PlayerNotificationManager.Builder(context, Debutante.NOTIFICATION_ID, Debutante.createNotificationChannel(context, Debutante.NOTIFICATION_CHANNEL_ID))
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
        mediaSession.setActive(true);
        L.i("Binding to media service, action: " + Optional.ofNullable(intent).map(Intent::getAction).orElse("<none>"));

        if (intent != null && MediaService.class.getName().equals(intent.getAction())) {
            return new LocalBinder<>(this);
        }

        return super.onBind(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        L.i("Creating media service");
        mediaSession = new MediaSessionCompat(this, Debutante.TAG + ".MSC");
        L.i("Initializing player");
        playerWrapper = new PlayerWrapper(this, d().exoPlayer(), d().castPlayer(), d().repository(), d().appConfig());
        mediaSession.setSessionActivity(PendingIntent.getActivity(this, DEACTIVATE_SESSION_REQUEST_CODE, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        nullSafeMediaMetadataProvider = new NullSafeMediaMetadataProvider(mediaSession);
        MediaPlaybackPreparer playbackPreparer = new MediaPlaybackPreparer(this, mediaSession, playerWrapper, d().repository());

        PlayerNotificationManager playerNotificationManager = buildPlayerNotificationManager(this, playerWrapper, mediaSession, d()::picasso);

        exoPlayerListener = new ExoPlayerListener(this, d().exoPlayer(), d().downloadManager(), playerNotificationManager, d().appConfig().getSongsToPreload(), playerWrapper);
        d().exoPlayer().addListener(exoPlayerListener);
        castPlayerListener = new CastPlayerListener(this, d().castPlayer(), d().sharedInstance().getPrecacheManager(), d().mediaItemConverter(), playerWrapper);
        d().castPlayer().addListener(castPlayerListener);


        syncAccountBroadcastReceiver = new SyncAccountBroadcastReceiver(d().okHttpClient(), playerWrapper, d().gson(), d().repository());
        registerReceiver(syncAccountBroadcastReceiver, Obj.tap(new IntentFilter(), f -> {
                    f.addAction(SyncAccountBroadcastReceiver.ACTION);
                    f.addAction(SyncAccountBroadcastReceiver.FORCE_STOP_ACTION);
                }), DeviceHelper.doNotRequireReceiverFlags() ? 0 : RECEIVER_EXPORTED
        );

        registerReceiver(deactivateSessionBroadcastReceiver, new IntentFilter(ACTION_DEACTIVATE_SESSION), DeviceHelper.doNotRequireReceiverFlags() ? 0 : RECEIVER_EXPORTED);

        MediaSessionConnector mediaSessionConnector = Obj.tap(new MediaSessionConnector(mediaSession), m -> {
            MediaQueueNavigator queueNavigator = new MediaQueueNavigator(this, mediaSession, d().appConfig(), s -> new File(d().cacheDir(), okhttp3.Cache.Companion.key(HttpUrl.get(s)) + ".1"));
            m.setQueueNavigator(queueNavigator);
            m.setQueueEditor(playbackPreparer);
            m.setPlaybackPreparer(playbackPreparer);
            m.setMediaMetadataProvider(nullSafeMediaMetadataProvider);
            m.setPlayer(playerWrapper.player());
        });
        d().castPlayer().setSessionAvailabilityListener(new CastSessionAvailabilityListener(this, playerWrapper, mediaSessionConnector, d().appConfig()));

        Player.Listener onReady = new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState != 0) {
                    L.i("Setting session token");
                    mediaSessionConnector.invalidateMediaSessionQueue();
                    mediaSessionConnector.invalidateMediaSessionMetadata();
                    mediaSessionConnector.invalidateMediaSessionPlaybackState();
                    mediaSession.setActive(true);
                    setSessionToken(mediaSession.getSessionToken());
                }
                d().exoPlayer().removeListener(this);
            }
        };
        d().exoPlayer().addListener(onReady);
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
                return new BrowserRoot(MediaBrowserHelper.RECENT_ROOT, bundle);
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

        if (MediaBrowserHelper.RECENT_ROOT.equals(parentId)) {
            Optional<Pair<MediaBrowserCompat.MediaItem, List<MediaBrowserCompat.MediaItem>>> mediaItems = PlayerState.loadMediaItems(this, Optional.empty());

            if (mediaItems.isPresent()) {
                doSendResults(mediaItems.get().getValue(), result);
                mediaSession.setActive(true);
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
        L.i("Stopping media service");
        unregisterReceiver(deactivateSessionBroadcastReceiver);
        d().exoPlayer().removeListener(exoPlayerListener);
        d().castPlayer().removeListener(castPlayerListener);
        d().castPlayer().setSessionAvailabilityListener(null);
        unregisterReceiver(syncAccountBroadcastReceiver);
        mediaSession.release();
        super.onDestroy();
    }

    protected void deativateSession() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
        }
    }

    private Debutante d() {
        return (Debutante) getApplication();
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

    private final class DeactivateSessionBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            L.i("Deactivating session");
            deativateSession();
        }
    }
}