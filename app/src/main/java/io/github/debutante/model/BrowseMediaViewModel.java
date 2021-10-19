package io.github.debutante.model;

import android.support.v4.media.MediaBrowserCompat.MediaItem;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

import io.github.debutante.helper.L;

public class BrowseMediaViewModel extends ViewModel {
    private final MutableLiveData<Result> result = new MutableLiveData<>();

    public void put(String parentMediaId, List<MediaItem> mediaItems) {
        L.v("BrowseMediaViewModel.put parentMediaId=" + parentMediaId
                + ", mediaItems.size=" + CollectionUtils.size(mediaItems));
        result.postValue(new Result(parentMediaId, mediaItems));
    }

    public MutableLiveData<Result> get() {
        return result;
    }

    public static final class Result {
        public final String parentMediaId;
        public final List<MediaItem> mediaItems;

        private Result(String parentMediaId, List<MediaItem> mediaItems) {
            this.parentMediaId = parentMediaId;
            this.mediaItems = mediaItems;
        }
    }
}
