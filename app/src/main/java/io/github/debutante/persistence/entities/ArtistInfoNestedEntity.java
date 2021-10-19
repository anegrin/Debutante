package io.github.debutante.persistence.entities;

import androidx.room.ColumnInfo;

@SuppressWarnings("CanBeFinal")
public class ArtistInfoNestedEntity {
    @ColumnInfo(name = "biography")
    public String biography;
    @ColumnInfo(name = "music_brainz_id")
    public String musicBrainzId;
    @ColumnInfo(name = "last_fm_url")
    public String lastFmUrl;
    @ColumnInfo(name = "small_image_url")
    public String smallImageUrl;
    @ColumnInfo(name = "medium_image_url")
    public String mediumImageUrl;
    @ColumnInfo(name = "large_image_url")
    public String largeImageUrl;

    public ArtistInfoNestedEntity(String biography, String musicBrainzId, String lastFmUrl, String smallImageUrl, String mediumImageUrl, String largeImageUrl) {
        this.biography = biography;
        this.musicBrainzId = musicBrainzId;
        this.lastFmUrl = lastFmUrl;
        this.smallImageUrl = smallImageUrl;
        this.mediumImageUrl = mediumImageUrl;
        this.largeImageUrl = largeImageUrl;
    }

    @Override
    public String toString() {
        return "ArtistInfoNestedEntity{" +
                "biography='" + biography + '\'' +
                ", musicBrainzId='" + musicBrainzId + '\'' +
                ", lastFmUrl='" + lastFmUrl + '\'' +
                ", smallImageUrl='" + smallImageUrl + '\'' +
                ", mediumImageUrl='" + mediumImageUrl + '\'' +
                ", largeImageUrl='" + largeImageUrl + '\'' +
                '}';
    }
}
