package io.github.debutante.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.google.gson.Gson;

import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.debutante.R;
import io.github.debutante.helper.EntityHelper;
import io.github.debutante.helper.GsonRequest;
import io.github.debutante.helper.L;
import io.github.debutante.helper.MediaBrowserHelper;
import io.github.debutante.helper.Obj;
import io.github.debutante.helper.PlayerWrapper;
import io.github.debutante.helper.RxHelper;
import io.github.debutante.helper.SubsonicHelper;
import io.github.debutante.model.api.AlbumResponse;
import io.github.debutante.model.api.ArtistInfoResponse;
import io.github.debutante.model.api.ArtistResponse;
import io.github.debutante.model.api.ArtistsResponse;
import io.github.debutante.model.api.PingResponse;
import io.github.debutante.model.api.PlaylistResponse;
import io.github.debutante.model.api.PlaylistsResponse;
import io.github.debutante.persistence.EntityRepository;
import io.github.debutante.persistence.PlayerState;
import io.github.debutante.persistence.entities.AccountEntity;
import io.github.debutante.persistence.entities.AlbumEntity;
import io.github.debutante.persistence.entities.ArtistEntity;
import io.github.debutante.persistence.entities.BaseEntity;
import io.github.debutante.persistence.entities.SongEntity;
import io.github.debutante.service.MediaService;
import io.github.debutante.service.SyncService;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.OkHttpClient;

public class SyncAccountBroadcastReceiver extends BroadcastReceiver {

    public static final String ACTION = SyncAccountBroadcastReceiver.class.getSimpleName() + "-ACTION";
    public static final String FORCE_STOP_ACTION = SyncAccountBroadcastReceiver.class.getSimpleName() + "-FORCE_STOP_ACTION";
    private static final String ACCOUNT_UUID_KEY = SyncAccountBroadcastReceiver.class.getSimpleName() + "-ACCOUNT_UUID_KEY";
    private static final String ARTIST_UUID_KEY = SyncAccountBroadcastReceiver.class.getSimpleName() + "-ARTIST_UUID_KEY";
    private static final Duration TIMEOUT = Duration.ofMinutes(5);
    public static final Duration CHECK_DELAY = Duration.ofSeconds(3);
    public static final Duration FETCH_DELAY = Duration.ofMillis(200);
    private final AtomicBoolean forceStop = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final OkHttpClient okHttpClient;
    private final PlayerWrapper playerWrapper;
    private final Gson gson;
    private final EntityRepository repository;
    private final RxHelper rxHelper;

    public SyncAccountBroadcastReceiver(OkHttpClient okHttpClient, PlayerWrapper playerWrapper, Gson gson, EntityRepository repository) {
        this.okHttpClient = okHttpClient;
        this.playerWrapper = playerWrapper;
        this.gson = gson;
        this.repository = repository;
        this.rxHelper = RxHelper.newInstance(Schedulers.io(), Schedulers.computation());
    }

    private static void logBroadcast(Intent intent) {
        L.i("Broadcasting " + intent.getAction() + " " + L.toString(intent.getExtras()));
    }

    public static void broadcastForceStop(Context context) {
        Intent intent = new Intent(SyncAccountBroadcastReceiver.FORCE_STOP_ACTION);
        context.sendBroadcast(Obj.tap(intent, SyncAccountBroadcastReceiver::logBroadcast));
    }

    public static void broadcast(Context context) {
        Intent intent = new Intent(SyncAccountBroadcastReceiver.ACTION);
        context.sendBroadcast(intent);
        context.sendBroadcast(Obj.tap(intent, SyncAccountBroadcastReceiver::logBroadcast));
    }

    public static void broadcast(Context context, AccountEntity entity) {
        broadcast(context, entity.uuid());
    }

    public static void broadcast(Context context, String accountUuid) {
        Intent intent = new Intent(SyncAccountBroadcastReceiver.ACTION);
        intent.putExtra(ACCOUNT_UUID_KEY, accountUuid);
        context.sendBroadcast(Obj.tap(intent, SyncAccountBroadcastReceiver::logBroadcast));
    }

    public static void broadcast(Context context, String accountUuid, String artistUuid) {
        Intent intent = new Intent(SyncAccountBroadcastReceiver.ACTION);
        intent.putExtra(ARTIST_UUID_KEY, artistUuid);
        intent.putExtra(ACCOUNT_UUID_KEY, accountUuid);
        context.sendBroadcast(Obj.tap(intent, SyncAccountBroadcastReceiver::logBroadcast));
    }

    private void toast(Context context, String message) {
        AndroidSchedulers.mainThread().scheduleDirect(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        if (FORCE_STOP_ACTION.equals(intent.getAction())) {
            forceStop.set(true);
        } else {
            boolean playing = !playerWrapper.player().isPlaying();
            if (!running.get() && playing) {
                running.set(true);
                forceStop.set(false);
                String artistUuid = intent.getStringExtra(ARTIST_UUID_KEY);
                String accountUuid = intent.getStringExtra(ACCOUNT_UUID_KEY);
                if (StringUtils.isNotBlank(artistUuid) && StringUtils.isNotBlank(accountUuid)) {
                    loadArtist(context, artistUuid, accountUuid);
                } else if (StringUtils.isNotBlank(accountUuid)) {
                    loadAccount(context, accountUuid);
                } else {
                    rxHelper.subscribe(repository.getAllAccounts(), l -> l.stream().filter(AccountEntity::isCloud).forEach(a -> loadAccount(context, a)), Throwable::printStackTrace);
                }
                context.startForegroundService(new Intent(context, SyncService.class));
            } else if (playing) {
                String message = context.getString(R.string.cant_sync_while_playing);
                toast(context, message);
            }
        }
    }

    private void loadAccount(Context context, String accountUuid) {
        loadAccount(context, accountUuid, this::loadAccount);
    }

    private void loadAccount(Context context, String accountUuid, BiConsumer<Context, AccountEntity> callback) {

        PlayerState.clear(context, accountUuid);

        if (forceStop.get()) {
            onFinish(context);
            return;
        }

        L.i("Loading account with uuid " + accountUuid);
        rxHelper.subscribe(repository.findAccountByUuid(accountUuid), a -> callback.accept(context, a), Throwable::printStackTrace);
    }

    private void loadArtist(Context context, String artistUuid, String accountUuid) {

        if (forceStop.get()) {
            onFinish(context);
            return;
        }

        L.i("Loading artist with uuid " + artistUuid);
        loadAccount(context, accountUuid, (x, a) -> {

            PlayerState.clear(context, a.uuid());

            rxHelper.subscribe(repository.findArtistByUuid(artistUuid), ar -> {
                toast(x, String.format(x.getString(R.string.fetch_start_artist), ar.name, a.alias));
                GsonRequest<PingResponse> request = SubsonicHelper.buildPingRequest(gson, a.url, a.username, a.token, r -> {
                    if (r.subsonicResponse.isOk()) {
                        Counters counters = new Counters();
                        rxHelper.subscribe(repository.deleteAllSongsByArtistUuid(artistUuid),
                                () -> rxHelper.subscribe(repository.deleteAllAlbumsByArtistUuid(artistUuid),
                                        () -> {
                                            if (ArtistEntity.PLAYLISTS_REMOTE_UUID.equals(ar.remoteUuid)) {
                                                fetchPlaylists(x, a, ar, counters, true);
                                            } else {
                                                fetchArtistInfo(x, a, Collections.singletonList(ar), counters);
                                            }
                                        },
                                        Throwable::printStackTrace),
                                Throwable::printStackTrace);
                    } else {
                        toast(x, x.getString(R.string.test_connectivity_failure) + "\n" + x.getString(r.subsonicResponse.error.stringResId()));
                    }
                }, e -> toast(x, x.getString(R.string.test_connectivity_failure) + "\n" + e.getMessage()));
                request.enqueue(okHttpClient);
            }, Throwable::printStackTrace);
        });
    }

    private void loadAccount(Context context, AccountEntity a) {

        if (forceStop.get()) {
            onFinish(context);
            return;
        }

        L.i("Loading account " + a.alias);

        toast(context, String.format(context.getString(R.string.fetch_start_account), a.alias));
        GsonRequest<PingResponse> request = SubsonicHelper.buildPingRequest(gson, a.url, a.username, a.token, r -> {
            if (r.subsonicResponse.isOk()) {
                Counters counters = new Counters();
                cleanupEntities(context, a, counters, repository::deleteAllSongsByAccountUuid,
                        (x1, a1, c1) -> cleanupEntities(x1, a1, counters, repository::deleteAllAlbumsByAccountUuid,
                                (x2, a2, c2) -> cleanupEntities(x2, a2, counters, repository::deleteAllArtistsByAccountUuid, this::fetchArtists)
                        )
                );
            } else {
                toast(context, context.getString(R.string.test_connectivity_failure) + "\n" + context.getString(r.subsonicResponse.error.stringResId()));
            }
        }, e -> toast(context, context.getString(R.string.test_connectivity_failure) + "\n" + e.getMessage()));
        request.enqueue(okHttpClient);


    }

    private <T extends BaseEntity> void cleanupEntities(Context context, AccountEntity accountEntity, Counters counters, Function<String, Completable> deleteAll, Fetcher fetcher) {
        L.i("Cleaning up entities for account " + accountEntity.alias);
        rxHelper.subscribe(deleteAll.apply(accountEntity.uuid), () -> fetcher.fetch(context, accountEntity, counters), Throwable::printStackTrace);
    }

    private void fetchArtists(Context context, AccountEntity accountEntity, Counters counters) {

        if (forceStop.get()) {
            onFinish(context);
            return;
        }

        L.i("Fetching artists for account " + accountEntity.alias);

        String fullURL = SubsonicHelper.buildUrl(accountEntity.url, "rest/getArtists", accountEntity.username, accountEntity.token);
        GsonRequest<ArtistsResponse> request = new GsonRequest<>(fullURL, ArtistsResponse.class, gson, r -> {
            if (r.subsonicResponse.isOk()) {
                ArtistEntity playlistsEntity = new ArtistEntity(accountEntity.uuid, ArtistEntity.PLAYLISTS_REMOTE_UUID, "Playlists", 0, null);
                saveEntities(Collections.singletonList(playlistsEntity), counters.artists, repository::insertAllArtists, () -> fetchPlaylists(context, accountEntity, playlistsEntity, counters, false));

                List<ArtistEntity> entities = r.subsonicResponse.artists.index.stream().flatMap(i -> i.artist.stream()).map(a -> EntityHelper.toEntity(accountEntity, a)).collect(Collectors.toList());
                saveEntities(entities, counters.artists, repository::insertAllArtists, () -> fetchArtistInfo(context, accountEntity, entities, counters));
            } else {
                toast(context, context.getString(R.string.fetch_artitsts_failure) + "\n" + context.getString(r.subsonicResponse.error.stringResId()));
            }
        }, e -> toast(context, context.getString(R.string.fetch_artitsts_failure) + "\n" + e.getMessage()));

        request.enqueue(okHttpClient);
    }

    private void fetchArtistInfo(Context context, AccountEntity accountEntity, List<ArtistEntity> artistEntities, Counters counters) {
        int i = 0;
        for (ArtistEntity artistEntity : artistEntities) {

            if (forceStop.get()) {
                onFinish(context);
                return;
            }

            final boolean last = ++i == artistEntities.size();

            L.i("Fetching artist info for account " + accountEntity.alias + " and artist " + artistEntity.name);

            String fullURL = SubsonicHelper.buildUrl(accountEntity.url, "rest/getArtistInfo", accountEntity.username, accountEntity.token, Collections.singletonMap("id", artistEntity.remoteUuid));
            GsonRequest<ArtistInfoResponse> request = new GsonRequest<>(fullURL, ArtistInfoResponse.class, gson, r -> {
                if (r.subsonicResponse.isOk()) {
                    artistEntity.artistInfo = EntityHelper.toEntity(r.subsonicResponse.artistInfo);

                    rxHelper.subscribe(repository.updateArtist(artistEntity), () -> fetchAlbums(context, accountEntity, artistEntity, counters, last), Throwable::printStackTrace);
                } else {
                    toast(context, context.getString(R.string.fetch_albums_failure) + "\n" + context.getString(r.subsonicResponse.error.stringResId()));
                }
            }, e -> toast(context, context.getString(R.string.fetch_albums_failure) + "\n" + e.getMessage()));

            request.enqueue(okHttpClient);
            sleep(getRandomDelay());
        }
    }

    private void fetchAlbums(Context context, AccountEntity accountEntity, ArtistEntity artistEntity, Counters counters, boolean lastArtist) {

        if (forceStop.get()) {
            onFinish(context);
            return;
        }

        L.i("Fetching albums for account " + accountEntity.alias + " and artist " + artistEntity.name);

        String fullURL = SubsonicHelper.buildUrl(accountEntity.url, "rest/getArtist", accountEntity.username, accountEntity.token, Collections.singletonMap("id", artistEntity.remoteUuid));
        GsonRequest<ArtistResponse> request = new GsonRequest<>(fullURL, ArtistResponse.class, gson, r -> {
            if (r.subsonicResponse.isOk()) {
                List<AlbumEntity> entities = r.subsonicResponse.artist.album.stream().map(a -> EntityHelper.toEntity(accountEntity, artistEntity, a)).collect(Collectors.toList());
                saveEntities(entities, counters.albums, repository::insertAllAlbums, () -> fetchSongs(context, accountEntity, artistEntity, entities, counters, lastArtist));
            } else {
                toast(context, context.getString(R.string.fetch_albums_failure) + "\n" + context.getString(r.subsonicResponse.error.stringResId()));
            }
        }, e -> toast(context, context.getString(R.string.fetch_albums_failure) + "\n" + e.getMessage()));

        request.enqueue(okHttpClient);
    }

    private void fetchSongs(Context context, AccountEntity accountEntity, ArtistEntity artistEntity, List<AlbumEntity> albumEntities, Counters counters, boolean lastArtist) {
        int i = 0;
        for (AlbumEntity albumEntity : albumEntities) {

            if (forceStop.get()) {
                onFinish(context);
                return;
            }

            L.i("Fetching songs for account " + accountEntity.alias + " and artist " + artistEntity.name);

            final boolean last = ++i == albumEntities.size();

            String fullURL = SubsonicHelper.buildUrl(accountEntity.url, "rest/getAlbum", accountEntity.username, accountEntity.token, Collections.singletonMap("id", albumEntity.remoteUuid));
            GsonRequest<AlbumResponse> request = new GsonRequest<>(fullURL, AlbumResponse.class, gson, r -> {
                if (r.subsonicResponse.isOk()) {
                    List<SongEntity> entities = r.subsonicResponse.album.song.stream().map(s -> EntityHelper.toEntity(accountEntity, artistEntity, albumEntity, s)).collect(Collectors.toList());
                    saveEntities(entities, counters.songs, repository::insertAllSongs, () -> {
                        if (lastArtist && last) {
                            waitForCompletion(context, artistEntity, counters);
                        }
                    });
                } else {
                    toast(context, context.getString(R.string.fetch_albums_failure) + "\n" + context.getString(r.subsonicResponse.error.stringResId()));
                }
            }, e -> toast(context, context.getString(R.string.fetch_albums_failure) + "\n" + e.getMessage()));

            request.enqueue(okHttpClient);
            sleep(getRandomDelay());
        }
    }

    private void fetchPlaylists(Context context, AccountEntity accountEntity, ArtistEntity playlistsEntity, Counters counters, boolean lastArtist) {

        if (forceStop.get()) {
            onFinish(context);
            return;
        }

        L.i("Fetching playlists for account " + accountEntity.alias);

        String fullURL = SubsonicHelper.buildUrl(accountEntity.url, "rest/getPlaylists", accountEntity.username, accountEntity.token);
        GsonRequest<PlaylistsResponse> request = new GsonRequest<>(fullURL, PlaylistsResponse.class, gson, r -> {
            if (r.subsonicResponse.isOk()) {
                if (r.subsonicResponse.playlists.playlist != null) {
                    List<AlbumEntity> entities = r.subsonicResponse.playlists.playlist.stream().map(p -> EntityHelper.toEntity(accountEntity, playlistsEntity, p)).collect(Collectors.toList());
                    saveEntities(entities, counters.playlists, repository::insertAllAlbums, () -> fetchPlaylistsSongs(context, accountEntity, playlistsEntity, entities, counters, lastArtist));
                }
            } else {
                toast(context, context.getString(R.string.fetch_albums_failure) + "\n" + context.getString(r.subsonicResponse.error.stringResId()));
            }
        }, e -> toast(context, context.getString(R.string.fetch_albums_failure) + "\n" + e.getMessage()));

        request.enqueue(okHttpClient);
    }

    private void fetchPlaylistsSongs(Context context, AccountEntity accountEntity, ArtistEntity artistEntity, List<AlbumEntity> playlistEntities, Counters counters, boolean lastArtist) {
        int i = 0;
        for (AlbumEntity playlist : playlistEntities) {

            if (forceStop.get()) {
                onFinish(context);
                return;
            }

            L.i("Fetching songs for account " + accountEntity.alias + " and artist " + artistEntity.name);

            final boolean lastPlaylist = ++i == playlistEntities.size();

            String fullURL = SubsonicHelper.buildUrl(accountEntity.url, "rest/getPlaylist", accountEntity.username, accountEntity.token, Collections.singletonMap("id", playlist.remoteUuid));
            GsonRequest<PlaylistResponse> request = new GsonRequest<>(fullURL, PlaylistResponse.class, gson, r -> {
                if (r.subsonicResponse.isOk()) {
                    AtomicInteger track = new AtomicInteger(1);
                    List<SongEntity> entities = r.subsonicResponse.playlist.entry.stream().map(s -> EntityHelper.toEntity(accountEntity, artistEntity, playlist, s, track.getAndIncrement())).collect(Collectors.toList());
                    saveEntities(entities, counters.entry, repository::insertAllSongs, () -> {
                        if (lastArtist && lastPlaylist) {
                            waitForCompletion(context, artistEntity, counters);
                        }
                    });
                } else {
                    toast(context, context.getString(R.string.fetch_albums_failure) + "\n" + context.getString(r.subsonicResponse.error.stringResId()));
                }
            }, e -> toast(context, context.getString(R.string.fetch_albums_failure) + "\n" + e.getMessage()));

            request.enqueue(okHttpClient);
            sleep(getRandomDelay());
        }
    }

    private <T extends BaseEntity> void saveEntities(List<T> entities, AtomicInteger counter, Function<List<T>, Completable> insertAll, Action onComplete) {
        final int size = entities.size();
        counter.addAndGet(size);

        rxHelper.subscribe(insertAll.apply(entities), () -> {
            onComplete.run();
            counter.addAndGet(-1 * size);
        }, Throwable::printStackTrace);
    }

    private void waitForCompletion(Context context, BaseEntity parentEntity, Counters counters) {
        if (!counters.checking.getAndSet(true)) {

            Completable.fromAction(() -> {
                long timeoutAt = System.currentTimeMillis() + TIMEOUT.toMillis();
                boolean done = false;
                while (!counters.done() && !done && System.currentTimeMillis() < timeoutAt && !forceStop.get()) {
                    Thread.sleep(CHECK_DELAY.toMillis() / 5);
                    done = counters.done();
                }

            }).delay(CHECK_DELAY.toMillis(), TimeUnit.MILLISECONDS)
                    .subscribeOn(Schedulers.from(Executors.newSingleThreadExecutor(RxHelper.LOW_PRI_THREAD_FACTORY)))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> {
                        onFinish(context);
                        String mediaId = EntityHelper.mediaId(EntityHelper.mediaId(parentEntity), Collections.singletonMap(MediaBrowserHelper.PREPEND_ACTIONS, false));
                        BrowseMediaBroadcastReceiver.broadcast(context, mediaId);
                        context.stopService(new Intent(context, SyncService.class));
                        MediaService.invalidateSession();
                    }, Throwable::printStackTrace);
        }
    }

    private synchronized void onFinish(Context context) {
        toast(context, context.getString(forceStop.get() ? R.string.fetch_aborted : R.string.fetch_success));
        running.set(false);
        forceStop.set(false);
        MediaService.invalidateSession();
    }

    private Duration getRandomDelay() {
        return Duration.ofMillis((long) (Math.random() * 2 * FETCH_DELAY.toMillis()));
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class Counters {
        private final AtomicBoolean checking = new AtomicBoolean(false);
        private final AtomicInteger artists = new AtomicInteger();
        private final AtomicInteger albums = new AtomicInteger();
        private final AtomicInteger songs = new AtomicInteger();
        private final AtomicInteger playlists = new AtomicInteger();
        private final AtomicInteger entry = new AtomicInteger();

        public boolean done() {
            return checking.get() && artists.get() == 0 && albums.get() == 0 && songs.get() == 0 && playlists.get() == 0 && entry.get() == 0;
        }
    }

    @FunctionalInterface
    private interface Fetcher {
        void fetch(Context context, AccountEntity accountEntity, Counters counters);
    }
}
