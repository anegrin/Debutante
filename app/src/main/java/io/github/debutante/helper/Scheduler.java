package io.github.debutante.helper;


import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import java.time.Duration;

import io.github.debutante.model.AppConfig;
import io.github.debutante.service.SyncJobService;
import io.github.debutante.service.WatchdogJobService;

public class Scheduler {

    private static final int SYNC_JOB_ID = 0;
    private static final int WATCHDOG_JOB_ID = 1;
    private static final Duration WATCHDOG_INTERVAL = Duration.ofMinutes(5);
    private final Context context;

    public Scheduler(Context context) {
        this.context = context;
    }

    public void scheduleSync(AppConfig appConfig, boolean refreshing) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        if (appConfig.isAccountsSyncEnabled()) {
            Duration interval = Duration.ofHours(appConfig.getAccountsSyncIntervalHours());

            if (refreshing) {
                jobScheduler.cancel(SYNC_JOB_ID);
            }

            if (jobScheduler.getPendingJob(SYNC_JOB_ID) == null) {
                L.i("Scheduling account sync job, interval=" + interval);
                jobScheduler.schedule(new JobInfo.Builder(SYNC_JOB_ID, new ComponentName(context, SyncJobService.class))
                        .setRequiresCharging(false)
                        .setMinimumLatency(interval.toMillis())
                        .setBackoffCriteria(interval.toMillis(), JobInfo.BACKOFF_POLICY_LINEAR)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build()
                );
            }
        } else {
            L.i("Unscheduling account sync job");
            jobScheduler.cancel(SYNC_JOB_ID);
        }
    }

    public void scheduleWatchdog() {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        if (jobScheduler.getPendingJob(WATCHDOG_JOB_ID) == null) {
            L.i("Scheduling watchdog job, interval=" + WATCHDOG_INTERVAL);
            long intervalMs = WATCHDOG_INTERVAL.toMillis();
            jobScheduler.schedule(new JobInfo.Builder(SYNC_JOB_ID, new ComponentName(context, WatchdogJobService.class))
                    .setRequiresCharging(false)
                    .setMinimumLatency(intervalMs)
                    .setBackoffCriteria(intervalMs, JobInfo.BACKOFF_POLICY_LINEAR)
                    .build()
            );
        }
    }
}
