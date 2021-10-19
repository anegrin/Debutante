package io.github.debutante.listeners;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;

import io.github.debutante.helper.L;
import io.github.debutante.persistence.PlayerState;
import io.github.debutante.receivers.ChangeIsPlayingBroadcastReceiver;

public abstract class BasePlayerListener implements Player.Listener {
    private static final String REPEAT_MODE_KEY = "repeat_mode";
    protected final Context context;
    protected final Player player;
    private final SharedPreferences sharedPreferences;
    private int repeatMode;

    public BasePlayerListener(Context context, Player player) {
        this.context = context;
        this.player = player;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        repeatMode = sharedPreferences.getInt(REPEAT_MODE_KEY, Player.REPEAT_MODE_ALL);
        player.setRepeatMode(repeatMode);
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        synchronized (REPEAT_MODE_KEY) {
            if (this.repeatMode != repeatMode) {
                try {
                    SharedPreferences.Editor edit = sharedPreferences.edit();
                    edit.putInt(REPEAT_MODE_KEY, repeatMode);
                    edit.commit();
                } catch (Exception e) {
                    L.v("Can't save repeatMode state", e);
                }
                this.repeatMode = repeatMode;
            }
        }
    }

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
        ChangeIsPlayingBroadcastReceiver.broadcast(context, playWhenReady);
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {

        if (!isPlaying) {
            MediaItem currentMediaItem = player.getCurrentMediaItem();
            if (currentMediaItem != null) {
                long currentPosition = player.getCurrentPosition();
                PlayerState.persistCurrentMediaItemPosition(context, currentMediaItem.mediaId, currentPosition);
            }

        }

        ChangeIsPlayingBroadcastReceiver.broadcast(context, isPlaying);
    }
}
