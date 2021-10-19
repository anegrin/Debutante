package io.github.debutante.adapter;

import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ext.cast.MediaItemConverter;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.common.images.WebImage;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;

import io.github.debutante.helper.MediaBrowserHelper;
import io.github.debutante.helper.SubsonicHelper;
import io.github.debutante.helper.URIHelper;

public class AudioMediaItemConverter implements MediaItemConverter {

    private static final String KEY_MEDIA_ID = "com.google.android.gms.cast.metadata.MEDIA_ID";
    private static final int IMG_SIZE = SubsonicHelper.IMG_SIZE;
    public static final Duration PRELOAD_TIME = Duration.ofSeconds(10);

    @Override
    public MediaQueueItem toMediaQueueItem(MediaItem mediaItem) {
        MediaMetadata metadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);

        metadata.putString(KEY_MEDIA_ID, mediaItem.mediaId);

        if (mediaItem.mediaMetadata.title != null) {
            metadata.putString(MediaMetadata.KEY_TITLE, mediaItem.mediaMetadata.title.toString());
        }
        if (mediaItem.mediaMetadata.trackNumber != null) {
            metadata.putInt(MediaMetadata.KEY_TRACK_NUMBER, mediaItem.mediaMetadata.trackNumber);
        }
        if (mediaItem.mediaMetadata.discNumber != null) {
            metadata.putInt(MediaMetadata.KEY_DISC_NUMBER, mediaItem.mediaMetadata.discNumber);
        }

        if (mediaItem.mediaMetadata.albumTitle != null) {
            metadata.putString(MediaMetadata.KEY_ALBUM_TITLE, mediaItem.mediaMetadata.albumTitle.toString());
        }

        if (mediaItem.mediaMetadata.artist != null) {
            metadata.putString(MediaMetadata.KEY_ARTIST, mediaItem.mediaMetadata.artist.toString());
        }

        if (mediaItem.mediaMetadata.albumArtist != null) {
            metadata.putString(MediaMetadata.KEY_ALBUM_ARTIST, mediaItem.mediaMetadata.albumArtist.toString());
        }

        if (URIHelper.isRemote(mediaItem.mediaMetadata.artworkUri)) {
            metadata.addImage(new WebImage(mediaItem.mediaMetadata.artworkUri, IMG_SIZE, IMG_SIZE));
        }

        MediaInfo mediaInfo = new MediaInfo.Builder(mediaItem.mediaId)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(mediaItem.localConfiguration.mimeType)
                .setContentUrl(SubsonicHelper.withCastClient(mediaItem.localConfiguration.uri.toString()))
                .setMetadata(metadata)
                .build();
        MediaQueueItem.Builder builder = new MediaQueueItem.Builder(mediaInfo);

        if (mediaItem.mediaMetadata.extras.containsKey(MediaMetadataCompat.METADATA_KEY_DURATION)) {
            builder.setPlaybackDuration(mediaItem.mediaMetadata.extras.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000d);
            builder.setPreloadTime(PRELOAD_TIME.getSeconds());
        }

        return builder.build();
    }

    @Override
    public MediaItem toMediaItem(MediaQueueItem mediaQueueItem) {

        MediaInfo info = mediaQueueItem.getMedia();
        String contentUrl = SubsonicHelper.withAppClient(info.getContentUrl());
        String contentType = info.getContentType();

        MediaMetadata metadata = info.getMetadata();

        Bundle extras = new Bundle();
        extras.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, contentUrl);
        com.google.android.exoplayer2.MediaMetadata.Builder metadataBuilder = new com.google.android.exoplayer2.MediaMetadata.Builder()
                .setExtras(extras)
                .setTitle(metadata.getString(MediaMetadata.KEY_TITLE));

        int discNumber = metadata.getInt(MediaMetadata.KEY_DISC_NUMBER);
        int track = metadata.getInt(MediaMetadata.KEY_TRACK_NUMBER);
        String album = metadata.getString(MediaMetadata.KEY_ALBUM_TITLE);
        String artist = metadata.getString(MediaMetadata.KEY_ARTIST);

        if (CollectionUtils.isNotEmpty(metadata.getImages())) {
            WebImage webImage = metadata.getImages().get(0);
            metadataBuilder.setArtworkUri(webImage.getUrl());
        } else {
            metadataBuilder.setArtworkUri(MediaBrowserHelper.SONG_ICON_URI);
        }

        metadataBuilder.setDiscNumber(discNumber);
        metadataBuilder.setTrackNumber(track);

        if (StringUtils.isNotBlank(album)) {
            metadataBuilder.setAlbumTitle(album);
        }

        if (StringUtils.isNotBlank(artist)) {
            metadataBuilder.setArtist(artist);
        }

        if (mediaQueueItem.getPlaybackDuration() > 0) {
            extras.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, ((long) mediaQueueItem.getPlaybackDuration() * 1000L));
        }

        return new MediaItem.Builder()
                .setMimeType(contentType)
                .setMediaId(info.getContentId())
                .setMediaMetadata(metadataBuilder.build())
                .setUri(contentUrl).build();
    }
}
