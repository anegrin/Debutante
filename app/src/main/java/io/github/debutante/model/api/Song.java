package io.github.debutante.model.api;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Song {

    private static final Pattern PATTERN = Pattern.compile("[0-9]+");

    public String id;
    public String title;
    public int duration;
    public String coverArt;
    public String track;
    public String discNumber;
    public String album;
    public String artist;
    public String year;

    public int trackAsInt() {
        return parseInt(track);
    }

    public int discNumberAsInt() {
        return parseInt(discNumber);
    }

    public int yearAsInt() {
        return parseInt(year);
    }

    static int parseInt(String crap) {
        if (StringUtils.isBlank(crap)) {
            return 0;
        } else {
            Matcher matcher = PATTERN.matcher(crap);
            boolean found = matcher.find();
            if (found) {
                String first = matcher.group();
                try {
                    return Integer.parseInt(first);
                } catch (NumberFormatException e) {
                    return 0;
                }
            } else {
                return 0;
            }
        }
    }

}
