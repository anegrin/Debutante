package io.github.debutante.helper;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.util.MimeTypes;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.debutante.model.AppConfig;
import io.github.debutante.persistence.EntityRepository;
import io.github.debutante.persistence.PlayerState;
import io.github.debutante.persistence.entities.AccountEntity;
import io.github.debutante.persistence.entities.SongEntity;
import io.github.debutante.service.HTTPDService;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.Consumer;

public class PlayerPreparer {

    private final Context context;
    private final PlayerWrapper playerWrapper;
    private final EntityRepository repository;
    private final AppConfig appConfig;

    PlayerPreparer(Context context, PlayerWrapper playerWrapper, EntityRepository repository, AppConfig appConfig) {
        this.context = context;
        this.playerWrapper = playerWrapper;
        this.repository = repository;
        this.appConfig = appConfig;
    }

    public void prepare(MediaBrowserCompat.MediaItem parentMediaItem,
                        List<MediaBrowserCompat.MediaItem> mediaItems,
                        String mediaItemId,
                        Action onComplete,
                        Consumer<? super Throwable> onError,
                        boolean playWhenReady) {
        prepare(parentMediaItem, mediaItems, mediaItemId, onComplete, onError, playWhenReady, true);
    }

    public void prepare(MediaBrowserCompat.MediaItem parentMediaItem,
                        List<MediaBrowserCompat.MediaItem> mediaItems,
                        String mediaItemId,
                        Action onComplete,
                        Consumer<? super Throwable> onError,
                        boolean playWhenReady,
                        boolean saveSate) {
        prepare(parentMediaItem, mediaItems, mediaItemId, C.TIME_UNSET, onComplete, onError, playWhenReady, saveSate);
    }

    public void prepare(MediaBrowserCompat.MediaItem parentMediaItem,
                        List<MediaBrowserCompat.MediaItem> mediaItems,
                        String mediaItemId,
                        long startPositionMs,
                        Action onComplete,
                        Consumer<? super Throwable> onError,
                        boolean playWhenReady,
                        boolean saveSate) {

        L.i("preaparing player;  parentMediaItem=" + Optional.ofNullable(parentMediaItem).map(MediaBrowserCompat.MediaItem::getMediaId).orElse(null)
                + ", mediaItems.size=" + CollectionUtils.size(mediaItems)
                + ", mediaItemId=" + mediaItemId
                + ", startPositionMs=" + startPositionMs
                + ", playWhenReady=" + playWhenReady
                + ", saveSate=" + saveSate
        );

        String mediaIdForAccountExtraction = parentMediaItem != null ? parentMediaItem.getMediaId() : mediaItemId;
        if (mediaIdForAccountExtraction == null && CollectionUtils.isNotEmpty(mediaItems)) {
            mediaIdForAccountExtraction = mediaItems.get(0).getMediaId();
        }
        if (mediaIdForAccountExtraction == null) {
            return;
        }

        RxHelper.defaultInstance().subscribe(repository.findAccountByUuid(EntityHelper.metadata(mediaIdForAccountExtraction).accountUuid), a -> {

            String loopbackHostname = HTTPDService.hostname(context);

            List<MediaItem> items = new ArrayList<>(mediaItems.size());

            int windowIndex = 0;
            for (int i = 0; i < mediaItems.size(); i++) {
                MediaBrowserCompat.MediaItem mediaItem = mediaItems.get(i);
                String mediaId = mediaItem.getMediaId();
                EntityHelper.EntityMetadata itemMetadata = EntityHelper.metadata(mediaId);
                if (itemMetadata.type == SongEntity.class) {
                    final String uri;
                    String remoteUuid = itemMetadata.params.get(EntityHelper.EntityMetadata.REMOTE_UUID_PARAM);
                    if (AccountEntity.LOCAL.uuid().equals(itemMetadata.accountUuid)) {

                        if (playerWrapper.isCasting()) {
                            uri = "http://" + loopbackHostname + ":" + HTTPDService.SERVER_PORT + "/?" + URIHelper.LOCAL_UUID_PARAM + "=" + URIHelper.urlEncode(HTTPDService.encrypt(itemMetadata.uuid));
                        } else {
                            uri = remoteUuid != null ? new File(remoteUuid).toURI().toString() : "";
                        }
                    } else {
                        uri = SubsonicHelper.buildStreamUrl(a.url,
                                a.username,
                                a.token,
                                remoteUuid,
                                appConfig.getStreamingFormat(),
                                appConfig.getStreamingBitrate()
                        );
                    }
                    items.add(new MediaItem.Builder()
                            .setMimeType(MimeTypes.BASE_TYPE_AUDIO)
                            .setMediaId(mediaItem.getMediaId())
                            .setMediaMetadata(extractMetadata(a, uri, mediaItem.getDescription(), itemMetadata, mediaItems.size()))
                            .setUri(uri).build());
                    if (mediaItem.getMediaId().equals(mediaItemId)) {
                        windowIndex = i;
                    }
                }
            }


            if (saveSate) {
                PlayerState.persistMediaItems(context, a.uuid(), parentMediaItem, mediaItems);
                PlayerState.persistCurrentMediaItemId(context, a.uuid(), mediaItemId);
            }

            Player player = playerWrapper.player();
            player.setPlayWhenReady(playWhenReady);
            player.setMediaItems(items, windowIndex, startPositionMs);
            player.prepare();

            onComplete.run();
        }, onError);
    }

    private MediaMetadata extractMetadata(AccountEntity accountEntity, String uri, MediaDescriptionCompat mediaDescription, EntityHelper.EntityMetadata itemMetadata, int playlistSize) {

        Bundle extras = new Bundle();
        extras.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, uri);

        MediaMetadata.Builder builder = new MediaMetadata.Builder()
                .setExtras(extras)
                .setTotalTrackCount(playlistSize)
                .setTitle(mediaDescription.getTitle())
                .setDescription(mediaDescription.getDescription());

        String coverArt = itemMetadata.params.get(EntityHelper.EntityMetadata.COVER_ART_PARAM);
        String discNumber = itemMetadata.params.get(EntityHelper.EntityMetadata.DISC_NUMBER_PARAM);
        String track = itemMetadata.params.get(EntityHelper.EntityMetadata.TRACK_PARAM);
        String duration = itemMetadata.params.get(EntityHelper.EntityMetadata.DURATION_PARAM);
        String album = itemMetadata.params.get(EntityHelper.EntityMetadata.ALBUM_PARAM);
        String artist = itemMetadata.params.get(EntityHelper.EntityMetadata.ARTIST_PARAM);
        String year = itemMetadata.params.get(EntityHelper.EntityMetadata.YEAR_PARAM);

        if (StringUtils.isNotEmpty(duration)) {
            long durationMs = Long.parseLong(duration) * 1000;
            extras.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs);
        }


        if (StringUtils.isNotBlank(coverArt)) {
            builder.setArtworkUri(Uri.parse(SubsonicHelper.buildCoverArtUrl(accountEntity.url,
                    accountEntity.username,
                    accountEntity.token,
                    coverArt)
            ));
        } else {
            builder.setArtworkUri(MediaBrowserHelper.SONG_ICON_URI);
        }

        if (StringUtils.isNotBlank(discNumber)) {
            builder.setDiscNumber(Integer.parseInt(discNumber));
        }

        if (StringUtils.isNotBlank(track)) {
            builder.setTrackNumber(Integer.parseInt(track));
        }

        if (StringUtils.isNotBlank(album)) {
            builder.setAlbumTitle(album);
        }

        if (StringUtils.isNotBlank(artist)) {
            builder.setArtist(artist);
        }

        if (StringUtils.isNotBlank(year)) {
            builder.setReleaseYear(Integer.parseInt(year));
        }

        return builder.build();

    }

    public void prepareAndPlay(MediaBrowserCompat.MediaItem parentMediaItem, List<MediaBrowserCompat.MediaItem> mediaItems, String mediaItemId, Action onComplete, Consumer<? super Throwable> onError) {
        prepare(parentMediaItem, mediaItems, mediaItemId, onComplete, onError, true);
    }
}
