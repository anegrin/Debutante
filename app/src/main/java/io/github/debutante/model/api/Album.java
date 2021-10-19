package io.github.debutante.model.api;

import java.util.List;

public final class Album {
    public String id;
    public String name;
    public int songCount;
    public int duration;
    public String coverArt;
    public String year;
    public String artist;
    public List<Song> song;

    public int yearAsInt() {
        return Song.parseInt(year);
    }
}
