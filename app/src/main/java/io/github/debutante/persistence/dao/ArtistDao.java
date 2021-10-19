package io.github.debutante.persistence.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.github.debutante.persistence.entities.ArtistEntity;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

@Dao
public interface ArtistDao {
    @Query("SELECT * FROM artist order by name collate nocase asc")
    Single<List<ArtistEntity>> getAll();

    @Query("SELECT * FROM artist WHERE account_uuid = :accountUuid order by name collate nocase asc")
    Single<List<ArtistEntity>> findByAccountUuid(String accountUuid);

    @Query("SELECT * FROM artist WHERE uuid = :uuid LIMIT 1")
    Maybe<ArtistEntity> findByUuid(String uuid);

    @Query("SELECT * FROM artist WHERE remote_uuid = :remoteUuid LIMIT 1")
    Maybe<ArtistEntity> findByRemoteUuid(String remoteUuid);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(ArtistEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<ArtistEntity> entities);

    @Update
    Completable update(ArtistEntity entity);

    @Delete
    Completable delete(ArtistEntity entity);

    @Query("DELETE FROM artist WHERE account_uuid = :accountUuid")
    Completable deleteAll(String accountUuid);
}
