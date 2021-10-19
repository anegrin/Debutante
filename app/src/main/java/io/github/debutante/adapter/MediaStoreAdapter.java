package io.github.debutante.adapter;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.debutante.helper.L;
import io.github.debutante.persistence.entities.AccountEntity;
import io.github.debutante.persistence.entities.AlbumEntity;
import io.github.debutante.persistence.entities.ArtistEntity;
import io.github.debutante.persistence.entities.SongEntity;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

public class MediaStoreAdapter {

    public static final String LOCAL = "l$";

    private final ContentResolver contentResolver;

    public MediaStoreAdapter(Context context) {
        contentResolver = context.getContentResolver();
    }

    private void log(String s) {
        L.v(MediaStoreAdapter.class.getSimpleName() + "." + s);
    }

    public Single<List<ArtistEntity>> findAllArtists() {
        log("findAllArtists");

        return Single.fromSupplier(() -> {
            try (Cursor cursor = contentResolver.query(
                    MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Artists._ID, MediaStore.Audio.Artists.ARTIST, MediaStore.Audio.Artists.NUMBER_OF_ALBUMS},
                    null,
                    null,
                    MediaStore.Audio.Artists.ARTIST)) {
                List<ArtistEntity> artistEntities = new ArrayList<>(cursor.getCount());
                while (cursor.moveToNext()) {
                    String _id = cursor.getString(0);
                    String name = cursor.getString(1);
                    int albumCount = cursor.getInt(2);

                    artistEntities.add(new ArtistEntity(local(_id), AccountEntity.LOCAL.uuid(), null, name, albumCount, null));

                }
                return artistEntities;
            }
        });
    }

    private String local(String id) {
        return LOCAL + id;
    }

    private static String id(String local) {
        return local != null ? local.substring(LOCAL.length()) : null;
    }

    public static boolean isLocal(String uuid) {
        return uuid != null && (uuid.startsWith(LOCAL) || uuid.equals(AccountEntity.LOCAL.uuid));
    }

    public Single<List<SongEntity>> findSongsByArtistUuid(String uuid) {
        log("findSongsByArtistUuid " + uuid);
        return Single.fromSupplier(() -> {
            Cursor cursor = null;
            try {
                String id = id(uuid);
                cursor = contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.TRACK,
                                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.DATA},
                        MediaStore.Audio.Media.ARTIST_ID + "=?",
                        new String[]{id},
                        MediaStore.Audio.Media.ALBUM + "," + MediaStore.Audio.Media.TRACK);
                List<SongEntity> songEntities = new ArrayList<>(cursor.getCount());
                while (cursor.moveToNext()) {
                    String _id = cursor.getString(0);
                    String title = cursor.getString(1);
                    int duration = cursor.getInt(2);
                    int track = cursor.getInt(3);
                    String album = cursor.getString(4);
                    String artist = cursor.getString(5);
                    int year = cursor.getInt(6);
                    String data = cursor.getString(7);

                    songEntities.add(new SongEntity(local(_id), AccountEntity.LOCAL.uuid(), data, null, uuid, title, duration / 1000, null, track, 1, album, artist, year));

                }
                return songEntities;
            } finally {
                if (cursor != null) cursor.close();
            }
        });
    }

    public Single<List<AlbumEntity>> findAlbumsByArtistUuid(String uuid) {
        log("findAlbumsByArtistUuid " + uuid);
        return Single.fromSupplier(() -> {
            Cursor artistCursor = null;
            Cursor albumsCursor = null;
            try {
                String id = id(uuid);
                artistCursor = contentResolver.query(
                        MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Audio.Artists._ID, MediaStore.Audio.Artists.ARTIST},
                        MediaStore.Audio.Artists._ID + "=?",
                        new String[]{id},
                        null);
                if (artistCursor.moveToNext()) {
                    String artist = artistCursor.getString(1);

                    albumsCursor = contentResolver.query(
                            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                            new String[]{MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.NUMBER_OF_SONGS,
                                    MediaStore.Audio.Albums.FIRST_YEAR, MediaStore.Audio.Albums.ALBUM_ART},
                            MediaStore.Audio.Albums.ARTIST + "=?",
                            new String[]{artist},
                            MediaStore.Audio.Artists.ARTIST);
                    List<AlbumEntity> albumEntities = new ArrayList<>(albumsCursor.getCount());
                    while (albumsCursor.moveToNext()) {
                        String _id = albumsCursor.getString(0);
                        String name = albumsCursor.getString(1);
                        int songsCount = albumsCursor.getInt(2);
                        int year = albumsCursor.getInt(3);
                        String albumArt = albumsCursor.getString(4);

                        albumEntities.add(new AlbumEntity(local(_id), AccountEntity.LOCAL.uuid(), null, id, name, songsCount, 0, albumArt, year, artist));

                    }
                    return albumEntities;
                } else {
                    return Collections.emptyList();
                }
            } finally {
                if (artistCursor != null) artistCursor.close();
                if (albumsCursor != null) albumsCursor.close();

            }
        });
    }

    public Single<List<SongEntity>> findSongsByAlbumUuid(String uuid) {
        log("findSongsByAlbumUuid " + uuid);
        return Single.fromSupplier(() -> {
            Cursor cursor = null;
            try {
                String id = id(uuid);
                cursor = contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.TRACK,
                                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.DATA},
                        MediaStore.Audio.Media.ALBUM_ID + "=?",
                        new String[]{id},
                        MediaStore.Audio.Media.TRACK);
                List<SongEntity> songEntities = new ArrayList<>(cursor.getCount());
                while (cursor.moveToNext()) {
                    String _id = cursor.getString(0);
                    String title = cursor.getString(1);
                    int duration = cursor.getInt(2);
                    int track = cursor.getInt(3);
                    String album = cursor.getString(4);
                    String artist = cursor.getString(5);
                    int year = cursor.getInt(6);
                    String data = cursor.getString(7);


                    songEntities.add(new SongEntity(local(_id), AccountEntity.LOCAL.uuid(), data, uuid, null, title, duration, null, track, 1, album, artist, year));

                }
                return songEntities;
            } finally {
                if (cursor != null) cursor.close();
            }
        });
    }

    public Maybe<ArtistEntity> findArtistByUuid(String uuid) {
        log("findArtistByUuid " + uuid);
        String id = id(uuid);
        return Maybe.fromSupplier(() -> {
            try (Cursor cursor = contentResolver.query(
                    MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Artists._ID, MediaStore.Audio.Artists.ARTIST, MediaStore.Audio.Artists.NUMBER_OF_ALBUMS},
                    MediaStore.Audio.Artists._ID + "=?",
                    new String[]{id},
                    null)) {
                if (cursor.moveToNext()) {
                    String name = cursor.getString(1);
                    int albumCount = cursor.getInt(2);

                    return new ArtistEntity(uuid, AccountEntity.LOCAL.uuid(), null, name, albumCount, null);

                } else {
                    return null;
                }
            }
        });
    }

    public Maybe<AlbumEntity> findAlbumByUuid(String uuid) {
        log("findAlbumByUuid " + uuid);
        String id = id(uuid);
        return Maybe.fromSupplier(() -> {
            try (Cursor cursor = contentResolver.query(
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.NUMBER_OF_SONGS,
                            MediaStore.Audio.Albums.FIRST_YEAR, MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.ALBUM_ART},
                    MediaStore.Audio.Albums._ID + "=?",
                    new String[]{id},
                    null)) {
                if (cursor.moveToNext()) {
                    String name = cursor.getString(1);
                    int songsCount = cursor.getInt(2);
                    int year = cursor.getInt(3);
                    String artist = cursor.getString(4);
                    String albumArt = cursor.getString(5);

                    return new AlbumEntity(uuid, AccountEntity.LOCAL.uuid(), null, id, name, songsCount, 0, albumArt, year, artist);
                } else {
                    return null;
                }
            }
        });
    }

    public Maybe<SongEntity> findSongByUuid(String uuid) {
        log("findSongByUuid " + uuid);
        String id = id(uuid);
        return Maybe.fromSupplier(() -> {
            try (Cursor cursor = contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.TRACK,
                            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.DATA},
                    MediaStore.Audio.Media._ID + "=?",
                    new String[]{id},
                    null)) {
                if (cursor.moveToNext()) {
                    String _id = cursor.getString(0);
                    String title = cursor.getString(1);
                    int duration = cursor.getInt(2);
                    int track = cursor.getInt(3);
                    String album = cursor.getString(4);
                    String artist = cursor.getString(5);
                    int year = cursor.getInt(6);
                    String data = cursor.getString(7);


                    return new SongEntity(local(_id), AccountEntity.LOCAL.uuid(), data, null, null, title, duration, null, track, 1, album, artist, year);
                } else {
                    return null;
                }
            }
        });
    }
}
