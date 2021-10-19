package io.github.debutante.service;

import android.app.Notification;
import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.scheduler.PlatformScheduler;

import java.util.List;

import io.github.debutante.Debutante;
import io.github.debutante.R;
import io.github.debutante.helper.L;

public class MediaDownloadService extends DownloadService {

    private static final int JOB_ID = 1;
    private static final String DOWNLOAD_NOTIFICATION_CHANNEL_ID = Debutante.NOTIFICATION_CHANNEL_ID + "Download";

    public MediaDownloadService() {
        super(
                FOREGROUND_NOTIFICATION_ID_NONE,
                DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
                DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                R.string.app_name,
                /* channelDescriptionResourceId= */ 0);
    }

    public static void sendRemoveAllDownloads(Context context) {
        try {
            DownloadService.sendRemoveAllDownloads(context, MediaDownloadService.class, false);
        } catch (IllegalStateException ise) {
            L.e("Can't remove all downloads", ise);
        }
    }

    public static void sendPauseDownloads(Context context) {
        try {
            DownloadService.sendPauseDownloads(context, MediaDownloadService.class, false);
        } catch (IllegalStateException ise) {
            L.e("Can't pause downloads", ise);
        }
    }

    public static void sendResumeDownloads(Context context) {
        try {
            DownloadService.sendResumeDownloads(context, MediaDownloadService.class, false);
        } catch (IllegalStateException ise) {
            L.e("Can't resume downloads", ise);
        }
    }

    public static void sendAddDownload(Context context, DownloadRequest downloadRequest) {
        try {
            DownloadService.sendAddDownload(context, MediaDownloadService.class, downloadRequest, false);
        } catch (IllegalStateException ise) {
            L.e("Can't add download of " + downloadRequest.uri, ise);
        }
    }

    @Override
    @NonNull
    protected DownloadManager getDownloadManager() {
        return d().downloadManager();
    }

    @Override
    protected PlatformScheduler getScheduler() {
        return new PlatformScheduler(this, JOB_ID);
    }

    @Override
    @NonNull
    protected Notification getForegroundNotification(List<Download> downloads, int notMetRequirements) {
        throw new UnsupportedOperationException();
    }

    private Debutante d() {
        return (Debutante) getApplication();
    }
}
