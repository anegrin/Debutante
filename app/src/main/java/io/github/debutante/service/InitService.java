package io.github.debutante.service;

import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;
import androidx.media.session.MediaButtonReceiver;

import com.google.android.exoplayer2.Player;

import java.util.Optional;

import io.github.debutante.Debutante;
import io.github.debutante.helper.L;
import io.github.debutante.helper.PlayerWrapper;

public class InitService extends Service {

    public static final String ACTION_PLAY = InitService.class.getSimpleName() + "-ACTION_PLAY";
    public static final String ACTION_ACTIVATE_SESSION = InitService.class.getSimpleName() + "-ACTION_ACTIVATE_SESSION";

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

        if (ACTION_PLAY.equals(action) || ACTION_ACTIVATE_SESSION.equals(action)) {

            L.d("Autoplay on Bluetooth is enabled");

            final boolean play = autoplayOnBTEnabled && ACTION_PLAY.equals(action);

            Intent serviceIntent = new Intent(this, MediaService.class).setAction(MediaService.class.getName());

            final Handler handler = new Handler(getMainLooper());
            final ServiceConnection serviceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    MediaService mediaService = ((LocalBinder<MediaService>) service).getService();
                    PlayerWrapper playerWrapper = mediaService.playerWrapper();

                    final Player player = playerWrapper.player();
                    if (!playerWrapper.isCasting() && !player.isPlaying()) {
                        bindToPlayerServiceAndPlayOrPrepare(mediaService, playerWrapper, handler, play);
                    }
                    unbindService(this);
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {

                }
            };

            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }

        return startCommand;
    }

    private void bindToPlayerServiceAndPlayOrPrepare(MediaService mediaService, PlayerWrapper playerWrapper, Handler handler, boolean play) {
        Intent serviceIntent = new Intent(this, PlayerService.class);

        final ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                PlayerService playerService = ((LocalBinder<PlayerService>) service).getService();

                final Player player = playerWrapper.player();
                if (mediaService.mediaSession().isActive() && playerService.startedOnce()) {
                    L.i("Resuming current media session");
                    handler.post(play ? player::play : player::prepare);
                } else {
                    L.i("Resuming last media session");
                    // sending pause is just a trick to activate the session...
                    PendingIntent pendingIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
                            InitService.this,
                            play ? PlaybackStateCompat.ACTION_PLAY : PlaybackStateCompat.ACTION_PAUSE
                    );
                    try {
                        pendingIntent.send();
                    } catch (PendingIntent.CanceledException e) {
                        L.e("Can't send pending intent", e);
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

    protected Debutante d() {
        return (Debutante) getApplication();
    }

}
