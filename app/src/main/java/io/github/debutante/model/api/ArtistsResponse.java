package io.github.debutante.model.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class ArtistsResponse {
    @SerializedName("subsonic-response")
    public SubsonicResponse subsonicResponse;

    public static final class SubsonicResponse {
        public String status;
        public Error error;
        public Artists artists;

        public boolean isOk() {
            return "ok".equals(status);
        }

        public static final class Artists {
            public List<Index> index;

            public static final class Index {
                public String name;
                public List<Artist> artist;
            }
        }
    }
}
