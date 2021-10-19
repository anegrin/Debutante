package io.github.debutante.helper;

import android.content.Context;
import android.net.Uri;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.common.collect.ImmutableMap;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.github.debutante.BuildConfig;
import io.github.debutante.R;
import io.github.debutante.persistence.EntityRepository;
import io.github.debutante.persistence.entities.AccountEntity;
import io.github.debutante.persistence.entities.AlbumEntity;
import io.github.debutante.persistence.entities.ArtistEntity;
import io.github.debutante.persistence.entities.BaseEntity;
import io.github.debutante.persistence.entities.SongEntity;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MediaBrowserHelper {
    private static final String RES_PREFIX = "android.resource://" + BuildConfig.APPLICATION_ID + "/drawable/";

    public static final String ROOT_ID = MediaBrowserHelper.class.getSimpleName() + "-ROOT_ID";
    public static final String PREVIOUS_SESSION_ID = MediaBrowserHelper.class.getSimpleName() + "-PREVIOUS_SESSION_ID";
    public static final String RECURSIVE_CHILDREN_LOADING = "_rcl";
    public static final String PREPEND_ACTIONS = "_pa";
    public static final Uri SONG_ICON_URI = Uri.parse(RES_PREFIX + "ic_song");
    private static final Uri PLAY_ICON_URI = Uri.parse(RES_PREFIX + "ic_play_circle");
    private static final Uri ACCOUNT_ICON_URI = Uri.parse(RES_PREFIX + "ic_account");
    private static final Uri LOCAL_ICON_URI = Uri.parse(RES_PREFIX + "ic_local");
    private static final Uri ARTIST_ICON_URI = Uri.parse(RES_PREFIX + "ic_artist");
    private static final Uri ALBUM_ICON_URI = Uri.parse(RES_PREFIX + "ic_album");
    private static final String LOCAL_ACCOUNT_MEDIA_ID = EntityHelper.mediaId(AccountEntity.LOCAL);
    private static final String SONG_ICON_CHAR = "\uD83C\uDFB5";
    private static final Map<Uri, String> ICON_TO_CHAR = new ImmutableMap.Builder<Uri, String>()
            .put(PLAY_ICON_URI, "\u25B6")
            .put(ACCOUNT_ICON_URI, "\u2601")
            .put(LOCAL_ICON_URI, "\uD83D\uDCF1")
            .put(ARTIST_ICON_URI, "\uD83C\uDFB6")
            .put(ALBUM_ICON_URI, "\uD83D\uDCBF")
            .put(SONG_ICON_URI, SONG_ICON_CHAR)
            .build();

    private MediaBrowserHelper() {
    }

    private static void log(String s) {
        L.v(MediaBrowserHelper.class.getSimpleName() + "." + s);
    }


    public static void load(final Context context, EntityRepository repository, @NonNull String id, @NonNull Consumer<MediaBrowserCompat.MediaItem> result) {
        log("load, id=" + id);
        if (!ROOT_ID.equals(id)) {
            EntityHelper.EntityMetadata metadata = EntityHelper.metadata(id);

            if (metadata.type == AccountEntity.class) {
                RxHelper.defaultInstance().subscribe(repository.findAccountByUuid(metadata.uuid), a -> result.accept(toMediaItem(a)), t -> toastLoadFailure(context, t));
            } else if (metadata.type == ArtistEntity.class) {
                RxHelper.defaultInstance().subscribe(repository.findArtistByUuid(metadata.uuid), a -> result.accept(toMediaItem(a)), t -> toastLoadFailure(context, t));
            } else if (metadata.type == AlbumEntity.class) {
                RxHelper.defaultInstance().subscribe(repository.findAlbumByUuid(metadata.uuid), a -> result.accept(toMediaItem(context, a)), t -> toastLoadFailure(context, t));
            } else if (metadata.type == SongEntity.class) {
                RxHelper.defaultInstance().subscribe(repository.findSongByUuid(metadata.uuid), s -> result.accept(toMediaItem(context, s)), t -> toastLoadFailure(context, t));
            }
        }
    }

    public static void loadChildrenFromService(Context context, EntityRepository repository, @NonNull String parentId, @NonNull Consumer<List<MediaBrowserCompat.MediaItem>> result) {
        log("loadChildrenFromService, parentId=" + parentId);
        Scheduler scheduler = Schedulers.newThread();
        loadChildren(context, repository, parentId, result, scheduler, scheduler);
    }

    public static void loadChildren(Context context, EntityRepository repository, @NonNull String parentId, @NonNull Consumer<List<MediaBrowserCompat.MediaItem>> result) {
        loadChildren(context, repository, parentId, result, null, null);
    }

    private static void loadChildren(final Context context,
                                     EntityRepository repository,
                                     @NonNull String parentId,
                                     @NonNull Consumer<List<MediaBrowserCompat.MediaItem>> result,
                                     Scheduler subscribeOn,
                                     Scheduler observeOn) {

        log("loadChildren, parentId=" + parentId);
        if (parentId.startsWith(ROOT_ID)) {
            sendResultAsync(context, result, repository::getAllAccounts, MediaBrowserHelper::toMediaItem, null, subscribeOn, observeOn);
        } else {
            EntityHelper.EntityMetadata parentMetadata = EntityHelper.metadata(parentId);

            boolean prependActions = !Boolean.FALSE.toString().equals(parentMetadata.params.get(PREPEND_ACTIONS));
            if (parentMetadata.type == AccountEntity.class) {
                String parentIdForAction = prependActions && !AccountEntity.LOCAL.uuid().equals(parentMetadata.uuid) ? PREVIOUS_SESSION_ID + parentMetadata.uuid : null;
                sendResultAsync(context, result, () -> repository.findArtistsByAccountUuid(parentMetadata.uuid), MediaBrowserHelper::toMediaItem, parentIdForAction, subscribeOn, observeOn);
            } else {
                String parentIdForAction = prependActions ? parentId : null;
                if (parentMetadata.type == ArtistEntity.class) {
                    boolean loadSongs = Boolean.TRUE.toString().equals(parentMetadata.params.get(RECURSIVE_CHILDREN_LOADING));
                    if (loadSongs) {
                        sendResultAsync(context, result, () -> repository.findSongsByArtistUuidOrderByYear(parentMetadata.uuid), songEntity -> toMediaItem(context, songEntity), null, subscribeOn, observeOn);
                    } else {
                        sendResultAsync(context, result, () -> repository.findAlbumsByArtistUuidOrderByYear(parentMetadata.uuid), album -> toMediaItem(context, album), parentIdForAction, subscribeOn, observeOn);
                    }
                } else if (parentMetadata.type == AlbumEntity.class) {
                    sendResultAsync(context, result, () -> repository.findSongsByAlbumUuid(parentMetadata.uuid), songEntity -> toMediaItem(context, songEntity), parentIdForAction, subscribeOn, observeOn);
                }
            }
        }
    }

    private static <T extends BaseEntity> void sendResultAsync(Context context,
                                                               Consumer<List<MediaBrowserCompat.MediaItem>> result,
                                                               Supplier<Single<List<T>>> loader,
                                                               Function<T, MediaBrowserCompat.MediaItem> converter,
                                                               String parentIdForActions,
                                                               Scheduler subscribeOn,
                                                               Scheduler observeOn) {
        log("sendResultAsync, parentIdForActions=" + parentIdForActions);

        RxHelper rxHelper = subscribeOn != null && observeOn != null ? RxHelper.newInstance(subscribeOn, observeOn) : RxHelper.defaultInstance();
        rxHelper.subscribe(loader.get(), l -> {

            if (CollectionUtils.isNotEmpty(l)) {

                boolean prependActions = StringUtils.isNotEmpty(parentIdForActions);
                int listSize = prependActions ? l.size() + 1 : l.size();
                List<MediaBrowserCompat.MediaItem> items = new ArrayList<>(listSize);

                if (prependActions) {
                    if (parentIdForActions.startsWith(PREVIOUS_SESSION_ID)) {
                        items.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                                .setMediaId(parentIdForActions)
                                .setTitle(context.getString(R.string.play_previous))
                                .setIconUri(PLAY_ICON_URI)
                                .build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
                    } else {
                        items.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                                .setMediaId(EntityHelper.mediaId(parentIdForActions, Collections.singletonMap(MediaBrowserHelper.RECURSIVE_CHILDREN_LOADING, true)))
                                .setTitle(context.getString(R.string.play_all))
                                .setIconUri(PLAY_ICON_URI)
                                .build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
                    }
                }

                l.stream().map(converter).forEach(items::add);
                result.accept(items);
            } else {
                result.accept(Collections.emptyList());

            }
        }, t -> toastLoadFailure(context, t));
    }

    private static MediaBrowserCompat.MediaItem toMediaItem(AccountEntity accountEntity) {
        String mediaId = EntityHelper.mediaId(accountEntity);
        return new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                .setMediaId(mediaId)
                .setTitle(accountEntity.alias)
                .setIconUri(LOCAL_ACCOUNT_MEDIA_ID.equals(mediaId) ? LOCAL_ICON_URI : ACCOUNT_ICON_URI)
                .setDescription(accountEntity.username + " @ " + accountEntity.url)
                .build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    private static MediaBrowserCompat.MediaItem toMediaItem(ArtistEntity artistEntity) {
        return new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                .setMediaId(EntityHelper.mediaId(artistEntity))
                .setTitle(artistEntity.name)
                .setIconUri(ARTIST_ICON_URI)
                .build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    private static MediaBrowserCompat.MediaItem toMediaItem(Context context, AlbumEntity album) {
        return new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                .setMediaId(EntityHelper.mediaId(album))
                .setTitle(album.year > 0 ? String.format(context.getString(R.string.name_year_pattern), album.name, album.year) : album.name)
                .setIconUri(ALBUM_ICON_URI)
                .build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    private static MediaBrowserCompat.MediaItem toMediaItem(Context context, SongEntity songEntity) {
        return new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                .setMediaId(EntityHelper.mediaId(songEntity))
                .setTitle(songEntity.title)
                .setDescription(String.format(context.getString(R.string.name_duration_pattern), songEntity.album, DurationFormatUtils.formatDuration(songEntity.duration * 1000L, "mm:ss")))
                .setIconUri(SONG_ICON_URI)
                .build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
    }

    private static void toastLoadFailure(Context context, Throwable t) {
        Toast.makeText(context, context.getString(R.string.load_entites_failure) + "\n" + t.getMessage(), Toast.LENGTH_SHORT).show();
    }

    public static boolean isNotLocalAccount(MediaBrowserCompat.MediaItem mediaItem) {
        return !LOCAL_ACCOUNT_MEDIA_ID.equals(mediaItem.getMediaId());
    }

    public static String getUTF8CharForIconUri(Uri uri) {
        return uri != null ? ICON_TO_CHAR.getOrDefault(uri, SONG_ICON_CHAR) : SONG_ICON_CHAR;
    }
}
