package io.github.debutante.persistence.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "artist", indices = @Index("name"))
public class ArtistEntity implements BaseEntity {
    public static final String PLAYLISTS_REMOTE_UUID = "playlists";
    @PrimaryKey
    @NonNull
    public String uuid;

    @ColumnInfo(name = "account_uuid")
    public String accountUuid;
    @ColumnInfo(name = "remote_uuid")
    public String remoteUuid;
    @ColumnInfo(name = "name")
    public String name;
    @ColumnInfo(name = "albumCount")
    public int albumCount;
    @ColumnInfo(name = "cover_art")
    public String coverArt;
    @Embedded
    public ArtistInfoNestedEntity artistInfo;

    public ArtistEntity() {
    }

    @Ignore
    public ArtistEntity(String accountUuid, String remoteUuid, String name, int albumCount, String coverArt) {
        this(accountUuid + "-" + remoteUuid, accountUuid, remoteUuid, name, albumCount, coverArt);
    }

    @Ignore
    public ArtistEntity(@NonNull String uuid, String accountUuid, String remoteUuid, String name, int albumCount, String coverArt) {
        this.uuid = uuid;
        this.accountUuid = accountUuid;
        this.remoteUuid = remoteUuid;
        this.name = name;
        this.albumCount = albumCount;
        this.coverArt = coverArt;
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
        return accountUuid;
    }

    @Override
    public String toString() {
        return "ArtistEntity{" +
                "uuid='" + uuid + '\'' +
                ", accountUuid='" + accountUuid + '\'' +
                ", remoteUuid='" + remoteUuid + '\'' +
                ", name='" + name + '\'' +
                ", albumCount=" + albumCount +
                ", coverArt='" + coverArt + '\'' +
                ", artistInfo=" + artistInfo +
                '}';
    }
}
