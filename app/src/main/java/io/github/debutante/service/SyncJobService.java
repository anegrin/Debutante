package io.github.debutante.service;

import android.app.job.JobParameters;
import android.app.job.JobService;

import io.github.debutante.helper.L;
import io.github.debutante.receivers.SyncAccountBroadcastReceiver;

public class SyncJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        L.d("Starting job");
        SyncAccountBroadcastReceiver.broadcast(this);
        jobFinished(params, true);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        L.d("Stopping job");
        return true;
    }
}
