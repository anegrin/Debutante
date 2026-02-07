package io.github.debutante.service;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;

import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;

import io.github.debutante.Debutante;
import io.github.debutante.R;
import io.github.debutante.helper.L;
import io.github.debutante.receivers.SyncAccountBroadcastReceiver;

public class SyncService extends BaseForegroundService {

    private static final int NOTIFICATION_ID = Debutante.NOTIFICATION_ID + 2;
    private static final Object GLOBAL_LOCK = new Object();

    @RequiresApi(api = Build.VERSION_CODES.R)
    public SyncService() {
        super(R.string.sync_service_notification_content, NOTIFICATION_ID, FOREGROUND_SERVICE_TYPE_DATA_SYNC, true);
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
