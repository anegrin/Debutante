package io.github.debutante.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.os.IBinder;

import androidx.annotation.Nullable;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.debutante.Debutante;
import io.github.debutante.R;
import io.github.debutante.adapter.MediaDescriptionAdapter;
import io.github.debutante.helper.DeviceHelper;
import io.github.debutante.helper.L;
import io.github.debutante.helper.Obj;

public abstract class BaseForegroundService extends Service {

    private final AtomicBoolean receiverLock = new AtomicBoolean(false);

    public static final String ACTION_STOP = BaseForegroundService.class.getSimpleName() + "-ACTION_STOP";
    public static final int STOP_SERVICE_REQUEST_CODE = 1;

    private final StopBroadcastReceiver stopBroadcastReceiver = new StopBroadcastReceiver();

    private final int notifictionContentResId;
    private final int notificationId;
    private final boolean progressing;

    public BaseForegroundService(int notifictionContentResId, int notificationId) {
        this(notifictionContentResId, notificationId, false);
    }

    public BaseForegroundService(int notifictionContentResId, int notificationId, boolean progressing) {
        this.notifictionContentResId = notifictionContentResId;
        this.notificationId = notificationId;
        this.progressing = progressing;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (!receiverLock.getAndSet(true)) {
            L.i("Registering stop service receiver");
            registerReceiver(stopBroadcastReceiver, new IntentFilter(ACTION_STOP), DeviceHelper.doNotRequireReceiverFlags() ? 0 : RECEIVER_EXPORTED);
        }

        PendingIntent deleteIntent = PendingIntent.getBroadcast(this, STOP_SERVICE_REQUEST_CODE, new Intent(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification =
                new Notification.Builder(this, Debutante.createNotificationChannel(this, Debutante.NOTIFICATION_CHANNEL_ID))
                        .setContentTitle(getText(R.string.app_name))
                        .setContentText(getString(notifictionContentResId))
                        .setSmallIcon(R.drawable.ic_launcher_notification)
                        .addAction(new Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                                getString(R.string.player_service_stop),
                                deleteIntent)
                                .build())
                        .setContentIntent(getActivityIntent()
                                .map(i -> PendingIntent.getActivity(this, MediaDescriptionAdapter.OPEN_ACTIVITY_INTENT_REQUEST_CODE, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                                .orElse(null)
                        )
                        .setDeleteIntent(deleteIntent)
                        .setProgress(0, 0, progressing)
                        .setAutoCancel(false)
                        .build();

        startForeground(notificationId, notification);
        return START_NOT_STICKY;
    }

    protected Optional<Intent> getActivityIntent() {
        return Optional.empty();
    }

    @Override
    public boolean stopService(Intent name) {
        return Obj.tap(super.stopService(name), r -> unregisterReceiver());
    }

    @Override
    public void onDestroy() {
        unregisterReceiver();
        super.onDestroy();
    }

    private void unregisterReceiver() {
        if (receiverLock.getAndSet(false)) {
            L.i("Unregistering stop service receiver");
            unregisterReceiver(stopBroadcastReceiver);
        }
    }

    protected void doStopSelf() {
        unregisterReceiver();
        stopSelf();
    }

    protected Debutante d() {
        return (Debutante) getApplication();
    }

    private final class StopBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            L.i("Stopping " + BaseForegroundService.this.getClass().getSimpleName() + " service");
            doStopSelf();
        }
    }
}
