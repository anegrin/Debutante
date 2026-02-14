package io.github.debutante.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import io.github.debutante.helper.L;
import io.github.debutante.helper.PlayerWrapper;

public class WatchdogJobService extends JobService {
    private static final long TIMEOUT = 30_000;
    private PlayerWrapper playerWrapper;
    private ServiceConnection serviceConnection;

    @Override
    public void onCreate() {
        super.onCreate();
        Intent serviceIntent = new Intent(this, PlayerService.class).setAction(PlayerService.class.getName());

        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                playerWrapper = ((LocalBinder<PlayerService>) service).getService().playerWrapper();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };

        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(serviceConnection);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        L.d("Starting job");

        boolean isPlaying = playerWrapper != null && playerWrapper.player().isPlaying();
        if (isPlaying) {
            jobFinished(params, true);
            return true;
        } else {
            jobFinished(params, false);
            stopService(new Intent(WatchdogJobService.this, PlayerService.class));
            return false;
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
