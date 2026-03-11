package io.github.debutante.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import io.github.debutante.helper.L;
import io.github.debutante.helper.PlayerWrapper;

public class WatchdogJobService extends JobService {
    private PlayerWrapper playerWrapper;
    private ServiceConnection mediaServiceConnection;
    private boolean firstTime = true;

    @Override
    public void onCreate() {
        L.i("WatchdogJobService.onCreate");
        super.onCreate();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        L.i("Starting watchdog job");

        if (!firstTime) {
            Intent serviceIntent = new Intent(this, PlayerService.class).setAction(PlayerService.class.getName());

            mediaServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    try {
                        service.linkToDeath(() -> mediaServiceConnection = null, 0);
                    } catch (RemoteException e) {
                    }
                    playerWrapper = ((LocalBinder<PlayerService>) service).getService().playerWrapper();
                    boolean isPlaying = playerWrapper != null && playerWrapper.player().isPlaying();
                    if (isPlaying) {
                        L.d("It's playing");
                    } else {
                        L.d("It's not playing, let's stop PlayerService");
                        if (mediaServiceConnection != null) {
                            unbindService(mediaServiceConnection);
                            mediaServiceConnection = null;
                        }
                        sendBroadcast(new Intent(PlayerService.ACTION_STOP));
                        stopSelf();
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {

                }
            };

            bindService(serviceIntent, mediaServiceConnection, BIND_AUTO_CREATE);
        }
        firstTime = false;
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
