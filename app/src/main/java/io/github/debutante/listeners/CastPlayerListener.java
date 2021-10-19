package io.github.debutante.listeners;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.framework.PrecacheManager;

import java.time.Duration;

import io.github.debutante.adapter.AudioMediaItemConverter;
import io.github.debutante.helper.EntityHelper;
import io.github.debutante.helper.L;
import io.github.debutante.helper.PlayerWrapper;
import io.github.debutante.helper.RxHelper;
import io.github.debutante.helper.URIHelper;
import io.github.debutante.persistence.PlayerState;
import io.github.debutante.receivers.ChangeMediaItemBroadcastReceiver;
import io.github.debutante.service.MediaDownloadService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.Disposable;

public class CastPlayerListener extends BasePlayerListener {
    private final PrecacheManager precacheManager;
    private final AudioMediaItemConverter mediaItemConverter;
    private final PlayerWrapper playerWrapper;
    private Disposable precacheDisposable;

    public CastPlayerListener(Context context, CastPlayer castPlayer, PrecacheManager precacheManager, AudioMediaItemConverter mediaItemConverter, PlayerWrapper playerWrapper) {
        super(context, castPlayer);
        this.precacheManager = precacheManager;
        this.mediaItemConverter = mediaItemConverter;
        this.playerWrapper = playerWrapper;
    }

    @Override
    public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {

        synchronized (this) {
            if (precacheDisposable != null && !precacheDisposable.isDisposed()) {
                precacheDisposable.dispose();
            }
        }

        MediaItem mediaItemAt = playerWrapper.player().getCurrentMediaItem();
        if (mediaItemAt != null) {
            L.i("Cast onMediaItemTransition " + mediaItemAt.mediaId);

            ChangeMediaItemBroadcastReceiver.broadcast(context, mediaItemAt.mediaId);
            if (URIHelper.isRemote(mediaItemAt.mediaMetadata.extras.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI))) {
                EntityHelper.EntityMetadata metadata = EntityHelper.metadata(mediaItemAt.mediaId);
                PlayerState.persistCurrentMediaItemId(context, metadata.accountUuid, mediaItemAt.mediaId);
            }

            int nextIndex = player.getCurrentMediaItemIndex() + 1;
            if (player.getMediaItemCount() > nextIndex) {
                MediaItem nextMediaItem = playerWrapper.player().getMediaItemAt(nextIndex);
                MediaQueueItem mediaQueueItem = mediaItemConverter.toMediaQueueItem(nextMediaItem);
                String uri = mediaQueueItem.getMedia().getContentUrl();
                Bundle extras = nextMediaItem.mediaMetadata.extras;

                synchronized (this) {
                    if (extras != null && URIHelper.isRemote(uri)) {

                        long delay = extras.getLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1);

                        if (delay > 0) {
                            precacheDisposable = RxHelper.defaultInstance().subscribe(Duration.ofMillis(delay).minus(AudioMediaItemConverter.PRELOAD_TIME.multipliedBy(2)),
                                    Completable.fromAction(() -> L.d("Scheduling (delay=" + delay + "ms) precache for next media item: " + uri)),
                                    () -> {
                                        try {
                                            precacheManager.precache(uri);
                                            L.d("Precache next media item: " + uri);
                                        } catch (Exception e) {
                                            L.e("Can't precache next media item: " + uri, e);
                                        }
                                    }, Throwable::printStackTrace);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        super.onIsPlayingChanged(isPlaying);

        if (isPlaying) {
            L.d("Pausing downloads");
            MediaDownloadService.sendPauseDownloads(context);
        }
    }
}
