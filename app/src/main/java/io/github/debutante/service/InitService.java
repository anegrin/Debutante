package io.github.debutante.service;

import android.content.Intent;

import com.google.android.exoplayer2.Player;

import io.github.debutante.Debutante;
import io.github.debutante.R;
import io.github.debutante.helper.L;
import io.github.debutante.helper.PlayerWrapper;

public class InitService extends BaseForegroundService {

    private static final int NOTIFICATION_ID = Debutante.NOTIFICATION_ID - 2;
    public static final String ACTION_PLAY = InitService.class.getSimpleName() + "-ACTION_PLAY";
    public static final String ACTION_PREPARE = InitService.class.getSimpleName() + "-ACTION_PREPARE";

    public InitService() {
        super(R.string.init_service_notification_content, NOTIFICATION_ID);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int startCommand = super.onStartCommand(intent, flags, startId);

        String action = intent.getAction();

        boolean autoplayOnBTEnabled = d().appConfig().isAutoplayOnBTEnabled();

        if (autoplayOnBTEnabled) {
            if (ACTION_PREPARE.equals(action)) {
                L.i("Preparing app for A2DP events");
            } else if (ACTION_PLAY.equals(action)) {

                L.d("Autoplay on Bluetooth is enabled");
                PlayerWrapper playerWrapper = d().playerWrapper();

                Player player = playerWrapper.player();
                if (!playerWrapper.isCasting() && !player.isPlaying()) {
                    if (d().mediaSession().isActive()) {
                        L.i("Resuming current media session");
                        player.play();
                    } else {
                        L.i("Resuming last media session");
                        new MediaPlaybackPreparer(this, playerWrapper, d().repository()).onPrepare(true);
                    }
                }
            }

        }

        doStopSelf();
        return startCommand;
    }
}
