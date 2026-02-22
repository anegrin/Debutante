package io.github.debutante.receivers;

import android.content.Context;
import android.content.Intent;

import io.github.debutante.helper.L;

public class MediaButtonReceiver extends androidx.media.session.MediaButtonReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            L.i("MediaButtonReceiver.onReceive " + intent.getAction() + " " + L.toString(intent.getExtras()));
        }
        super.onReceive(context, intent);
    }
}
