package io.github.debutante.persistence.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.github.debutante.persistence.entities.SongEntity;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

@Dao
public interface SongDao {
    @Query("SELECT * FROM song order by title collate nocase asc")
    Single<List<SongEntity>> getAll();

    @Query("SELECT s.* FROM song s, album a WHERE a.artist_uuid = :artistId AND s.album_uuid = a.uuid order by a.year asc,a.name collate nocase asc,s.disc_number asc,s.track asc")
    Single<List<SongEntity>> findByArtistUuidOrderByYear(String artistId);

    @Query("SELECT s.* FROM song s, album a WHERE a.artist_uuid = :artistId AND s.album_uuid = a.uuid order by a.name collate nocase asc,s.disc_number asc,s.track asc")
    Single<List<SongEntity>> findByArtistUuidOrderByName(String artistId);

    @Query("SELECT * FROM song WHERE album_uuid = :albumUuid order by disc_number asc,track asc")
    Single<List<SongEntity>> findByAlbumUuid(String albumUuid);

    @Query("SELECT * FROM song WHERE uuid = :uuid LIMIT 1")
    Maybe<SongEntity> findByUuid(String uuid);

    @Query("SELECT * FROM song WHERE remote_uuid = :remoteUuid LIMIT 1")
    Maybe<SongEntity> findByRemoteUuid(String remoteUuid);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(SongEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<SongEntity> entities);

    @Update
    Completable update(SongEntity entity);

    @Delete
    Completable delete(SongEntity entity);

    @Query("DELETE FROM song WHERE account_uuid = :accountUuid")
    Completable deleteAll(String accountUuid);

    @Query("DELETE FROM song WHERE artist_uuid = :artistUuid")
    Completable deleteAllByArtistUuid(String artistUuid);
}
