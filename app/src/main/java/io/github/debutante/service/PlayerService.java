package io.github.debutante.service;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.KeyEvent;

import com.google.android.exoplayer2.C;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.debutante.Debutante;
import io.github.debutante.MainActivity;
import io.github.debutante.R;
import io.github.debutante.helper.DeviceHelper;
import io.github.debutante.helper.L;
import io.github.debutante.helper.Obj;
import io.github.debutante.helper.PlayerWrapper;
import io.github.debutante.helper.Scheduler;
import io.github.debutante.persistence.PlayerState;

public class PlayerService extends BaseForegroundService {
    public static final String ACTION_PAUSE = PlayerService.class.getSimpleName() + "-ACTION_PAUSE";
    public static final String ACTION_PLAY = PlayerService.class.getSimpleName() + "-ACTION_PLAY";
    public static final String ACTION_MEDIA_BUTTON = "android.intent.action.MEDIA_BUTTON";
    public static final String ACTION_WAKE = PlayerService.class.getSimpleName() + "-ACTION_WAKE";
    public static final String ACTION_PREPARE = PlayerService.class.getSimpleName() + "-ACTION_PREPARE";

    private static final Object GLOBAL_LOCK = new Object();
    private PowerManager.WakeLock wakeLock;
    private final AtomicBoolean startLock = new AtomicBoolean(false);
    private boolean startedOnce = false;
    private MediaCouple mediaCouple;

    public PlayerService() {
        super(R.string.player_service_notification_content, Debutante.NOTIFICATION_ID, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        synchronized (GLOBAL_LOCK) {
            int startCommand = super.onStartCommand(intent, flags, startId);
            if (intent != null) {
                L.i("PlayerService.onStartCommand: " + intent.getAction() + " " + L.toString(intent.getExtras()));
            }

            if (mediaCouple != null) {
                handleStartIntent(intent, mediaCouple);
            } else {
                bindToMediaBrowserServiceAndHandleStartIntent(intent);
            }

            return startCommand;
        }
    }

    private void bindToMediaBrowserServiceAndHandleStartIntent(Intent intent) {
        Intent serviceIntent = new Intent(this, MediaService.class).setAction(MediaService.class.getName());

        final ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                MediaService mediaService = ((LocalBinder<MediaService>) service).getService();
                MediaSessionCompat mediaSession = mediaService.mediaSession();
                PlayerWrapper playerWrapper = mediaService.playerWrapper();
                mediaCouple = new MediaCouple(mediaSession, playerWrapper);

                handleStartIntent(intent, mediaCouple);

                unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };

        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void handleStartIntent(Intent intent, MediaCouple mediaCouple) {
        MediaSessionCompat mediaSession = mediaCouple.mediaSession;
        PlayerWrapper playerWrapper = mediaCouple.playerWrapper;

        mediaSession.setActive(!playerWrapper.isCasting());

        if (!startLock.getAndSet(true)) {
            L.i("Registering stop service receiver");
            new Scheduler(PlayerService.this).scheduleWatchdog();
            L.i("Starting foreground player service, action: " + Optional.ofNullable(intent).map(Intent::getAction).orElse("<none>"));
        }

        if (wakeLock == null) {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    Debutante.TAG + "::" + getClass().getSimpleName());
        }

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
            L.i("Active player (play): " + playerWrapper.activePlayer().getClass().getSimpleName() + ", prepare and play (from button: " + playButtonPressed + ", started once: " + startedOnce + ", playing " + playerWrapper.activePlayer().isPlaying() + ")");
            if (startedOnce && !playerWrapper.activePlayer().isPlaying()) {
                startedOnce = true;
                playerWrapper.activePlayer().play();
            } else {
                startedOnce = true;
                final Handler handler = new Handler(getMainLooper());
                handler.post(() -> Obj.tap(playerWrapper.activePlayer(), p -> {
                    if (p.getMediaItemCount() > 0) {
                        L.d("Player has items enqueued");
                        p.setPlayWhenReady(true);
                        p.prepare();
                    } else {
                        L.d("Player has not items enqueued");
                        PlayerState.loadMediaItems(this, Optional.empty()).ifPresent(mi -> {
                            MediaBrowserCompat.MediaItem parentMediaItem = mi.getLeft();
                            List<MediaBrowserCompat.MediaItem> mediaItems = mi.getRight();
                            Optional<Pair<String, Long>> mediaIdAndPosition = PlayerState.loadCurrentMediaItemPosition(this);
                            String currentMediaItemId = PlayerState.loadCurrentMediaItemId(this, Optional.empty()).orElse(parentMediaItem.getMediaId());
                            Long position = mediaIdAndPosition.filter(kv -> kv.getKey().equals(currentMediaItemId)).map(Pair::getValue).orElse(C.TIME_UNSET);
                            L.d("Loaded " + CollectionUtils.size(mediaItems) + " media items, start playing from " + position);
                            playerWrapper.newPlayerPreparer().prepare(parentMediaItem, mediaItems, currentMediaItemId, position, p::play, e -> L.e("Can't prepare and play", e), true, false);
                        });
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
    public boolean stopService(Intent name) {
        startedOnce = false;
        return super.stopService(name);
    }

    public boolean startedOnce() {
        return startedOnce;
    }

    private void acquireLock() {
        wakeLock.acquire(Duration.ofHours(1).toMillis());
    }

    @Override
    public void onDestroy() {
        L.i("Stopping foreground player service");
        releaseLock();
        super.onDestroy();
    }

    @Override
    protected void doStopSelf() {
        if (mediaCouple != null && mediaCouple.playerWrapper.activePlayer().isPlaying()) {
            mediaCouple.playerWrapper.activePlayer().pause();
        }
        mediaCouple = null;
        super.doStopSelf();
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

    private class MediaCouple {
        private final MediaSessionCompat mediaSession;
        private final PlayerWrapper playerWrapper;

        public MediaCouple(MediaSessionCompat mediaSession, PlayerWrapper playerWrapper) {
            this.mediaSession = mediaSession;
            this.playerWrapper = playerWrapper;
        }
    }
}
