package io.github.debutante.service;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.github.debutante.helper.EntityHelper;
import io.github.debutante.helper.L;
import io.github.debutante.helper.MediaBrowserHelper;
import io.github.debutante.helper.PlayerWrapper;
import io.github.debutante.persistence.EntityRepository;
import io.github.debutante.persistence.PlayerState;
import io.github.debutante.persistence.entities.AlbumEntity;
import io.github.debutante.persistence.entities.SongEntity;

public class MediaPlaybackPreparer implements MediaSessionConnector.PlaybackPreparer {

    private static final long SUPPORTED_PREPARE_ACTIONS =
            PlaybackStateCompat.ACTION_PREPARE
                    | PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID
                    | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID;
    private final Context context;
    private final PlayerWrapper playerWrapper;
    private final EntityRepository repository;

    public MediaPlaybackPreparer(Context context, PlayerWrapper playerWrapper, EntityRepository repository) {
        this.context = context;
        this.playerWrapper = playerWrapper;
        this.repository = repository;
    }

    @Override
    public long getSupportedPrepareActions() {
        return SUPPORTED_PREPARE_ACTIONS;
    }

    @Override
    public void onPrepare(boolean playWhenReady) {

        L.i("onPrepare, playWhenReady=" + playWhenReady);

        Optional<Pair<MediaBrowserCompat.MediaItem, List<MediaBrowserCompat.MediaItem>>> mediaItems = PlayerState.loadMediaItems(context, Optional.empty());
        Optional<String> currentMediaItemId = PlayerState.loadCurrentMediaItemId(context, Optional.empty());

        mediaItems.ifPresent(p -> playerWrapper.newPlayerPreparer().prepare(p.getKey(), p.getValue(), currentMediaItemId.orElse(null), () -> {
                }, Throwable::printStackTrace, playWhenReady, false)
        );
    }

    private void onPrepare(String accountUuid, boolean playWhenReady) {

        Optional<Pair<MediaBrowserCompat.MediaItem, List<MediaBrowserCompat.MediaItem>>> mediaItems = PlayerState.loadMediaItems(context, Optional.of(accountUuid));
        Optional<String> currentMediaItemId = PlayerState.loadCurrentMediaItemId(context, Optional.of(accountUuid));

        mediaItems.ifPresent(p -> playerWrapper.newPlayerPreparer().prepare(p.getKey(), p.getValue(), currentMediaItemId.orElse(null), () -> {
                }, Throwable::printStackTrace, playWhenReady, false)
        );
    }

    @Override
    public void onPrepareFromMediaId(String mediaId, boolean playWhenReady, @Nullable Bundle extras) {

        L.i("onPrepareFromMediaId: " + mediaId);

        if (mediaId.startsWith(MediaBrowserHelper.PREVIOUS_SESSION_ID)) {
            onPrepare(mediaId.substring(MediaBrowserHelper.PREVIOUS_SESSION_ID.length()), playWhenReady);
        } else {
            MediaBrowserHelper.load(context, repository, mediaId, found -> {

                EntityHelper.EntityMetadata metadata = EntityHelper.metadata(found.getMediaId());

                String parentId = metadata.type == SongEntity.class ? EntityHelper.mediaId(new AlbumEntity(metadata.params.get(EntityHelper.EntityMetadata.PARENT_UUID_PARAM), metadata.accountUuid, null, null, null, 0, 0, null, 0, null)) : mediaId;
                parentId = EntityHelper.mediaId(parentId, Collections.singletonMap(MediaBrowserHelper.PREPEND_ACTIONS, false));

                MediaBrowserHelper.loadChildrenFromService(context, repository, parentId, children -> playerWrapper.newPlayerPreparer().prepare(found, children, mediaId, () -> {
                }, Throwable::printStackTrace, playWhenReady));
            });
        }
    }

    @Override
    public void onPrepareFromSearch(String query, boolean playWhenReady, @Nullable Bundle extras) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onPrepareFromUri(Uri uri, boolean playWhenReady, @Nullable Bundle extras) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean onCommand(Player player, String command, @Nullable Bundle extras, @Nullable ResultReceiver cb) {
        L.d("onCommand: " + command);
        return false;
    }
}
