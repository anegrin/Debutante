package io.github.debutante.model;

import android.support.v4.media.MediaBrowserCompat.MediaItem;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Optional;

import io.github.debutante.helper.L;

public class PlayMediaViewModel extends ViewModel {
    private final MutableLiveData<Result> result = new MutableLiveData<>();

    public void put(MediaItem parentMediaItem, List<MediaItem> mediaItems, String mediaItemId) {
        L.v("PlayMediaViewModel.put parentMediaItem=" + Optional.ofNullable(parentMediaItem).map(MediaItem::getMediaId).orElse("")
                + ", mediaItems.size=" + CollectionUtils.size(mediaItems)
                + ", mediaItemId=" + mediaItemId);
        result.setValue(new Result(parentMediaItem, mediaItems, mediaItemId));
    }

    public MutableLiveData<Result> get() {
        return result;
    }

    public static final class Result {
        public final MediaItem parentMediaItem;
        public final List<MediaItem> mediaItems;
        public final String mediaItemId;

        private Result(MediaItem parentMediaItem, List<MediaItem> mediaItems, String mediaItemId) {
            this.parentMediaItem = parentMediaItem;
            this.mediaItems = mediaItems;
            this.mediaItemId = mediaItemId;
        }
    }
}
