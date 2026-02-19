package io.github.debutante.service;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;

import java.util.Optional;

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

        String action = Optional.ofNullable(intent).map(Intent::getAction).orElse(null);

        boolean autoplayOnBTEnabled = d().appConfig().isAutoplayOnBTEnabled();

        if (autoplayOnBTEnabled) {
            if (ACTION_PREPARE.equals(action)) {
                L.i("Preparing app for A2DP events");
            } else if (ACTION_PLAY.equals(action)) {

                L.d("Autoplay on Bluetooth is enabled");

                Intent serviceIntent = new Intent(this, PlayerService.class).setAction(PlayerService.class.getName());

                final Handler handler = new Handler(getMainLooper());
                final ServiceConnection serviceConnection = new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        PlayerService playerService = ((LocalBinder<PlayerService>) service).getService();
                        PlayerWrapper playerWrapper = playerService.playerWrapper();

                        final Player player = playerWrapper.player();
                        if (!playerWrapper.isCasting() && !player.isPlaying()) {
                            if (playerService.mediaSession().isActive() && playerService.startedOnce()) {
                                L.i("Resuming current media session");
                                handler.post(player::play);
                            } else {
                                L.i("Resuming last media session");
                                handler.post(() -> {
                                    player.prepare();
                                    player.play();
                                });
                            }
                        }
                        unbindService(this);
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {

                    }
                };

                bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

            }

        }

        return startCommand;
    }

    protected Debutante d() {
        return (Debutante) getApplication();
    }

}
