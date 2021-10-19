package io.github.debutante.persistence.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "account")
public class AccountEntity implements BaseEntity {

    private static final String LOCAL_UUID = "local";
    public static final AccountEntity LOCAL = new AccountEntity(LOCAL_UUID, "Local", "local storage", "you", null);

    @PrimaryKey
    @NonNull
    public String uuid;

    @ColumnInfo(name = "alias")
    public String alias;
    @ColumnInfo(name = "url")
    public String url;
    @ColumnInfo(name = "username")
    public String username;
    @ColumnInfo(name = "token")
    public String token;

    public AccountEntity() {
    }

    @Ignore
    public AccountEntity(String alias, String url, String username, String token) {
        this(alias, alias, url, username, token);
    }

    @Ignore
    public AccountEntity(@NonNull String uuid, String alias, String url, String username, String token) {
        this.uuid = uuid;
        this.alias = alias;
        this.url = url;
        this.username = username;
        this.token = token;
    }

    @Override
    public String uuid() {
        return uuid;
    }

    @Override
    public String accountUuid() {
        return uuid;
    }

    @Override
    public String remoteUuid() {
        return null;
    }

    @Override
    public String parentUuid() {
        return null;
    }

    public boolean isCloud() {
        return !LOCAL_UUID.equals(uuid());
    }

    @Override
    public String toString() {
        return "AccountEntity{" +
                "uuid='" + uuid + '\'' +
                ", alias='" + alias + '\'' +
                ", url='" + url + '\'' +
                ", username='" + username + '\'' +
                ", token='***'" +
                '}';
    }
}
