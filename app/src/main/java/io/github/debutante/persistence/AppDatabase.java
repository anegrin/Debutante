package io.github.debutante.persistence;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import io.github.debutante.persistence.dao.AccountDao;
import io.github.debutante.persistence.dao.AlbumDao;
import io.github.debutante.persistence.dao.ArtistDao;
import io.github.debutante.persistence.dao.SongDao;
import io.github.debutante.persistence.entities.AccountEntity;
import io.github.debutante.persistence.entities.AlbumEntity;
import io.github.debutante.persistence.entities.ArtistEntity;
import io.github.debutante.persistence.entities.SongEntity;

@Database(entities = {AccountEntity.class, ArtistEntity.class, AlbumEntity.class, SongEntity.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract AccountDao accountDao();

    public abstract ArtistDao artistDao();

    public abstract AlbumDao albumDao();

    public abstract SongDao songDao();
}
