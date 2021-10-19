package io.github.debutante.listeners;

import android.content.Context;
import android.content.Intent;

import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.github.debutante.Debutante;
import io.github.debutante.helper.L;
import io.github.debutante.helper.PlayerWrapper;
import io.github.debutante.model.AppConfig;
import io.github.debutante.receivers.ChangeIsPlayingBroadcastReceiver;
import io.github.debutante.service.HTTPDService;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class CastSessionAvailabilityListener implements SessionAvailabilityListener {

    private final Duration AVAILABLE_CHECK_DELAY = Duration.ofSeconds(1);
    private final Duration UNAVAILABLE_CHECK_DELAY = Duration.ofSeconds(3);
    private boolean castSessionAvailable = false;
    private final Context context;
    private final PlayerWrapper playerWrapper;
    private final MediaSessionConnector mediaSessionConnector;
    private final AppConfig appConfig;

    public CastSessionAvailabilityListener(Context context, PlayerWrapper playerWrapper, MediaSessionConnector mediaSessionConnector, AppConfig appConfig) {
        this.context = context;
        this.playerWrapper = playerWrapper;
        this.mediaSessionConnector = mediaSessionConnector;
        this.appConfig = appConfig;
    }

    @Override
    public void onCastSessionAvailable() {
        String eventId = UUID.randomUUID().toString();
        Completable.fromAction(() -> {
            L.v("onCastSessionAvailable " + eventId);
            castSessionAvailable = true;
        }).delay(AVAILABLE_CHECK_DELAY.toMillis(), TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    if (castSessionAvailable) {
                        L.v("onCastSessionAvailable confirmed " + eventId);
                        if (appConfig.isCastLocalEnabled()) {
                            context.startForegroundService(new Intent(context, HTTPDService.class));
                        }
                        playerWrapper.swtichToCast();
                        mediaSessionConnector.setPlayer(null);
                        mediaSessionConnector.mediaSession.setActive(false);
                    }
                }, Throwable::printStackTrace);
    }

    @Override
    public void onCastSessionUnavailable() {
        String eventId = UUID.randomUUID().toString();
        Completable.fromAction(() -> {
            L.v("onCastSessionUnavailable " + eventId);
            castSessionAvailable = false;
        }).delay(UNAVAILABLE_CHECK_DELAY.toMillis(), TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    if (!castSessionAvailable) {
                        L.v("onCastSessionUnavailable confirmed " + eventId);
                        if (appConfig.isCastLocalEnabled()) {
                            context.stopService(new Intent(context, HTTPDService.class));
                        }
                        playerWrapper.switchToExo();
                        mediaSessionConnector.setPlayer(playerWrapper.player());
                        mediaSessionConnector.mediaSession.setActive(true);
                        if (Debutante.HANDLE_AUDIO_BECOMING_NOISY) {
                            ChangeIsPlayingBroadcastReceiver.broadcast(context, false);
                        }
                    }
                }, Throwable::printStackTrace);
    }
}
