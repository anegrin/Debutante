package io.github.debutante.service;

import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;
import androidx.media.session.MediaButtonReceiver;

import com.google.android.exoplayer2.Player;

import java.util.Optional;

import io.github.debutante.Debutante;
import io.github.debutante.helper.L;
import io.github.debutante.helper.PlayerWrapper;

public class A2DPDelegateService extends Service {

    public static final String ACTION_PLAY = A2DPDelegateService.class.getSimpleName() + "-ACTION_PLAY";
    public static final String ACTION_ACTIVATE_SESSION = A2DPDelegateService.class.getSimpleName() + "-ACTION_ACTIVATE_SESSION";
    private ServiceConnection mediaServiceConnection;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        L.i("A2DPDelegateService.onStartCommand");
        int startCommand = super.onStartCommand(intent, flags, startId);

        String action = Optional.ofNullable(intent).map(Intent::getAction).orElse(null);

        boolean autoplayOnBTEnabled = d().appConfig().isAutoplayOnBTEnabled();

        if (ACTION_PLAY.equals(action) || ACTION_ACTIVATE_SESSION.equals(action)) {

            L.d("Autoplay on Bluetooth is enabled");

            final boolean play = autoplayOnBTEnabled && ACTION_PLAY.equals(action);

            Intent serviceIntent = new Intent(this, PlayerService.class).setAction(PlayerService.class.getName());

            final Handler handler = new Handler(getMainLooper());
            mediaServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    try {
                        service.linkToDeath(() -> mediaServiceConnection = null, 0);
                    } catch (RemoteException e) {
                    }
                    PlayerService playerService = ((LocalBinder<PlayerService>) service).getService();
                    PlayerWrapper playerWrapper = playerService.playerWrapper();

                    final Player player = playerWrapper.player();
                    if (!playerWrapper.isCasting() && !player.isPlaying()) {
                        bindToPlayerServiceAndPlayOrPrepare(playerWrapper, handler, play);
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {

                }
            };

            bindService(serviceIntent, mediaServiceConnection, Context.BIND_AUTO_CREATE);
        }

        return startCommand;
    }

    private void bindToPlayerServiceAndPlayOrPrepare(PlayerWrapper playerWrapper, Handler handler, boolean play) {
        final Player player = playerWrapper.player();
        if (d().getSafeMediaSession().isActive()) {
            L.i("Resuming current media session");
            handler.post(play ? player::play : player::prepare);
        } else {
            L.i("Resuming last media session");
            // sending pause is just a trick to activate the session...
            PendingIntent pendingIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
                    A2DPDelegateService.this,
                    play ? PlaybackStateCompat.ACTION_PLAY : PlaybackStateCompat.ACTION_PAUSE
            );
            try {
                pendingIntent.send();
            } catch (PendingIntent.CanceledException e) {
                L.e("Can't send pending intent", e);
            }
        }
    }

    @Override
    public void onDestroy() {
        L.i("A2DPDelegateService.onDestroy");
        if (mediaServiceConnection != null) {
            unbindService(mediaServiceConnection);
            mediaServiceConnection = null;
        }
        super.onDestroy();
    }

    protected Debutante d() {
        return (Debutante) getApplication();
    }

}
