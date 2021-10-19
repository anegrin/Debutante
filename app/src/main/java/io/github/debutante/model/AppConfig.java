package io.github.debutante.model;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import io.github.debutante.R;

public class AppConfig {

    private static final String SERVER_VALUE = "server";
    private final Context context;

    private boolean accountsLocalEnabled;
    private boolean castLocalEnabled;
    private boolean playlistsEnabled;
    private boolean artistPicEnabled;
    private boolean autoplayOnBTEnabled;
    private Set<String> autoplayBTExclusion;
    private boolean artOnBTEnabled;
    private boolean offloadEnabled;
    private boolean carTextIconsEnabled;
    private boolean accountsSyncEnabled;
    private int accountsSyncIntervalHours;
    private boolean preloadOnWiFiOnly;
    private long coverArtCacheSize;
    private long songCacheSize;
    private int songsToPreload;
    private String streamingFormat;
    private String streamingBitrate;
    private final List<Consumer<AppConfig>> onRefreshListeners = new ArrayList<>();
    private int streamingTimeoutSecs;
    private boolean handleAudioBecomingNoisy;

    public AppConfig(Context context) {
        this.context = context;
        setFields();
    }

    public void addOnRefreshListeners(Consumer<AppConfig> listener) {
        onRefreshListeners.add(listener);
    }

    public void removeOnRefreshListeners(Consumer<AppConfig> listener) {
        onRefreshListeners.remove(listener);
    }

    public void refresh() {
        setFields();
        onRefreshListeners.forEach(c -> c.accept(this));
    }

    private void setFields() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        accountsLocalEnabled = sharedPreferences.getBoolean("accounts_local_enabled", true);
        castLocalEnabled = sharedPreferences.getBoolean("cast_local_enabled", false);
        playlistsEnabled = sharedPreferences.getBoolean("playlists_enabled", true);
        artistPicEnabled = sharedPreferences.getBoolean("artist_pic_local_enabled", true);
        autoplayOnBTEnabled = sharedPreferences.getBoolean("autoplay_on_bt_enabled", false);
        autoplayBTExclusion = sharedPreferences.getStringSet("autoplay_bt_exclusion", Collections.emptySet());
        artOnBTEnabled = sharedPreferences.getBoolean("art_on_bt_enabled", false);
        offloadEnabled = sharedPreferences.getBoolean("offload_enabled", false);
        carTextIconsEnabled = sharedPreferences.getBoolean("car_text_icons_enabled", false);
        accountsSyncEnabled = sharedPreferences.getBoolean("accounts_sync_enabled", true);
        accountsSyncIntervalHours = sharedPreferences.getInt("accounts_sync_interval_hours", context.getResources().getInteger(R.integer.accounts_sync_interval_hours_default));
        preloadOnWiFiOnly = sharedPreferences.getBoolean("song_preload_wifi", false);
        coverArtCacheSize = sharedPreferences.getInt("cover_art_cache_mb", context.getResources().getInteger(R.integer.cover_art_cache_mb_default)) * 1024L * 1024L;
        songCacheSize = sharedPreferences.getInt("song_cache_mb", context.getResources().getInteger(R.integer.song_cache_mb_default)) * 1024L * 1024L;
        songsToPreload = sharedPreferences.getInt("song_preload", context.getResources().getInteger(R.integer.song_preload_default));
        streamingFormat = sharedPreferences.getString("streaming_format", context.getResources().getString(R.string.format_default));
        streamingBitrate = sharedPreferences.getString("streaming_bitrate", context.getResources().getString(R.string.bitrate_default));
        streamingTimeoutSecs = sharedPreferences.getInt("streaming_timeout_secs", context.getResources().getInteger(R.integer.streaming_timeout_secs));
        handleAudioBecomingNoisy = sharedPreferences.getBoolean("handle_audio_becoming_noisy", true);
    }

    public boolean isAccountsLocalEnabled() {
        return accountsLocalEnabled;
    }

    public boolean isCastLocalEnabled() {
        return isAccountsLocalEnabled() && castLocalEnabled;
    }

    public boolean isPlaylistsEnabled() {
        return playlistsEnabled;
    }

    public boolean isArtistPicEnabled() {
        return artistPicEnabled;
    }

    public boolean isAutoplayOnBTEnabled() {
        return autoplayOnBTEnabled;
    }

    public Set<String> getAutoplayBTExclusion() {
        return autoplayBTExclusion;
    }

    public boolean isArtOnBTEnabled() {
        return artOnBTEnabled;
    }

    public boolean isOffloadEnabled() {
        return offloadEnabled;
    }

    public boolean isCarTextIconsEnabled() {
        return carTextIconsEnabled;
    }

    public boolean isAccountsSyncEnabled() {
        return accountsSyncEnabled;
    }

    public int getAccountsSyncIntervalHours() {
        return accountsSyncIntervalHours;
    }

    public boolean isPreloadOnWiFiOnly() {
        return preloadOnWiFiOnly;
    }

    public long getCoverArtCacheSize() {
        return coverArtCacheSize;
    }

    public long getSongCacheSize() {
        return songCacheSize;
    }

    public int getSongsToPreload() {
        return songsToPreload;
    }

    public Optional<String> getStreamingFormat() {
        return SERVER_VALUE.equals(streamingFormat) ? Optional.empty() : Optional.ofNullable(streamingFormat);
    }

    public Optional<String> getStreamingBitrate() {
        return SERVER_VALUE.equals(streamingBitrate) ? Optional.empty() : Optional.ofNullable(streamingBitrate);
    }

    public int getStreamingTimeoutSecs() {
        return streamingTimeoutSecs;
    }

    public boolean isHandleAudioBecomingNoisy() {
        return handleAudioBecomingNoisy;
    }
}
