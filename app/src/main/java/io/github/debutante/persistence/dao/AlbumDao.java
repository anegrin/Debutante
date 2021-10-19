package io.github.debutante.persistence.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.github.debutante.persistence.entities.AlbumEntity;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

@Dao
public interface AlbumDao {
    @Query("SELECT * FROM album order by name collate nocase asc")
    Single<List<AlbumEntity>> getAll();

    @Query("SELECT * FROM album WHERE artist_uuid = :artistUuid order by year asc")
    Single<List<AlbumEntity>> findByArtistUuidOrderByYear(String artistUuid);

    @Query("SELECT * FROM album WHERE artist_uuid = :artistUuid order by name collate nocase asc")
    Single<List<AlbumEntity>> findByArtistUuidOrderByName(String artistUuid);

    @Query("SELECT * FROM album WHERE uuid = :uuid LIMIT 1")
    Maybe<AlbumEntity> findByUuid(String uuid);

    @Query("SELECT * FROM album WHERE remote_uuid = :remoteUuid LIMIT 1")
    Maybe<AlbumEntity> findByRemoteUuid(String remoteUuid);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(AlbumEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<AlbumEntity> entities);

    @Update
    Completable update(AlbumEntity entity);

    @Delete
    Completable delete(AlbumEntity entity);

    @Query("DELETE FROM album WHERE account_uuid = :accountUuid")
    Completable deleteAllByAccountUuid(String accountUuid);

    @Query("DELETE FROM album WHERE artist_uuid = :artistUuid")
    Completable deleteAllByArtistUuid(String artistUuid);
}
