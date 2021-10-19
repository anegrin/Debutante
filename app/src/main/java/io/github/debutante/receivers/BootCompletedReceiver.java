package io.github.debutante.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.debutante.helper.L;
import io.github.debutante.helper.Obj;
import io.github.debutante.helper.Scheduler;
import io.github.debutante.model.AppConfig;
import io.github.debutante.service.InitService;

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        L.i("Receiving " + intent.getAction() + " " + L.toString(intent.getExtras()));
        context.startForegroundService(Obj.tap(new Intent(context, InitService.class), i -> i.setAction(InitService.ACTION_PREPARE)));
        new Scheduler(context).scheduleSync(new AppConfig(context), false);
    }
}
