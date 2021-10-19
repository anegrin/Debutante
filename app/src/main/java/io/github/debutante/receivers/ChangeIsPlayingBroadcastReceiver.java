
package io.github.debutante.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.ImageButton;

import io.github.debutante.R;
import io.github.debutante.helper.L;
import io.github.debutante.helper.Obj;

public class ChangeIsPlayingBroadcastReceiver extends BroadcastReceiver {
    public static final String ACTION = ChangeIsPlayingBroadcastReceiver.class.getSimpleName() + "-ACTION";
    public static final String PLAYING_KEY = ChangeIsPlayingBroadcastReceiver.class.getSimpleName() + "-PLAYING_KEY";
    private final ImageButton ibPlayPause;

    public ChangeIsPlayingBroadcastReceiver(ImageButton ibPlayPause) {
        this.ibPlayPause = ibPlayPause;
    }

    private static void logBroadcast(Intent intent) {
        L.i("Broadcasting " + ACTION + " " + L.toString(intent.getExtras()));
    }

    public static void broadcast(Context context, boolean isPlaying) {
        Intent intent = new Intent(ChangeIsPlayingBroadcastReceiver.ACTION);
        intent.putExtra(PLAYING_KEY, isPlaying);
        context.sendBroadcast(Obj.tap(intent, ChangeIsPlayingBroadcastReceiver::logBroadcast));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        L.i("Receiving " + intent.getAction() + " " + L.toString(intent.getExtras()));
        boolean isPlaying = intent.getBooleanExtra(PLAYING_KEY, false);
        ibPlayPause.setImageResource(isPlaying ? R.drawable.exo_styled_controls_pause : R.drawable.exo_styled_controls_play);
    }
}
