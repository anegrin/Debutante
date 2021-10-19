
package io.github.debutante.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.function.Consumer;

import io.github.debutante.helper.L;
import io.github.debutante.helper.Obj;

public class CastMenuItemBroadcastReceiver extends BroadcastReceiver {
    public static final String ACTION = CastMenuItemBroadcastReceiver.class.getSimpleName() + "-ACTION";
    public static final String ENABLED_KEY = CastMenuItemBroadcastReceiver.class.getSimpleName() + "-ENABLED_KEY";
    private final Consumer<Boolean> callback;

    public CastMenuItemBroadcastReceiver(Consumer<Boolean> callback) {
        this.callback = callback;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        callback.accept(intent.getBooleanExtra(ENABLED_KEY, true));
    }

    private static void logBroadcast(Intent intent) {
        L.i("Broadcasting " + ACTION + " " + L.toString(intent.getExtras()));
    }

    public static void broadcast(Context context, boolean enabled) {
        Intent intent = new Intent(ACTION);
        intent.putExtra(ENABLED_KEY, enabled);
        context.sendBroadcast(Obj.tap(intent, CastMenuItemBroadcastReceiver::logBroadcast));
    }
}
