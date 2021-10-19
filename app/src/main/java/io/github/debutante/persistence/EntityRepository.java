package io.github.debutante.persistence;

import android.content.Context;
import android.util.LruCache;

import androidx.room.Room;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import io.github.debutante.adapter.MediaStoreAdapter;
import io.github.debutante.helper.L;
import io.github.debutante.model.AppConfig;
import io.github.debutante.persistence.dao.AccountDao;
import io.github.debutante.persistence.dao.AlbumDao;
import io.github.debutante.persistence.dao.ArtistDao;
import io.github.debutante.persistence.dao.SongDao;
import io.github.debutante.persistence.entities.AccountEntity;
import io.github.debutante.persistence.entities.AlbumEntity;
import io.github.debutante.persistence.entities.ArtistEntity;
import io.github.debutante.persistence.entities.SongEntity;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

public class EntityRepository {
    private final LruCache<String, AccountEntity> accountsCache = new LruCache<>(2);
    private final LruCache<String, ArtistEntity> artistsCache = new LruCache<>(16);
    private final LruCache<String, AlbumEntity> albumsCache = new LruCache<>(16);
    private final LruCache<String, SongEntity> songsCache = new LruCache<>(16);

    private final AppDatabase db;
    private final AppConfig appConfig;
    private final AccountDao accountDao;
    private final ArtistDao artistDao;
    private final AlbumDao albumDao;
    private final SongDao songDao;
    private final MediaStoreAdapter mediaStoreAdapter;

    public EntityRepository(Context context, AppConfig appConfig) {
        db = Room.databaseBuilder(context, AppDatabase.class, "debutante").build();
        this.appConfig = appConfig;
        accountDao = db.accountDao();
        artistDao = db.artistDao();
        albumDao = db.albumDao();
        songDao = db.songDao();
        mediaStoreAdapter = new MediaStoreAdapter(context);
    }

    private void log(String s) {
        L.v(EntityRepository.class.getSimpleName() + "." + s);
    }

    public void close() {
        log("close");
        try {
            if (db.isOpen()) {
                db.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Single<Boolean> hasAccounts() {
        log("hasAccounts");
        return accountDao.count().map(i -> i > 0);
    }

    public Completable insertAccount(AccountEntity accountEntity) {
        log("insertAccount " + accountEntity);
        accountsCache.evictAll();
        return accountDao.insert(accountEntity);
    }

    public Completable updateAccount(AccountEntity accountEntity) {
        log("updateAccount " + accountEntity);
        accountsCache.evictAll();
        return accountDao.update(accountEntity);
    }

    public Completable updateArtist(ArtistEntity artistEntity) {
        log("updateArtist " + artistEntity);
        artistsCache.evictAll();
        return artistDao.update(artistEntity);
    }

    public Single<List<AccountEntity>> getAllAccounts() {
        log("getAllAccounts");
        return accountDao.getAll();
    }

    public Single<List<ArtistEntity>> findArtistsByAccountUuid(String uuid) {
        log("findArtistsByAccountUuid " + uuid);

        if (MediaStoreAdapter.isLocal(uuid)) {
            return mediaStoreAdapter.findAllArtists();
        } else {
            Single<List<ArtistEntity>> artists = artistDao.findByAccountUuid(uuid);
            if (appConfig.isPlaylistsEnabled()) {
                return artists.map(LinkedList::new)
                        .map(l -> {

                            ArtistEntity playlists = null;
                            Iterator<ArtistEntity> iterator = l.iterator();
                            while (playlists == null && iterator.hasNext()) {
                                ArtistEntity next = iterator.next();
                                if (ArtistEntity.PLAYLISTS_REMOTE_UUID.equals(next.remoteUuid)) {
                                    playlists = next;
                                    iterator.remove();
                                }
                            }

                            if (playlists != null) {
                                l.add(0, playlists);
                            }

                            return l;
                        });
            } else {
                return artists.map(l -> l.stream().filter(a -> !ArtistEntity.PLAYLISTS_REMOTE_UUID.equals(a.remoteUuid)).collect(Collectors.toList()));
            }
        }

    }

    public Single<List<SongEntity>> findSongsByArtistUuidOrderByYear(String uuid) {
        log("findSongsByArtistUuidOrderByYear " + uuid);

        if (MediaStoreAdapter.isLocal(uuid)) {
            return mediaStoreAdapter.findSongsByArtistUuid(uuid);
        } else {
            return songDao.findByArtistUuidOrderByYear(uuid);
        }
    }

    public Single<List<AlbumEntity>> findAlbumsByArtistUuidOrderByYear(String uuid) {
        log("findAlbumsByArtistUuidOrderByYear " + uuid);

        if (MediaStoreAdapter.isLocal(uuid)) {
            return mediaStoreAdapter.findAlbumsByArtistUuid(uuid);
        } else {
            return albumDao.findByArtistUuidOrderByYear(uuid);
        }
    }

    public Single<List<SongEntity>> findSongsByAlbumUuid(String uuid) {
        log("findSongsByAlbumUuid " + uuid);

        if (MediaStoreAdapter.isLocal(uuid)) {
            return mediaStoreAdapter.findSongsByAlbumUuid(uuid);
        } else {
            return songDao.findByAlbumUuid(uuid);
        }
    }

    public Maybe<AccountEntity> findAccountByUuid(String uuid) {
        log("findAccountByUuid " + uuid);

        AccountEntity cached = accountsCache.get(uuid);
        if (cached != null) {
            return Maybe.fromSupplier(() -> cached);
        }
        if (MediaStoreAdapter.isLocal(uuid)) {
            return Maybe.fromSupplier(() -> AccountEntity.LOCAL);
        } else {
            return accountDao.findByUuid(uuid).doOnSuccess(e -> accountsCache.put(uuid, e));
        }
    }

    public Maybe<ArtistEntity> findArtistByUuid(String uuid) {
        log("findArtistByUuid " + uuid);

        ArtistEntity cached = artistsCache.get(uuid);
        if (cached != null) {
            return Maybe.fromSupplier(() -> cached);
        }
        if (MediaStoreAdapter.isLocal(uuid)) {
            return mediaStoreAdapter.findArtistByUuid(uuid);
        } else {
            return artistDao.findByUuid(uuid).doOnSuccess(e -> artistsCache.put(uuid, e));
        }
    }

    public Maybe<AlbumEntity> findAlbumByUuid(String uuid) {
        log("findAlbumByUuid " + uuid);

        AlbumEntity cached = albumsCache.get(uuid);
        if (cached != null) {
            return Maybe.fromSupplier(() -> cached);
        }
        if (MediaStoreAdapter.isLocal(uuid)) {
            return mediaStoreAdapter.findAlbumByUuid(uuid);
        } else {
            return albumDao.findByUuid(uuid).doOnSuccess(e -> albumsCache.put(uuid, e));
        }
    }

    public Maybe<SongEntity> findSongByUuid(String uuid) {
        log("findSongByUuid " + uuid);

        SongEntity cached = songsCache.get(uuid);
        if (cached != null) {
            return Maybe.fromSupplier(() -> cached);
        }
        if (MediaStoreAdapter.isLocal(uuid)) {
            return mediaStoreAdapter.findSongByUuid(uuid);
        } else {
            return songDao.findByUuid(uuid).doOnSuccess(e -> songsCache.put(uuid, e));
        }
    }

    public Completable deleteAccountByUuid(String uuid) {
        log("deleteAccountByUuid " + uuid);

        accountsCache.evictAll();
        return accountDao.delete(uuid);
    }

    public Completable deleteAllSongsByAccountUuid(String accountUuid) {
        log("deleteAllSongsByAccountUuid " + accountUuid);

        songsCache.evictAll();
        return songDao.deleteAll(accountUuid);
    }

    public Completable deleteAllSongsByArtistUuid(String artistUuid) {
        log("deleteAllSongsByArtistUuid " + artistUuid);

        songsCache.evictAll();
        return songDao.deleteAllByArtistUuid(artistUuid);
    }

    public Completable deleteAllAlbumsByAccountUuid(String accountUuid) {
        log("deleteAllAlbumsByAccountUuid " + accountUuid);

        albumsCache.evictAll();
        return albumDao.deleteAllByAccountUuid(accountUuid);
    }

    public Completable deleteAllAlbumsByArtistUuid(String artistUuid) {
        log("deleteAllAlbumsByArtistUuid " + artistUuid);

        albumsCache.evictAll();
        return albumDao.deleteAllByArtistUuid(artistUuid);
    }

    public Completable deleteAllArtistsByAccountUuid(String accountUuid) {
        log("deleteAllArtistsByAccountUuid " + accountUuid);

        artistsCache.evictAll();
        return artistDao.deleteAll(accountUuid);
    }

    public Completable insertAllArtists(List<ArtistEntity> entities) {
        log("deleteAllArtistsByAccountUuid");

        artistsCache.evictAll();
        return artistDao.insertAll(entities);
    }

    public Completable insertAllAlbums(List<AlbumEntity> entities) {
        log("insertAllAlbums");

        albumsCache.evictAll();
        return albumDao.insertAll(entities);
    }

    public Completable insertAllSongs(List<SongEntity> entities) {
        log("insertAllSongs");

        songsCache.evictAll();
        return songDao.insertAll(entities);
    }
}
