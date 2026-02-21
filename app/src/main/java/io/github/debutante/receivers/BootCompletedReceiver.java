package io.github.debutante.receivers;

import static android.content.Intent.ACTION_BOOT_COMPLETED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.debutante.helper.L;
import io.github.debutante.helper.Scheduler;
import io.github.debutante.model.AppConfig;

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            L.i("Receiving " + intent.getAction() + " " + L.toString(intent.getExtras()));
            //context.startService(Obj.tap(new Intent(context, InitService.class), i -> i.setAction(InitService.ACTION_PREPARE)));
            new Scheduler(context).scheduleSync(new AppConfig(context), false);
        }
    }
}
