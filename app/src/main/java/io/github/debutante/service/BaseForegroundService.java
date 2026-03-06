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

import androidx.annotation.NonNull;
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

    private static final int STOP_SERVICE_REQUEST_CODE = 1;
    private final String ACTION_STOP = getClass().getSimpleName() + "-ACTION_STOP";

    private final StopBroadcastReceiver stopBroadcastReceiver = new StopBroadcastReceiver();

    private final int notifictionContentResId;
    private final int notificationId;
    private final boolean progressing;
    private int foregroundServiceType;

    public BaseForegroundService(int notifictionContentResId, int notificationId, int foregroundServiceType) {
        this(notifictionContentResId, notificationId, foregroundServiceType, false);
    }

    public BaseForegroundService(int notifictionContentResId, int notificationId, int foregroundServiceType, boolean progressing) {
        this.notifictionContentResId = notifictionContentResId;
        this.notificationId = notificationId;
        this.foregroundServiceType = foregroundServiceType;
        this.progressing = progressing;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        L.i("Binding " + getClass().getSimpleName());
        return new LocalBinder<>(this);
    }

    @NonNull
    static Notification buildNotification(Context context, Optional<Intent> activityIntent, int notifictionContentResId, boolean progressing, @Nullable PendingIntent deleteIntent) {
        Notification.Builder builder = new Notification.Builder(context, Debutante.createNotificationChannel(context, Debutante.NOTIFICATION_CHANNEL_ID))
                .setContentTitle(context.getText(R.string.app_name))
                .setContentText(context.getString(notifictionContentResId))
                .setSmallIcon(R.drawable.ic_launcher_notification)
                .setContentIntent(activityIntent
                        .map(i -> PendingIntent.getActivity(context, MediaDescriptionAdapter.OPEN_ACTIVITY_INTENT_REQUEST_CODE, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                        .orElse(null)
                )
                .setProgress(0, 0, progressing)
                .setAutoCancel(false);

        if (deleteIntent != null) {
            builder = builder
                    .addAction(new Notification.Action.Builder(Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                            context.getString(R.string.player_service_stop),
                            deleteIntent)
                            .build())
                    .setDeleteIntent(deleteIntent);
        }

        Notification notification = builder.build();
        return notification;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (!receiverLock.getAndSet(true)) {
            L.i("Registering stop service receiver");
            registerReceiver(stopBroadcastReceiver, new IntentFilter(ACTION_STOP), DeviceHelper.doNotRequireReceiverFlags() ? 0 : RECEIVER_EXPORTED);
        }

        PendingIntent deleteIntent = PendingIntent.getBroadcast(this, STOP_SERVICE_REQUEST_CODE, new Intent(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = buildNotification(this, getActivityIntent(), notifictionContentResId, progressing, deleteIntent);

        if (DeviceHelper.needsForegroundServiceTypeOnStart()) {
            startForeground(notificationId, notification, foregroundServiceType);
        } else {
            startForeground(notificationId, notification);
        }
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
