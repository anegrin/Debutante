package io.github.debutante.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;

import io.github.debutante.Debutante;
import io.github.debutante.helper.L;
import io.github.debutante.helper.PlayerWrapper;

public class WatchdogJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        L.d("Starting job");

        PlayerWrapper playerWrapper = ((Debutante) getApplication()).playerWrapper();

        boolean isPlaying = playerWrapper.player().isPlaying();
        if (isPlaying) {
            jobFinished(params, true);
            return true;
        } else {
            jobFinished(params, false);
            stopService(new Intent(this, PlayerService.class));
            return false;
        }

    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
