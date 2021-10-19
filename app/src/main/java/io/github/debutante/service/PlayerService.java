package io.github.debutante.service;

import android.content.Intent;
import android.os.PowerManager;

import java.time.Duration;
import java.util.Optional;

import io.github.debutante.Debutante;
import io.github.debutante.MainActivity;
import io.github.debutante.R;
import io.github.debutante.helper.L;
import io.github.debutante.helper.PlayerWrapper;
import io.github.debutante.helper.Scheduler;

public class PlayerService extends BaseForegroundService {

    public static final String ACTION_PAUSE = PlayerService.class.getSimpleName() + "-ACTION_PAUSE";
    public static final String ACTION_PLAY = PlayerService.class.getSimpleName() + "-ACTION_PLAY";
    public static final String ACTION_WAKE = PlayerService.class.getSimpleName() + "-ACTION_WAKE";
    private static final int NOTIFICATION_ID = Debutante.NOTIFICATION_ID - 1;
    public static final String ACTION_PREPARE = PlayerService.class.getSimpleName() + "-ACTION_PREPARE";

    private static final Object GLOBAL_LOCK = new Object();
    private PowerManager.WakeLock wakeLock;

    public PlayerService() {
        super(R.string.player_service_notification_content, NOTIFICATION_ID);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        synchronized (GLOBAL_LOCK) {
            L.i("Starting foreground player service");

            if (wakeLock == null) {
                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        Debutante.TAG + "::" + getClass().getSimpleName());
            }

            new Scheduler(this).scheduleWatchdog();

            int startCommand = super.onStartCommand(intent, flags, startId);

            PlayerWrapper playerWrapper = d().playerWrapper();
            d().mediaSession().setActive(!playerWrapper.isCasting());

            if (ACTION_WAKE.equals(intent.getAction())) {
                releaseLock();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                acquireLock();
            } else if (ACTION_PAUSE.equals(intent.getAction())) {
                releaseLock();
            } else if (ACTION_PLAY.equals(intent.getAction())) {
                releaseLock();
                L.i("Active player (play): " + playerWrapper.activePlayer().getClass().getSimpleName());
                playerWrapper.activePlayer().play();
                if (playerWrapper.inactivePlayer().isPlaying()) {
                    L.i("Inactive player (stop): " + playerWrapper.inactivePlayer().getClass().getSimpleName());
                    playerWrapper.inactivePlayer().pause();
                }
                acquireLock();
            } else if (ACTION_PREPARE.equals(intent.getAction())) {
                releaseLock();
                L.i("Active player (prepare): " + playerWrapper.activePlayer().getClass().getSimpleName());
                playerWrapper.activePlayer().prepare();
                acquireLock();
            }


            return startCommand;
        }
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

    private void releaseLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    protected Optional<Intent> getActivityIntent() {
        Intent activityIntent = new Intent(this, MainActivity.class);
        activityIntent.putExtra(MainActivity.OPEN_PLAYER_KEY, true);
        return Optional.of(activityIntent);
    }
}
