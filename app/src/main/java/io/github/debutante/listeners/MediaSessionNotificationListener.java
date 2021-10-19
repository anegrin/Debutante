package io.github.debutante.listeners;

import android.content.Context;
import android.content.Intent;

import com.google.android.exoplayer2.ui.PlayerNotificationManager;

import io.github.debutante.Debutante;
import io.github.debutante.helper.PlayerWrapper;
import io.github.debutante.service.PlayerService;

public class MediaSessionNotificationListener implements PlayerNotificationManager.NotificationListener {
    private final Context context;
    private final PlayerWrapper playerWrapper;

    public MediaSessionNotificationListener(Context context, PlayerWrapper playerWrapper) {
        this.context = context;
        this.playerWrapper = playerWrapper;
    }

    @Override
    public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
        if (notificationId == Debutante.NOTIFICATION_ID && dismissedByUser) {
            if (!playerWrapper.player().isPlaying()) {
                context.stopService(new Intent(context, PlayerService.class));
            }
        }
    }
}
