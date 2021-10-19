package io.github.debutante.persistence.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "album", indices = {@Index("artist_uuid"), @Index("name")})
public class AlbumEntity implements BaseEntity {
    @PrimaryKey
    @NonNull
    public String uuid;

    @ColumnInfo(name = "account_uuid")
    public String accountUuid;
    @ColumnInfo(name = "remote_uuid")
    public String remoteUuid;
    //we don't want FK as we fetch data asynchronously
    @ColumnInfo(name = "artist_uuid")
    public String artistUuid;
    @ColumnInfo(name = "name")
    public String name;
    @ColumnInfo(name = "songCount")
    public int songCount;
    @ColumnInfo(name = "duration")
    public int duration;
    @ColumnInfo(name = "cover_art")
    public String coverArt;
    @ColumnInfo(name = "year")
    public int year;
    @ColumnInfo(name = "artist")
    public String artist;

    public AlbumEntity() {
    }

    @Ignore
    public AlbumEntity(String accountUuid, String remoteUuid, String artistUuid, String name, int songCount, int duration, String coverArt, int year, String artist) {
        this(accountUuid + "-" + remoteUuid, accountUuid, remoteUuid, artistUuid, name, songCount, duration, coverArt, year, artist);
    }

    @Ignore
    public AlbumEntity(@NonNull String uuid, String accountUuid, String remoteUuid, String artistUuid, String name, int songCount, int duration, String coverArt, int year, String artist) {
        this.uuid = uuid;
        this.accountUuid = accountUuid;
        this.remoteUuid = remoteUuid;
        this.artistUuid = artistUuid;
        this.name = name;
        this.songCount = songCount;
        this.duration = duration;
        this.coverArt = coverArt;
        this.year = year;
        this.artist = artist;
    }


    @Override
    public String uuid() {
        return uuid;
    }

    @Override
    public String accountUuid() {
        return accountUuid;
    }

    @Override
    public String remoteUuid() {
        return remoteUuid;
    }

    @Override
    public String parentUuid() {
        return artistUuid;
    }

    @Override
    public String toString() {
        return "AlbumEntity{" +
                "uuid='" + uuid + '\'' +
                ", accountUuid='" + accountUuid + '\'' +
                ", remoteUuid='" + remoteUuid + '\'' +
                ", artistUuid='" + artistUuid + '\'' +
                ", name='" + name + '\'' +
                ", songCount=" + songCount +
                ", duration=" + duration +
                ", coverArt='" + coverArt + '\'' +
                ", year=" + year +
                ", artist='" + artist + '\'' +
                '}';
    }
}
