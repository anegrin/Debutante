package io.github.debutante.listeners;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;

import java.io.IOException;

import io.github.debutante.helper.EntityHelper;
import io.github.debutante.helper.L;
import io.github.debutante.helper.PlayerWrapper;
import io.github.debutante.helper.URIHelper;
import io.github.debutante.persistence.PlayerState;
import io.github.debutante.receivers.CastMenuItemBroadcastReceiver;
import io.github.debutante.receivers.ChangeMediaItemBroadcastReceiver;
import io.github.debutante.service.MediaDownloadService;
import io.github.debutante.service.PlayerService;

public class ExoPlayerListener extends BasePlayerListener {

    private final DownloadManager downloadManager;
    private final PlayerNotificationManager playerNotificationManager;
    private final int songsToPreload;
    private final PlayerWrapper playerWrapper;

    public ExoPlayerListener(Context context, ExoPlayer exoPlayer, DownloadManager downloadManager, PlayerNotificationManager playerNotificationManager, int songsToPreload, PlayerWrapper playerWrapper) {
        super(context, exoPlayer);
        this.downloadManager = downloadManager;
        this.playerNotificationManager = playerNotificationManager;
        this.songsToPreload = songsToPreload;
        this.playerWrapper = playerWrapper;
    }

    @Override
    public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
        if (mediaItem != null) {
            L.i("Exo onMediaItemTransition " + mediaItem.mediaId);

            ChangeMediaItemBroadcastReceiver.broadcast(context, mediaItem.mediaId);

            boolean remote = URIHelper.isRemote(mediaItem.mediaMetadata.extras.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI));
            CastMenuItemBroadcastReceiver.broadcast(context, remote);

            if (remote) {
                EntityHelper.EntityMetadata metadata = EntityHelper.metadata(mediaItem.mediaId);
                PlayerState.persistCurrentMediaItemId(context, metadata.accountUuid, mediaItem.mediaId);
            }

            int currentWindowIndex = player.getCurrentMediaItemIndex();
            for (int i = 1; i <= songsToPreload; i++) {
                int nextIndex = currentWindowIndex + i;
                if (player.getMediaItemCount() > nextIndex) {
                    MediaItem nextMediaItem = player.getMediaItemAt(nextIndex);
                    Uri uri = nextMediaItem.localConfiguration.uri;
                    if (URIHelper.isRemote(uri)) {
                        try {
                            Download download = downloadManager.getDownloadIndex().getDownload(uri.toString());
                            if (download == null) {
                                DownloadRequest downloadRequest = new DownloadRequest.Builder(uri.toString(), uri).build();
                                L.i("Requesting download of " + uri);
                                MediaDownloadService.sendAddDownload(context, downloadRequest);
                            }
                        } catch (IOException ioe) {
                            L.e("Can't check if " + uri + " has been downloaded", ioe);
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
            L.d("Resuming downloads");
            MediaDownloadService.sendResumeDownloads(context);
        }

        if (isPlaying && playerWrapper.isCasting()) {
            player.stop();
        } else {
            boolean stopped = !isPlaying && player.getCurrentMediaItem() == null;
            playerNotificationManager.setPlayer(stopped ? null : player);
            if (stopped) {
                context.stopService(new Intent(context, PlayerService.class));
            }
        }
    }
}
