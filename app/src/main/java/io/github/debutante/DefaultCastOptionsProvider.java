package io.github.debutante;

import android.content.Context;

import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;
import com.google.android.gms.cast.framework.media.CastMediaOptions;
import com.google.android.gms.cast.framework.media.MediaIntentReceiver;
import com.google.android.gms.cast.framework.media.NotificationOptions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DefaultCastOptionsProvider implements OptionsProvider {

    @Override
    public CastOptions getCastOptions(Context context) {
        return new CastOptions.Builder()
                .setResumeSavedSession(false)
                .setEnableReconnectionService(true)
                .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
                .setStopReceiverApplicationWhenEndingSession(true)
                .setCastMediaOptions(new CastMediaOptions.Builder()
                        .setNotificationOptions(new NotificationOptions.Builder()
                                .setActions(Arrays.asList(MediaIntentReceiver.ACTION_SKIP_PREV,
                                        MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK,
                                        MediaIntentReceiver.ACTION_SKIP_NEXT,
                                        MediaIntentReceiver.ACTION_STOP_CASTING), new int[]{0, 1, 2})
                                .setTargetActivityClassName(MainActivity.class.getName())
                                .build())
                        .build())
                .build();
    }

    @Override
    public List<SessionProvider> getAdditionalSessionProviders(Context context) {
        return Collections.emptyList();
    }
}
