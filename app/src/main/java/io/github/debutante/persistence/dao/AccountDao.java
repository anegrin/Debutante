package io.github.debutante.persistence.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.github.debutante.persistence.entities.AccountEntity;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

@Dao
public interface AccountDao {
    @Query("SELECT count(uuid) FROM account")
    Single<Integer> count();

    @Query("SELECT * FROM account order by token desc, alias collate nocase asc")
    Single<List<AccountEntity>> getAll();

    @Query("SELECT * FROM account WHERE uuid = :uuid LIMIT 1")
    Maybe<AccountEntity> findByUuid(String uuid);

    @Query("SELECT * FROM account WHERE alias = :alias LIMIT 1")
    Maybe<AccountEntity> findByAlias(String alias);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(AccountEntity entity);

    @Update
    Completable update(AccountEntity entity);

    @Delete
    Completable delete(AccountEntity entity);

    @Query("DELETE FROM account WHERE uuid = :uuid")
    Completable delete(String uuid);
}
