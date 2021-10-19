package io.github.debutante.helper;

import com.google.common.collect.ImmutableMap;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.debutante.BuildConfig;
import io.github.debutante.model.api.Album;
import io.github.debutante.model.api.Artist;
import io.github.debutante.model.api.ArtistInfo;
import io.github.debutante.model.api.Playlist;
import io.github.debutante.model.api.Song;
import io.github.debutante.persistence.entities.AccountEntity;
import io.github.debutante.persistence.entities.AlbumEntity;
import io.github.debutante.persistence.entities.ArtistEntity;
import io.github.debutante.persistence.entities.ArtistInfoNestedEntity;
import io.github.debutante.persistence.entities.BaseEntity;
import io.github.debutante.persistence.entities.SongEntity;

public class EntityHelper {

    public EntityHelper() {
    }

    private static String mediaId(BaseEntity entity, Map<String, Object> additionalParams) {
        if (entity != null) {
            String querySuffix = getQuerySuffix(additionalParams);
            return entity.getClass().getName() + "://" + entity.accountUuid() + "/" + entity.uuid()
                    + "?" + EntityMetadata.REMOTE_UUID_PARAM + "=" + URIHelper.urlEncode(entity.remoteUuid())
                    + "&" + EntityMetadata.PARENT_UUID_PARAM + "=" + URIHelper.urlEncode(entity.parentUuid())
                    + (StringUtils.isNotBlank(querySuffix) ? "&" + querySuffix : "");

        } else {
            return null;
        }
    }

    public static String mediaId(BaseEntity entity) {
        return mediaId(entity, Collections.emptyMap());
    }

    public static String mediaId(SongEntity entity) {
        return mediaId(entity, new ImmutableMap.Builder<String, Object>()
                .put(EntityMetadata.COVER_ART_PARAM, notNull(entity.coverArt))
                .put(EntityMetadata.DISC_NUMBER_PARAM, entity.discNumber)
                .put(EntityMetadata.DURATION_PARAM, entity.duration)
                .put(EntityMetadata.TRACK_PARAM, entity.track)
                .put(EntityMetadata.ALBUM_PARAM, notNull(entity.album))
                .put(EntityMetadata.ARTIST_PARAM, notNull(entity.artist))
                .put(EntityMetadata.YEAR_PARAM, entity.year)
                .build());

    }

    public static String mediaId(AlbumEntity entity) {
        return mediaId(entity, new ImmutableMap.Builder<String, Object>()
                .put(EntityMetadata.COVER_ART_PARAM, notNull(entity.coverArt))
                .put(EntityMetadata.DURATION_PARAM, entity.duration)
                .put(EntityMetadata.SONG_COUNT_PARAM, entity.songCount)
                .put(EntityMetadata.YEAR_PARAM, entity.year)
                .put(EntityMetadata.ARTIST_PARAM, notNull(entity.artist))
                .build());
    }

    public static String mediaId(ArtistEntity entity) {

        String coverArt = Optional.ofNullable(entity.artistInfo).map(i -> Optional.ofNullable(i.mediumImageUrl)
                .orElse(Optional.ofNullable(i.smallImageUrl)
                        .orElse(i.largeImageUrl)
                )
        ).orElse(entity.coverArt);

        return mediaId(entity, new ImmutableMap.Builder<String, Object>()
                .put(EntityMetadata.COVER_ART_PARAM, notNull(coverArt))
                .put(EntityMetadata.ALBUM_COUNT_PARAM, entity.albumCount)
                .build());
    }

    private static String notNull(String s) {
        return s != null ? s : "";
    }

    public static String mediaId(String mediaId, Map<String, Object> additionalParams) {
        if (mediaId != null) {
            String querySuffix = getQuerySuffix(additionalParams);
            return mediaId + (mediaId.contains("?") ? "&" : "?") + querySuffix;
        } else {
            return null;
        }
    }

    private static String getQuerySuffix(Map<String, Object> additionalParams) {
        return Optional.ofNullable(additionalParams).map(m -> m.entrySet().stream().map(e -> URIHelper.urlEncode(e.getKey()) + "=" + URIHelper.urlEncode(e.getValue())).collect(Collectors.joining("&"))).orElse("");
    }

    @SuppressWarnings("unchecked")
    public static EntityMetadata metadata(String mediaId) {
        if (mediaId != null) {

            try {
                URI uri = new URI(mediaId);
                return new EntityMetadata(uri.getHost(), uri.getPath() != null ? uri.getPath().substring(1) : null, (Class<? extends BaseEntity>) Class.forName(uri.getScheme()), uri.getRawQuery());
            } catch (ClassNotFoundException | URISyntaxException e) {
                return null;
            }

        } else {
            return null;
        }
    }

    public static ArtistEntity toEntity(AccountEntity accountEntity, Artist artist) {
        return new ArtistEntity(accountEntity.uuid, artist.id, artist.name, artist.albumCount, artist.coverArt);
    }

    public static AlbumEntity toEntity(AccountEntity accountEntity, ArtistEntity artistEntity, Playlist playlist) {
        return new AlbumEntity(accountEntity.uuid, playlist.id, artistEntity.uuid, playlist.name, playlist.songCount, playlist.duration, null, 0, artistEntity.name);
    }

    public static AlbumEntity toEntity(AccountEntity accountEntity, ArtistEntity artistEntity, Album album) {
        return new AlbumEntity(accountEntity.uuid, album.id, artistEntity.uuid, album.name, album.songCount, album.duration, album.coverArt, album.yearAsInt(), album.artist);
    }

    public static SongEntity toEntity(AccountEntity accountEntity, ArtistEntity artistEntity, AlbumEntity albumEntity, Song song) {
        return toEntity(accountEntity, artistEntity, albumEntity, song, song.trackAsInt());
    }

    public static SongEntity toEntity(AccountEntity accountEntity, ArtistEntity artistEntity, AlbumEntity albumEntity, Song song, int track) {
        return new SongEntity(accountEntity.uuid, song.id, albumEntity.uuid, artistEntity.uuid, song.title, song.duration, StringUtils.isNotBlank(albumEntity.coverArt) ? albumEntity.coverArt : song.coverArt, track, song.discNumberAsInt(), song.album, song.artist, song.yearAsInt());
    }

    public static ArtistInfoNestedEntity toEntity(ArtistInfo artistInfo) {
        return new ArtistInfoNestedEntity(artistInfo.biography, artistInfo.musicBrainzId, artistInfo.lastFmUrl, artistInfo.smallImageUrl, artistInfo.mediumImageUrl, artistInfo.largeImageUrl);
    }

    public static final class EntityMetadata {
        public static final String REMOTE_UUID_PARAM = BuildConfig.L_D ? "remoteUuid" : "rU";
        public static final String PARENT_UUID_PARAM = BuildConfig.L_D ? "parentUuid" : "pU";
        public static final String COVER_ART_PARAM = BuildConfig.L_D ? "coverArt" : "cA";
        public static final String DISC_NUMBER_PARAM = BuildConfig.L_D ? "discNumber" : "dN";
        public static final String DURATION_PARAM = BuildConfig.L_D ? "duration" : "d";
        public static final String TRACK_PARAM = BuildConfig.L_D ? "track" : "t";
        public static final String ALBUM_PARAM = BuildConfig.L_D ? "album" : "al";
        public static final String ARTIST_PARAM = BuildConfig.L_D ? "artist" : "ar";
        public static final String YEAR_PARAM = BuildConfig.L_D ? "year" : "y";
        public static final String SONG_COUNT_PARAM = BuildConfig.L_D ? "songCount" : "sC";
        public static final String ALBUM_COUNT_PARAM = BuildConfig.L_D ? "albumCount" : "aC";


        public final String accountUuid;
        public final String uuid;
        public final Class<? extends BaseEntity> type;
        public final Map<String, String> params;

        public EntityMetadata(String accountUuid, String uuid, Class<? extends BaseEntity> type, String rawQuery) {
            this.accountUuid = accountUuid;
            this.uuid = uuid;
            this.type = type;
            this.params = Optional.ofNullable(rawQuery).map(q -> Arrays.stream(q.split("&"))
                    .map(p -> p.split("="))
                    .filter(a -> a.length == 2)
                    .collect(Collectors.toMap(a -> URIHelper.urlDecode(a[0]), a -> URIHelper.urlDecode(a[1]), (v1, v2) -> v2))
            ).orElse(Collections.emptyMap());
        }
    }
}
