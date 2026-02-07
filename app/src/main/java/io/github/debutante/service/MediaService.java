package io.github.debutante.service;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
import static io.github.debutante.service.BaseForegroundService.buildNotification;

import android.Manifest;
import android.app.Notification;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github.debutante.Debutante;
import io.github.debutante.R;
import io.github.debutante.helper.DeviceHelper;
import io.github.debutante.helper.L;
import io.github.debutante.helper.MediaBrowserHelper;
import io.github.debutante.helper.Obj;

public class MediaService extends MediaBrowserServiceCompat {
    private static final int NOTIFICATION_ID = Debutante.NOTIFICATION_ID + 4;

    private static String sessionId = UUID.randomUUID().toString();

    public static void invalidateSession() {
        sessionId = Obj.tap(UUID.randomUUID().toString(), s -> L.d("Creating new session id: " + s));
    }

    public static String currentSessionId() {
        return sessionId;
    }

    @Override
    public IBinder onBind(Intent intent) {
        L.i("Binding to media service, action: " + Optional.ofNullable(intent).map(Intent::getAction).orElse("<none>"));
        return super.onBind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        L.i("Starting media service, action: " + Optional.ofNullable(intent).map(Intent::getAction).orElse("<none>"));
        Notification notification = buildNotification(this, Optional.empty(), R.string.media_service_notification_content, false, null);
        if (DeviceHelper.needsForegroundServiceTypeOnStart()) {
            startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        L.i("Creating media service");
        MediaSessionCompat mediaSession = d().mediaSession();
        setSessionToken(mediaSession.getSessionToken());
        startForegroundService(new Intent(this, MediaService.class));
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        String rootId = MediaBrowserHelper.ROOT_ID + "?_sid=" + sessionId;
        L.i("onGetRoot: " + rootId + ", client: " + clientPackageName);

        Bundle extras = new Bundle();
        extras.putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true);
        extras.putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1);
        extras.putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1);

        return new BrowserRoot(rootId, extras);
    }

    @Override
    public void onLoadItem(String itemId, @NonNull Result<MediaBrowserCompat.MediaItem> result) {
        L.i("onLoadItem: " + itemId);
        MediaBrowserHelper.loadFromService(this, d().repository(), withSessionId(itemId), result::sendResult);
        result.detach();
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        L.i("onLoadChildren: " + parentId);
        MediaBrowserHelper.loadChildrenFromService(this, d().repository(), withSessionId(parentId), children -> doSendResults(children, result));
        result.detach();
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
