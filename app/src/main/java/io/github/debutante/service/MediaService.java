package io.github.debutante.service;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github.debutante.Debutante;
import io.github.debutante.helper.DeviceHelper;
import io.github.debutante.helper.L;
import io.github.debutante.helper.MediaBrowserHelper;
import io.github.debutante.helper.Obj;

public class MediaService extends MediaBrowserServiceCompat {

    private static String sessionId = UUID.randomUUID().toString();

    public static void invalidateSession() {
        sessionId = Obj.tap(UUID.randomUUID().toString(), s -> L.d("Creating new session id: " + s));
    }

    public static String currentSessionId() {
        return sessionId;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        L.i("Creating media service");
        setSessionToken(d().mediaSession().getSessionToken());
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        String rootId = MediaBrowserHelper.ROOT_ID + "?_sid=" + sessionId;
        L.i("onGetRoot: " + rootId);
        return new BrowserRoot(rootId, null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        L.i("onLoadChildren: " + parentId);
        result.detach();
        MediaBrowserHelper.loadChildrenFromService(this, d().repository(), withSessionId(parentId), children -> doSendResults(children, result));
    }

    private void doSendResults(List<MediaBrowserCompat.MediaItem> children, Result<List<MediaBrowserCompat.MediaItem>> result) {
        L.d("doSendResults, items count: " + CollectionUtils.size(children));
        String permission = DeviceHelper.requireSpecificReadAudioPermissions() ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        int checkResult = checkSelfPermission(permission);
        if (checkResult != PackageManager.PERMISSION_GRANTED || !d().appConfig().isAccountsLocalEnabled()) {
            result.sendResult(children.stream().filter(MediaBrowserHelper::isNotLocalAccount).map(this::decorateTitle).collect(Collectors.toList()));
        } else {
            result.sendResult(children.stream().map(this::decorateTitle).collect(Collectors.toList()));
        }
    }

    private MediaBrowserCompat.MediaItem decorateTitle(MediaBrowserCompat.MediaItem mediaItem) {
        if (d().appConfig().isCarTextIconsEnabled()) {

            return new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                    .setMediaId(mediaItem.getDescription().getMediaId())
                    .setTitle(MediaBrowserHelper.getUTF8CharForIconUri(mediaItem.getDescription().getIconUri()) + " " + mediaItem.getDescription().getTitle())
                    .setDescription(mediaItem.getDescription().getDescription())
                    .build(), mediaItem.getFlags());
        } else {
            return mediaItem;
        }
    }

    @NonNull
    private String withSessionId(@NonNull String parentId) {
        if (!parentId.contains("_sid=")) {
            parentId += parentId.contains("?") ? "&_sid=" + sessionId : "?_sid=" + sessionId;
        }
        return parentId;
    }

    private Debutante d() {
        return (Debutante) getApplication();
    }
}
