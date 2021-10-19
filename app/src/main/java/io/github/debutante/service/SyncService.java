package io.github.debutante.service;

import android.content.Intent;

import io.github.debutante.Debutante;
import io.github.debutante.R;
import io.github.debutante.helper.L;
import io.github.debutante.receivers.SyncAccountBroadcastReceiver;

public class SyncService extends BaseForegroundService {

    private static final int NOTIFICATION_ID = Debutante.NOTIFICATION_ID + 2;
    private static final Object GLOBAL_LOCK = new Object();


    public SyncService() {
        super(R.string.sync_service_notification_content, NOTIFICATION_ID, true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        synchronized (GLOBAL_LOCK) {
            L.i("Starting foreground sync service");
            return super.onStartCommand(intent, flags, startId);
        }
    }

    @Override
    protected void doStopSelf() {
        SyncAccountBroadcastReceiver.broadcastForceStop(this);
        super.doStopSelf();
    }
}
