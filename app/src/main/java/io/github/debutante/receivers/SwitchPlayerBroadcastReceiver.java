
package io.github.debutante.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.StyledPlayerView;

import io.github.debutante.helper.L;

public class SwitchPlayerBroadcastReceiver extends BroadcastReceiver {
    public static final String ACTION = SwitchPlayerBroadcastReceiver.class.getSimpleName() + "-ACTION";
    private final StyledPlayerView styledPlayerView;

    public SwitchPlayerBroadcastReceiver(StyledPlayerView styledPlayerView) {
        this.styledPlayerView = styledPlayerView;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Player player = styledPlayerView.getPlayer();
        styledPlayerView.setPlayer(null);
        styledPlayerView.setPlayer(player);
        styledPlayerView.showController();
    }

    public static void broadcast(Context context) {
        L.i("Broadcasting " + ACTION);
        Intent intent = new Intent(SwitchPlayerBroadcastReceiver.ACTION);
        context.sendBroadcast(intent);
    }
}
