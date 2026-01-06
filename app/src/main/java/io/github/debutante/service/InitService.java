package io.github.debutante.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;

import io.github.debutante.Debutante;
import io.github.debutante.helper.L;
import io.github.debutante.helper.PlayerWrapper;

public class InitService extends Service {

    public static final String ACTION_PLAY = InitService.class.getSimpleName() + "-ACTION_PLAY";
    public static final String ACTION_PREPARE = InitService.class.getSimpleName() + "-ACTION_PREPARE";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
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

        return startCommand;
    }

    protected Debutante d() {
        return (Debutante) getApplication();
    }

}
