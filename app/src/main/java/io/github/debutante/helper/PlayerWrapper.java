package io.github.debutante.helper;

import android.content.Context;
import android.content.Intent;
import android.support.v4.media.MediaBrowserCompat;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.cast.CastPlayer;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.github.debutante.model.AppConfig;
import io.github.debutante.persistence.EntityRepository;
import io.github.debutante.persistence.PlayerState;
import io.github.debutante.receivers.SwitchPlayerBroadcastReceiver;
import io.github.debutante.service.PlayerService;

public class PlayerWrapper {

    private final Context context;
    private final ExoPlayer exoPlayer;
    private final CastPlayer castPlayer;
    private final EntityRepository repository;
    private final ExoPlayer.AudioOffloadListener audioOffloadListener;
    private Player activePlayer;
    private final AppConfig appConfig;

    private final Player playerProxy = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Player.class}, new PlayerHandler());
    private List<MediaItem> currentMediaItems = Collections.emptyList();
    private boolean audioOffloadListenerSet;

    public PlayerWrapper(Context context, ExoPlayer exoPlayer, CastPlayer castPlayer, EntityRepository repository, AppConfig appConfig) {
        this.context = context;
        this.exoPlayer = exoPlayer;
        this.castPlayer = castPlayer;
        this.repository = repository;
        this.activePlayer = exoPlayer;
        this.appConfig = appConfig;
        this.audioOffloadListener = new ExoPlayer.AudioOffloadListener() {
            @Override
            public void onExperimentalOffloadSchedulingEnabledChanged(boolean offloadSchedulingEnabled) {
                L.i("ExoPlayer UI is going to be " + (offloadSchedulingEnabled ? "active" : "inactive"));
            }

            @Override
            public void onExperimentalSleepingForOffloadChanged(boolean sleepingForOffload) {
                L.i("ExoPlayer UI is now " + (sleepingForOffload ? "inactive" : "active"));
            }
        };
        if (appConfig.isOffloadEnabled()) {
            setOptionalOffloadSchedulingEnabled(true);
        }

        if (DeviceHelper.supportsAudioOffload()) {
            appConfig.addOnRefreshListeners(a -> setOptionalOffloadSchedulingEnabled(a.isOffloadEnabled()));
        }
    }

    private void setAddAudioOffloadListener() {
        synchronized (exoPlayer) {
            if (!audioOffloadListenerSet) {
                exoPlayer.addAudioOffloadListener(audioOffloadListener);
                audioOffloadListenerSet = true;
            }
        }
    }

    private void removeAddAudioOffloadListener() {
        synchronized (exoPlayer) {
            if (audioOffloadListenerSet) {
                exoPlayer.removeAudioOffloadListener(audioOffloadListener);
                audioOffloadListenerSet = false;
            }
        }
    }

    public void setOptionalOffloadSchedulingEnabled(boolean enabled) {
        if (DeviceHelper.supportsAudioOffload()) {
            setAddAudioOffloadListener();
            exoPlayer.experimentalSetOffloadSchedulingEnabled(enabled);
        } else {
            exoPlayer.experimentalSetOffloadSchedulingEnabled(false);
            removeAddAudioOffloadListener();
        }

    }

    public Player activePlayer() {
        return activePlayer;
    }

    public Player inactivePlayer() {
        return activePlayer instanceof ExoPlayer ? castPlayer : exoPlayer;
    }

    public void swtichToCast() {
        syncState(castPlayer);
    }

    public void switchToExo() {
        syncState(exoPlayer);
    }

    public PlayerPreparer newPlayerPreparer() {
        return new PlayerPreparer(context, this, repository, appConfig);
    }

    private synchronized void syncState(Player newPlayer) {
        synchronized (this) {
            Player previousPlayer = this.activePlayer;

            if (previousPlayer == newPlayer) {
                return;
            }

            L.i("Switching from " + previousPlayer.getClass().getSimpleName() + " to " + newPlayer.getClass().getSimpleName());

            this.activePlayer = newPlayer;

            boolean startPlayer = previousPlayer.isPlaying() && !(previousPlayer instanceof CastPlayer);
            int playbackState = previousPlayer.getPlaybackState();
            int mediaItemCount = previousPlayer.getMediaItemCount();

            previousPlayer.pause();
            newPlayer.pause();

            newPlayer.setRepeatMode(previousPlayer.getRepeatMode());

            if ((mediaItemCount == 0 && previousPlayer instanceof CastPlayer) || CollectionUtils.isEmpty(currentMediaItems)) {
                L.d("No media items in Cast player's queue");
                Optional<Pair<MediaBrowserCompat.MediaItem, List<MediaBrowserCompat.MediaItem>>> mediaItems = PlayerState.loadMediaItems(context, Optional.empty());
                Optional<String> currentMediaItemId = PlayerState.loadCurrentMediaItemId(context, Optional.empty());

                long currentPosition = previousPlayer.getCurrentPosition();

                mediaItems.ifPresent(p -> newPlayerPreparer().prepare(p.getKey(), p.getValue(), currentMediaItemId.orElse(null), currentPosition, () -> {
                        }, Throwable::printStackTrace, startPlayer, false)
                );
            } else if (playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {
                L.d("Copying media items from old player's queue to new player's one");

                int currentWindowIndex = previousPlayer.getCurrentMediaItemIndex();
                long currentPosition = previousPlayer.getCurrentPosition();
                newPlayer.setPlayWhenReady(startPlayer);
                newPlayer.setMediaItems(IntStream.range(0, mediaItemCount).mapToObj(currentMediaItems::get).collect(Collectors.toList()), currentWindowIndex, currentPosition);
                newPlayer.prepare();

            }

            SwitchPlayerBroadcastReceiver.broadcast(context);
        }
    }

    public Player player() {
        return playerProxy;
    }

    private MediaItem getMediaItemAt(int index) {
        if (index >= 0 && CollectionUtils.size(currentMediaItems) > index) {
            return currentMediaItems.get(index);
        } else {
            return null;
        }
    }

    public boolean isCasting() {
        return activePlayer instanceof CastPlayer;
    }

    private class PlayerHandler implements InvocationHandler {
        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                if (method.getName().equals("play")) {
                    Intent service = new Intent(context, PlayerService.class);
                    service.setAction(PlayerService.ACTION_PLAY);
                    context.startForegroundService(service);
                    return Void.class;
                } else if (method.getName().equals("pause")) {
                    Intent service = new Intent(context, PlayerService.class);
                    service.setAction(PlayerService.ACTION_PAUSE);
                    context.startForegroundService(service);
                    return method.invoke(activePlayer, args);
                } else if (method.getName().equals("prepare") && activePlayer.getPlayWhenReady()) {
                    Intent service = new Intent(context, PlayerService.class);
                    service.setAction(PlayerService.ACTION_PREPARE);
                    context.startForegroundService(service);
                    return Void.class;
                } else if (method.getName().equals("stop") /*|| method.getName().equals("pause")*/) {
                    context.stopService(new Intent(context, PlayerService.class));
                    return method.invoke(activePlayer, args);
                } else if (method.getName().equals("setMediaItems")) {

                    if (ArrayUtils.isNotEmpty(args)) {
                        currentMediaItems = (List<MediaItem>) args[0];
                    } else {
                        currentMediaItems = Collections.emptyList();
                    }

                    return method.invoke(activePlayer, args);
                } else if (method.getName().equals("getCurrentMediaItem")) {
                    MediaItem mediaItemAt = getMediaItemAt(activePlayer.getCurrentMediaItemIndex());
                    return mediaItemAt != null ? mediaItemAt : method.invoke(activePlayer, args);
                } else if (method.getName().equals("getMediaItemAt")) {
                    MediaItem mediaItemAt = getMediaItemAt((Integer) args[0]);
                    return mediaItemAt != null ? mediaItemAt : method.invoke(activePlayer, args);
                } else {
                    return method.invoke(activePlayer, args);
                }
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }
    }
}
