package io.github.debutante.model.api;

import com.google.gson.annotations.SerializedName;

public final class PlaylistResponse {
    @SerializedName("subsonic-response")
    public SubsonicResponse subsonicResponse;

    public static final class SubsonicResponse {
        public String status;
        public Error error;
        public Playlist playlist;

        public boolean isOk() {
            return "ok".equals(status);
        }
    }
}
