package io.github.debutante.receivers;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.Nullable;

import java.util.Optional;

import io.github.debutante.helper.L;

public class MediaButtonReceiver extends androidx.media.session.MediaButtonReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        L.i("MediaButtonReceiver.onReceive " + intent.getAction() + ", extras: " + L.toString(intent.getExtras()));
        super.onReceive(context, intent);
    }

    @Override
    public IBinder peekService(Context myContext, Intent service) {
        L.i("MediaButtonReceiver.peekService " + Optional.ofNullable(service).map(Intent::getAction).orElse("null") + ", extras: " + L.toString(Optional.ofNullable(service).map(Intent::getExtras).orElse(new Bundle())));
        IBinder iBinder = super.peekService(myContext, service);
        L.i("MediaButtonReceiver.iBinder " + iBinder);
        return iBinder;
    }

    @Override
    public int getSentFromUid() {
        int sentFromUid = super.getSentFromUid();
        L.i("MediaButtonReceiver.sentFromUid: " + sentFromUid);
        return sentFromUid;
    }

    @Nullable
    @Override
    public String getSentFromPackage() {
        String sentFromPackage = super.getSentFromPackage();
        L.i("MediaButtonReceiver.sentFromPackage: " + sentFromPackage);
        return sentFromPackage;
    }
}
