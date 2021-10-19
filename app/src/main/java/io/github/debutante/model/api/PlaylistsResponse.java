package io.github.debutante.model.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class PlaylistsResponse {
    @SerializedName("subsonic-response")
    public SubsonicResponse subsonicResponse;

    public static final class SubsonicResponse {
        public String status;
        public Error error;
        public Playlists playlists;

        public boolean isOk() {
            return "ok".equals(status);
        }

        public static final class Playlists {
            public List<Playlist> playlist;
        }
    }
}
