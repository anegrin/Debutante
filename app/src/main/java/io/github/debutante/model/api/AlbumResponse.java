package io.github.debutante.model.api;

import com.google.gson.annotations.SerializedName;

public final class AlbumResponse {
    @SerializedName("subsonic-response")
    public SubsonicResponse subsonicResponse;

    public static final class SubsonicResponse {
        public String status;
        public Error error;
        public Album album;

        public boolean isOk() {
            return "ok".equals(status);
        }
    }
}
