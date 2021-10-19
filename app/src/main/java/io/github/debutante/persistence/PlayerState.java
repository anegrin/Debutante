package io.github.debutante.persistence;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.media.MediaBrowserCompat;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.debutante.helper.L;
import io.github.debutante.helper.Obj;

public class PlayerState {

    public static final String MEDIA_ITEMS_STORE = PlayerState.class.getSimpleName() + "-MEDIA_ITEMS_STORE";
    public static final String CURRENT_MEDIA_ITEM_ID_STORE = PlayerState.class.getSimpleName() + "-CURRENT_MEDIA_ITEM_ID_STORE";
    public static final String CURRENT_MEDIA_ITEM_POSITION_STORE = PlayerState.class.getSimpleName() + "-CURRENT_MEDIA_ITEM_POSITION_STORE";

    private PlayerState() {

    }

    private static File getFilesDir(Context context) {
        return Obj.tap(new File(context.getFilesDir(), "player_state"), d -> {
            try {
                if (!d.exists()) d.mkdirs();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void clear(Context context, String accountUuid) {
        clear(context, Optional.ofNullable(accountUuid));
        clear(context, Optional.empty());
    }

    private static void clear(Context context, Optional<String> accountUuid) {
        try {
            new File(getFilesDir(context), MEDIA_ITEMS_STORE + accountUuid.orElse("")).delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            new File(getFilesDir(context), CURRENT_MEDIA_ITEM_ID_STORE + accountUuid.orElse("")).delete();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void persistMediaItems(Context context, String accountUuid, MediaBrowserCompat.MediaItem parentMediaItem, List<MediaBrowserCompat.MediaItem> mediaItems) {
        persistMediaItems(context, Optional.ofNullable(accountUuid), parentMediaItem, mediaItems);
        persistMediaItems(context, Optional.empty(), parentMediaItem, mediaItems);
    }

    private static void persistMediaItems(Context context, Optional<String> accountUuid, MediaBrowserCompat.MediaItem parentMediaItem, List<MediaBrowserCompat.MediaItem> mediaItems) {

        L.i("Saving media items for accountId " + accountUuid.orElse("ANY"));

        File file = new File(getFilesDir(context), MEDIA_ITEMS_STORE + accountUuid.orElse(""));

        try (FileOutputStream fos = new FileOutputStream(file)) {
            Parcel p = Parcel.obtain();
            p.writeParcelable(parentMediaItem, 0);
            p.writeParcelableArray(mediaItems.toArray(new Parcelable[0]), 0);
            fos.write(p.marshall());
            fos.flush();
            p.recycle();

            L.i("Saved parent media item id is " + parentMediaItem.getMediaId());
            L.i("Saved media items count: " + CollectionUtils.size(mediaItems));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Optional<Pair<MediaBrowserCompat.MediaItem, List<MediaBrowserCompat.MediaItem>>> loadMediaItems(Context context, Optional<String> accountUuid) {
        L.i("Loading media items for accountId " + accountUuid.orElse("ANY"));

        File file = new File(getFilesDir(context), MEDIA_ITEMS_STORE + accountUuid.orElse(""));

        if (file.exists()) {
            L.d("File exists: " + file);

            try (FileInputStream fis = new FileInputStream(file);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                IOUtils.copy(fis, baos);
                baos.flush();
                byte[] bytes = baos.toByteArray();

                Parcel parcel = Parcel.obtain();
                parcel.unmarshall(bytes, 0, bytes.length);
                parcel.setDataPosition(0);

                ClassLoader classLoader = MediaBrowserCompat.MediaItem.class.getClassLoader();
                MediaBrowserCompat.MediaItem key = parcel.readParcelable(classLoader);
                List<MediaBrowserCompat.MediaItem> value = Arrays.stream(parcel.readParcelableArray(classLoader)).map(MediaBrowserCompat.MediaItem.class::cast).collect(Collectors.toList());
                parcel.recycle();

                L.i("Loaded parent media item id is " + key.getMediaId());
                L.i("Loaded media items count: " + CollectionUtils.size(value));

                return Optional.of(Pair.of(key, value));

            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        return Optional.empty();
    }

    public static void persistCurrentMediaItemId(Context context, String accountUuid, String id) {
        persistCurrentMediaItemId(context, Optional.ofNullable(accountUuid), id);
        persistCurrentMediaItemId(context, Optional.empty(), id);
    }

    private static void persistCurrentMediaItemId(Context context, Optional<String> accountUuid, String id) {
        L.i("Saving current media item id for accountId " + accountUuid.orElse("ANY"));

        File file = new File(getFilesDir(context), CURRENT_MEDIA_ITEM_ID_STORE + accountUuid.orElse(""));

        if (id != null) {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
                bw.write(id);
                bw.newLine();
                bw.flush();

                L.i("Saved media item id is " + id);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void persistCurrentMediaItemPosition(Context context, String id, long position) {
        L.i("Saving current media item position");

        File file = new File(getFilesDir(context), CURRENT_MEDIA_ITEM_POSITION_STORE);

        if (id != null) {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
                bw.write(id);
                bw.newLine();
                bw.write(String.valueOf(position));
                bw.newLine();
                bw.flush();

                L.i("Saved current media item id is " + id + ", position is " + position);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static Optional<String> loadCurrentMediaItemId(Context context, Optional<String> accountUuid) {
        L.i("Loading current media item id for accountId " + accountUuid.orElse("ANY"));

        File file = new File(getFilesDir(context), CURRENT_MEDIA_ITEM_ID_STORE + accountUuid.orElse(""));

        if (file.exists()) {
            L.d("File exists: " + file);

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                return Obj.tap(Optional.ofNullable(br.readLine()), o -> o.ifPresent(m -> L.i("Loaded media item id is " + m)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return Optional.empty();
    }

    public static Optional<Pair<String, Long>> loadCurrentMediaItemPosition(Context context) {
        L.i("Loading current media item position");

        File file = new File(getFilesDir(context), CURRENT_MEDIA_ITEM_POSITION_STORE);

        if (file.exists()) {
            L.d("File exists: " + file);

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                return Obj.tap(Optional.of(Pair.of(br.readLine(), Long.parseLong(br.readLine()))), o -> o.ifPresent(m -> L.i("Loaded media item id is " + m.getKey() + ", position is " + m.getValue())));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return Optional.empty();
    }
}
