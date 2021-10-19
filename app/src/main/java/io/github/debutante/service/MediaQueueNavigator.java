package io.github.debutante.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator;

import java.io.File;
import java.io.FileInputStream;
import java.util.function.Function;

import io.github.debutante.helper.DeviceHelper;
import io.github.debutante.helper.L;
import io.github.debutante.helper.URIHelper;
import io.github.debutante.model.AppConfig;

public class MediaQueueNavigator extends TimelineQueueNavigator {
    private final AppConfig appConfig;
    private final BluetoothAdapter bluetoothAdapter;
    private final Function<String, File> cachedFileResolver;

    public MediaQueueNavigator(Context context, MediaSessionCompat mediaSession, AppConfig appConfig, Function<String, File> cachedFileResolver) {
        super(mediaSession, Integer.MAX_VALUE);
        this.appConfig = appConfig;
        this.cachedFileResolver = cachedFileResolver;

        boolean granted = (DeviceHelper.doNotRequireBTPermissions() || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED);
        if (granted) {
            bluetoothAdapter = DeviceHelper.doNotRequireBTPermissions() ? BluetoothAdapter.getDefaultAdapter() : ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        } else {
            bluetoothAdapter = null;
        }
    }

    @Override
    public MediaDescriptionCompat getMediaDescription(Player player, int windowIndex) {

        MediaItem mediaItem = player.getMediaItemAt(windowIndex);

        Bundle extras = new Bundle();
        if (mediaItem.mediaMetadata.trackNumber != null) {
            extras.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, mediaItem.mediaMetadata.trackNumber);
        }
        if (mediaItem.mediaMetadata.discNumber != null) {
            extras.putLong(MediaMetadataCompat.METADATA_KEY_DISC_NUMBER, mediaItem.mediaMetadata.discNumber);
        }

        if (mediaItem.mediaMetadata.extras != null) {
            if (mediaItem.mediaMetadata.extras.containsKey(MediaMetadataCompat.METADATA_KEY_DURATION)) {
                extras.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaItem.mediaMetadata.extras.getLong(MediaMetadataCompat.METADATA_KEY_DURATION));
            }
        }

        if (mediaItem.mediaMetadata.artworkUri != null) {
            String coverArtUrl = mediaItem.mediaMetadata.artworkUri.toString();
            extras.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, coverArtUrl);
            extras.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, coverArtUrl);
            if (appConfig.isArtOnBTEnabled() && URIHelper.isRemote(coverArtUrl) && isA2DPConnected()) {
                try {
                    File cached = cachedFileResolver.apply(coverArtUrl);
                    if (cached.exists()) {
                        Bitmap bitmap = BitmapFactory.decodeStream(new FileInputStream(cached));
                        if (bitmap != null) {
                            extras.putParcelable(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap);
                            extras.putParcelable(MediaMetadataCompat.METADATA_KEY_ART, bitmap);
                        }
                    }
                } catch (Exception e) {
                    L.v("Can't load album art", e);
                }
            }
        }


        if (mediaItem.mediaMetadata.albumTitle != null) {
            extras.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, mediaItem.mediaMetadata.albumTitle.toString());
        }

        if (mediaItem.mediaMetadata.artist != null) {
            extras.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, mediaItem.mediaMetadata.artist.toString());
        }

        if (mediaItem.mediaMetadata.albumArtist != null) {
            extras.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, mediaItem.mediaMetadata.albumArtist.toString());
        }

        if (mediaItem.mediaMetadata.title != null) {
            extras.putString(MediaMetadataCompat.METADATA_KEY_TITLE, mediaItem.mediaMetadata.title.toString());
            extras.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, mediaItem.mediaMetadata.title.toString());
        }

        return new MediaDescriptionCompat.Builder()
                .setMediaId(mediaItem.mediaId)
                .setDescription(mediaItem.mediaMetadata.description)
                .setTitle(mediaItem.mediaMetadata.title)
                .setIconUri(mediaItem.mediaMetadata.artworkUri)
                .setExtras(extras)
                .build();
    }

    @SuppressLint("MissingPermission")//checked on constructor
    public boolean isA2DPConnected() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled()
                && bluetoothAdapter.getProfileConnectionState(BluetoothHeadset.A2DP) == BluetoothHeadset.STATE_CONNECTED;
    }
}
