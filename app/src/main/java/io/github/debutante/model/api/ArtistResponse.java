package io.github.debutante.model.api;

import com.google.gson.annotations.SerializedName;

public final class ArtistResponse {
    @SerializedName("subsonic-response")
    public SubsonicResponse subsonicResponse;

    public static final class SubsonicResponse {
        public String status;
        public Error error;
        public Artist artist;

        public boolean isOk() {
            return "ok".equals(status);
        }
    }
}
