package io.github.debutante.persistence.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;


@Entity(tableName = "song", indices = {@Index("album_uuid"), @Index("title")})
public class SongEntity implements BaseEntity {
    @PrimaryKey
    @NonNull
    public String uuid;

    @ColumnInfo(name = "account_uuid")
    public String accountUuid;
    @ColumnInfo(name = "remote_uuid")
    public String remoteUuid;
    //we don't want FK as we fetch data asynchronously
    @ColumnInfo(name = "album_uuid")
    public String albumUuid;
    @ColumnInfo(name = "artist_uuid")
    public String artistUuid;
    @ColumnInfo(name = "title")
    public String title;
    @ColumnInfo(name = "duration")
    public int duration;
    @ColumnInfo(name = "cover_art")
    public String coverArt;
    @ColumnInfo(name = "track")
    public int track;
    @ColumnInfo(name = "disc_number")
    public int discNumber;
    @ColumnInfo(name = "album")
    public String album;
    @ColumnInfo(name = "artist")
    public String artist;
    @ColumnInfo(name = "year")
    public int year;

    public SongEntity() {
    }

    @Ignore
    public SongEntity(String accountUuid, String remoteUuid, String albumUuid, String artistUuid, String title, int duration, String coverArt, int track, int discNumber, String album, String artist, int year) {
        this(accountUuid + "-" + remoteUuid, accountUuid, remoteUuid, albumUuid, artistUuid, title, duration, coverArt, track, discNumber, album, artist, year);
    }

    @Ignore
    public SongEntity(@NonNull String uuid, String accountUuid, String remoteUuid, String albumUuid, String artistUuid, String title, int duration, String coverArt, int track, int discNumber, String album, String artist, int year) {
        this.uuid = uuid;
        this.accountUuid = accountUuid;
        this.remoteUuid = remoteUuid;
        this.albumUuid = albumUuid;
        this.artistUuid = artistUuid;
        this.title = title;
        this.duration = duration;
        this.coverArt = coverArt;
        this.track = track;
        this.discNumber = discNumber;
        this.album = album;
        this.artist = artist;
        this.year = year;
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
        return albumUuid;
    }

    @Override
    public String toString() {
        return "SongEntity{" +
                "uuid='" + uuid + '\'' +
                ", accountUuid='" + accountUuid + '\'' +
                ", remoteUuid='" + remoteUuid + '\'' +
                ", albumUuid='" + albumUuid + '\'' +
                ", artistUuid='" + artistUuid + '\'' +
                ", title='" + title + '\'' +
                ", duration=" + duration +
                ", coverArt='" + coverArt + '\'' +
                ", track=" + track +
                ", discNumber=" + discNumber +
                ", album='" + album + '\'' +
                ", artist='" + artist + '\'' +
                ", year=" + year +
                '}';
    }
}
