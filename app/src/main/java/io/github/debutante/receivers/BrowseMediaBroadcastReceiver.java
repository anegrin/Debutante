package io.github.debutante.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.common.collect.ImmutableMap;

import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.Map;

import io.github.debutante.AccountsFragment;
import io.github.debutante.AlbumsFragment;
import io.github.debutante.ArtistsFragment;
import io.github.debutante.SongsFragment;
import io.github.debutante.helper.EntityHelper;
import io.github.debutante.helper.L;
import io.github.debutante.helper.MediaBrowserHelper;
import io.github.debutante.helper.Obj;
import io.github.debutante.model.BrowseMediaViewModel;
import io.github.debutante.persistence.EntityRepository;
import io.github.debutante.persistence.entities.AccountEntity;
import io.github.debutante.persistence.entities.AlbumEntity;
import io.github.debutante.persistence.entities.ArtistEntity;
import io.github.debutante.persistence.entities.BaseEntity;

public class BrowseMediaBroadcastReceiver extends BroadcastReceiver {
    public static final String ACTION = BrowseMediaBroadcastReceiver.class.getSimpleName() + "-ACTION";
    public static final String MEDIA_ID_KEY = BrowseMediaBroadcastReceiver.class.getSimpleName() + "-MEDIA_ID_KEY";
    public static final String MEDIA_TITLE_KEY = BrowseMediaBroadcastReceiver.class.getSimpleName() + "-MEDIA_TITLE_KEY";
    private static final Map<Class<? extends BaseEntity>, String> PARENT_TYPE_TO_KEYS = new ImmutableMap.Builder<Class<? extends BaseEntity>, String>()
            .put(AccountEntity.class, ArtistsFragment.ARTISTS_KEY)
            .put(ArtistEntity.class, AlbumsFragment.ALBUMS_KEY)
            .put(AlbumEntity.class, SongsFragment.SONGS_KEY)
            .build();
    private final EntityRepository repository;
    private final ViewModelProvider viewModelProvider;

    public BrowseMediaBroadcastReceiver(ViewModelProvider viewModelProvider, EntityRepository repository) {
        this.viewModelProvider = viewModelProvider;
        this.repository = repository;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        L.i("Receiving " + intent.getAction() + " " + L.toString(intent.getExtras()));

        String mediaId = intent.getStringExtra(MEDIA_ID_KEY);

        String parentId = StringUtils.isNotEmpty(mediaId) ? mediaId : MediaBrowserHelper.ROOT_ID;

        MediaBrowserHelper.loadChildren(context, repository, parentId, children -> {
            final BrowseMediaViewModel browseMediaViewModel;
            if (MediaBrowserHelper.ROOT_ID.equals(parentId)) {
                browseMediaViewModel = viewModelProvider.get(AccountsFragment.ACCOUNTS_KEY, BrowseMediaViewModel.class);
            } else {
                browseMediaViewModel = viewModelProvider.get(getViewModelKey(EntityHelper.metadata(parentId).type), BrowseMediaViewModel.class);
            }
            browseMediaViewModel.put(parentId, children != null ? children : Collections.emptyList());
        });
    }

    @Nullable
    public static String getViewModelKey(@NonNull Class<? extends BaseEntity> type) {
        return PARENT_TYPE_TO_KEYS.get(type);
    }

    private static void logBroadcast(Intent intent) {
        L.i("Broadcasting " + ACTION + " " + L.toString(intent.getExtras()));
    }

    public static void broadcast(Context context, String mediaId) {
        Intent intent = new Intent(BrowseMediaBroadcastReceiver.ACTION);
        intent.putExtra(MEDIA_ID_KEY, mediaId);
        context.sendBroadcast(Obj.tap(intent, BrowseMediaBroadcastReceiver::logBroadcast));
    }
}
