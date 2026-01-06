package io.github.debutante.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.debutante.helper.L;
import io.github.debutante.helper.Scheduler;
import io.github.debutante.model.AppConfig;

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        L.i("Receiving " + intent.getAction() + " " + L.toString(intent.getExtras()));
        new Scheduler(context).scheduleSync(new AppConfig(context), false);
    }
}
