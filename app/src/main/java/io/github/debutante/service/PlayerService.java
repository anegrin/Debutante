package io.github.debutante.service;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.KeyEvent;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.debutante.Debutante;
import io.github.debutante.MainActivity;
import io.github.debutante.R;
import io.github.debutante.helper.DeviceHelper;
import io.github.debutante.helper.L;
import io.github.debutante.helper.PlayerWrapper;
import io.github.debutante.helper.Scheduler;

public class PlayerService extends BaseForegroundService {
    public static final String ACTION_PAUSE = PlayerService.class.getSimpleName() + "-ACTION_PAUSE";
    public static final String ACTION_PLAY = PlayerService.class.getSimpleName() + "-ACTION_PLAY";
    public static final String ACTION_MEDIA_BUTTON = "android.intent.action.MEDIA_BUTTON";
    public static final String ACTION_WAKE = PlayerService.class.getSimpleName() + "-ACTION_WAKE";
    private static final int NOTIFICATION_ID = Debutante.NOTIFICATION_ID - 1;
    public static final String ACTION_PREPARE = PlayerService.class.getSimpleName() + "-ACTION_PREPARE";

    private static final Object GLOBAL_LOCK = new Object();
    private PowerManager.WakeLock wakeLock;
    private final AtomicBoolean startLock = new AtomicBoolean(false);
    private boolean startedOnce = false;
    private Trinity trinity;

    public PlayerService() {
        super(R.string.player_service_notification_content, NOTIFICATION_ID, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        synchronized (GLOBAL_LOCK) {
            int startCommand = super.onStartCommand(intent, flags, startId);
            if (intent != null) {
                L.i("PlayerService.onStartCommand: " + intent.getAction() + " " + L.toString(intent.getExtras()));
            }
            bindToMediaBrowserServiceAndHandleIntent(intent);

            return startCommand;
        }
    }

    private void bindToMediaBrowserServiceAndHandleIntent(Intent intent) {
        Intent serviceIntent = new Intent(this, MediaService.class).setAction(MediaService.class.getName());

        final ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                MediaService mediaService = ((LocalBinder<MediaService>) service).getService();
                MediaSessionCompat mediaSession = mediaService.mediaSession();
                PlayerWrapper playerWrapper = mediaService.playerWrapper();
                MediaPlaybackPreparer playbackPreparer = mediaService.playbackPreparer();
                trinity = new Trinity(mediaSession, playerWrapper, playbackPreparer);

                handleIntent(intent, trinity);

                unbindService(this);
            }
            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };

        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void handleIntent(Intent intent, Trinity trinity) {
        MediaSessionCompat mediaSession = trinity.mediaSession;
        PlayerWrapper playerWrapper = trinity.playerWrapper;
        MediaPlaybackPreparer playbackPreparer = trinity.playbackPreparer;

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
            L.i("Active player (play): " + playerWrapper.activePlayer().getClass().getSimpleName() + ", prepare and play (from button: " + playButtonPressed + ")");
            if (startedOnce && !playerWrapper.activePlayer().isPlaying()) {
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
            startedOnce = true;
        }
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
        if (trinity != null && trinity.playerWrapper.activePlayer().isPlaying()) {
            trinity.playerWrapper.activePlayer().stop();
        }
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

    private class Trinity {
        private final MediaSessionCompat mediaSession;
        private final PlayerWrapper playerWrapper;
        private final MediaPlaybackPreparer playbackPreparer;

        public Trinity(MediaSessionCompat mediaSession, PlayerWrapper playerWrapper, MediaPlaybackPreparer playbackPreparer) {
            this.mediaSession = mediaSession;
            this.playerWrapper = playerWrapper;
            this.playbackPreparer = playbackPreparer;
        }
    }
}
