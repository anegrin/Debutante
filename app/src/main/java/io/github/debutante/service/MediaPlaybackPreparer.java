package io.github.debutante.service;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
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

public class MediaPlaybackPreparer implements MediaSessionConnector.PlaybackPreparer, MediaSessionConnector.QueueEditor {

    public static final long SUPPORTED_PREPARE_ACTIONS =
            PlaybackStateCompat.ACTION_PREPARE
                    | PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID
                    | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                    | PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH
                    | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                    | PlaybackStateCompat.ACTION_PREPARE_FROM_URI
                    | PlaybackStateCompat.ACTION_PLAY_FROM_URI
                    | PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
                    | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
    private final Context context;
    private final MediaSessionCompat mediaSession;
    private final PlayerWrapper playerWrapper;
    private final EntityRepository repository;

    public MediaPlaybackPreparer(Context context, MediaSessionCompat mediaSession, PlayerWrapper playerWrapper, EntityRepository repository) {
        this.context = context;
        this.mediaSession = mediaSession;
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

        if (mediaItems.isPresent()) {
            Pair<MediaBrowserCompat.MediaItem, List<MediaBrowserCompat.MediaItem>> p = mediaItems.get();
            playerWrapper.newPlayerPreparer().prepare(p.getKey(), p.getValue(), currentMediaItemId.orElse(null), () -> {
            }, Throwable::printStackTrace, playWhenReady, false);
        } else {
            playerWrapper.newPlayerPreparer().prepare(() -> {
            }, Throwable::printStackTrace);
        }
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
                onPrepareFromMediaIdResult(mediaId, playWhenReady, found);
            });
        }
    }

    private void onPrepareFromMediaIdResult(String mediaId, boolean playWhenReady, MediaBrowserCompat.MediaItem result) {
        EntityHelper.EntityMetadata metadata = EntityHelper.metadata(result.getMediaId());

        String parentId = metadata.type == SongEntity.class ? EntityHelper.mediaId(new AlbumEntity(metadata.params.get(EntityHelper.EntityMetadata.PARENT_UUID_PARAM), metadata.accountUuid, null, null, null, 0, 0, null, 0, null)) : mediaId;
        parentId = EntityHelper.mediaId(parentId, Collections.singletonMap(MediaBrowserHelper.PREPEND_ACTIONS, false));

        MediaBrowserHelper.loadChildrenFromService(context, repository, parentId, children -> playerWrapper.newPlayerPreparer().prepare(result, children, mediaId, () -> {
        }, Throwable::printStackTrace, playWhenReady));
    }

    @Override
    public void onPrepareFromSearch(@NonNull String query, boolean playWhenReady, @Nullable Bundle extras) {
        L.i("onPrepareFromSearch: " + query);
        MediaBrowserHelper.search(context, repository, query, found -> {
            found.stream().findFirst().ifPresent(r -> {
                onPrepareFromMediaIdResult(r.getMediaId(), playWhenReady, r);
            });
        });
    }

    @Override
    public void onPrepareFromUri(@NonNull Uri uri, boolean playWhenReady, @Nullable Bundle extras) {
        L.i("onPrepareFromUri: " + uri);

        onPrepareFromMediaId(uri.toString(), playWhenReady, extras);
    }

    @Override
    public boolean onCommand(@NonNull Player player, @NonNull String command, @Nullable Bundle extras, @Nullable ResultReceiver cb) {
        L.d("onCommand: " + command);
        return false;
    }

    @Override
    public void onAddQueueItem(Player player, MediaDescriptionCompat description) {
        L.d("onAddQueueItem");

    }

    @Override
    public void onAddQueueItem(Player player, MediaDescriptionCompat description, int index) {
        L.d("onAddQueueItem @" + index);

    }

    @Override
    public void onRemoveQueueItem(Player player, MediaDescriptionCompat description) {
        L.d("onRemoveQueueItem");

    }
}
