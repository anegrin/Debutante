package io.github.debutante;

import static io.github.debutante.BuildConfig.L_D;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.Session2Token;
import android.media.session.MediaSessionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.google.android.gms.cast.framework.CastContext;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.squareup.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;

import org.acra.ACRA;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.data.StringFormat;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import io.github.debutante.adapter.AudioMediaItemConverter;
import io.github.debutante.helper.DeviceHelper;
import io.github.debutante.helper.L;
import io.github.debutante.helper.Obj;
import io.github.debutante.helper.RxHelper;
import io.github.debutante.helper.Scheduler;
import io.github.debutante.listeners.DynamicSizeCacheEvictor;
import io.github.debutante.model.AppConfig;
import io.github.debutante.persistence.EntityRepository;
import okhttp3.Call;
import okhttp3.OkHttpClient;

public class Debutante extends Application {

    public static final int MAX_PARALLEL_DOWNLOADS = 2;
    public static final boolean HANDLE_AUDIO_BECOMING_NOISY = true;
    public static final String COVER_ART_CACHE = "coverArt-cache";

    private final Object PICASSO_LOCK = new Object();
    private AppConfig appConfig;

    public static final String TAG = L_D ? "Debugante" : "Debutante";
    public static final String NOTIFICATION_CHANNEL_ID = TAG;
    public static final int NOTIFICATION_ID = 2112;
    public static final Requirements DOWNLOAD_REQUIREMENTS_DEFAULT = new Requirements(Requirements.NETWORK);
    public static final Requirements DOWNLOAD_REQUIREMENTS_WIFI = new Requirements(Requirements.NETWORK | Requirements.NETWORK_UNMETERED);
    private OkHttpClient okHttpClient;
    private Gson gson;
    private MediaSessionCompat mediaSession;
    private EntityRepository repository;
    private DownloadManager downloadManager;
    private Picasso picasso;
    private okhttp3.Cache picassoCache;
    private File cacheDir;
    private ExoPlayer exoPlayer;
    private CastPlayer castPlayer;
    private CastContext sharedInstance;
    private AudioMediaItemConverter mediaItemConverter;

    public Debutante() {
        super();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (repository != null) {
                repository.close();
            }
        }));
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        if (L_D) {
            CoreConfigurationBuilder builder = new CoreConfigurationBuilder(this);
            //core configuration:
            builder
                    .withBuildConfigClass(BuildConfig.class)
                    .withReportFormat(StringFormat.KEY_VALUE_LIST);
            //each plugin you chose above can be configured with its builder like this:
            builder.getPluginConfigurationBuilder(MailSenderConfigurationBuilder.class)
                    //required
                    .withMailTo(BuildConfig.ACRA_EMAIL)
                    //defaults to true
                    .withReportAsFile(true)
                    //defaults to ACRA-report.stacktrace
                    .withReportFileName("Crash.txt")
                    //defaults to "<applicationId> Crash Report"
                    .withSubject("Acra - " + TAG)
                    .withEnabled(true);

            builder.getPluginConfigurationBuilder(DialogConfigurationBuilder.class)
                    //required
                    .withEnabled(true)
                    //required
                    .withResText(R.string.app_name)
                    //optional, enables the dialog title
                    .withResTitle(R.string.app_name)
                    .withText("Sending to " + BuildConfig.ACRA_EMAIL);

            ACRA.init(this, builder);
        }
    }

    @NonNull
    public static AppConfig loadAppConfig(@NonNull Context context) {
        return new AppConfig(context);
    }

    private synchronized AppConfig getAppConfig(@NonNull Context context) {
        if (appConfig == null) {
            appConfig = loadAppConfig(context);
        }
        return appConfig;
    }

    @NonNull
    private static Scheduler buildScheduler(Context context, AppConfig appConfig) {
        Scheduler scheduler = new Scheduler(context);
        scheduler.scheduleSync(appConfig, false);
        return scheduler;
    }

    @NonNull
    private static DownloadManager buildDownloadManager(Context context, StandaloneDatabaseProvider databaseProvider, Cache downloadCache, DataSource.Factory cacheDataSourceFactory, AppConfig appConfig) {
        return Obj.tap(new DownloadManager(
                context,
                databaseProvider,
                downloadCache,
                cacheDataSourceFactory,
                Executors.newFixedThreadPool(MAX_PARALLEL_DOWNLOADS, RxHelper.LOW_PRI_THREAD_FACTORY)), d -> {
            d.setRequirements(getDownloadRequirements(appConfig));
            d.setMaxParallelDownloads(MAX_PARALLEL_DOWNLOADS);

            appConfig.addOnRefreshListeners(a -> d.setRequirements(getDownloadRequirements(a)));
        });
    }

    @NonNull
    private static StandaloneDatabaseProvider buildStandaloneDatabaseProvider(Context context) {
        return new StandaloneDatabaseProvider(context);
    }

    @NonNull
    private static SimpleCache buildDownloadCache(File cacheDir, StandaloneDatabaseProvider databaseProvider, AppConfig appConfig) {
        return new SimpleCache(
                cacheDir,
                new DynamicSizeCacheEvictor(appConfig),
                databaseProvider);
    }

    @NonNull
    private static DataSource.Factory buildCacheDataSourceFactory(Context context, AppConfig appConfig, Cache downloadCache) {

        DataSource.Factory httpDataSource = new OkHttpDataSource.Factory((Call.Factory) newOkHttpClientBuilder(appConfig).build());

        return new CacheDataSource.Factory()
                .setCache(downloadCache)
                .setUpstreamDataSourceFactory(new DefaultDataSource.Factory(context, httpDataSource))
                .setCacheWriteDataSinkFactory(null);
    }

    @NonNull
    private static OkHttpClient.Builder newOkHttpClientBuilder(AppConfig appConfig) {
        int streamingTimeoutSecs = appConfig.getStreamingTimeoutSecs();
        return new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .addInterceptor(c -> c.proceed(c.request().newBuilder()
                        .header("User-Agent", "Debutante")
                        .build()))
                .readTimeout(Duration.ofSeconds(streamingTimeoutSecs))
                .connectTimeout(Duration.ofSeconds(streamingTimeoutSecs));
    }

    public static String createNotificationChannel(Context context, String key) {
        NotificationManager systemService = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = Obj.tap(new NotificationChannel(key, key, NotificationManager.IMPORTANCE_DEFAULT), c -> {
            c.setDescription(key);
            c.setSound(null, null);
            c.enableLights(false);
            c.enableVibration(false);
        });
        systemService.createNotificationChannel(channel);
        return channel.getId();
    }

    private static Requirements getDownloadRequirements(AppConfig appConfig) {
        L.i("Building download requirements for Exo download service, preloadOnWiFiOnly=" + appConfig.isPreloadOnWiFiOnly());
        return appConfig.isPreloadOnWiFiOnly() ? DOWNLOAD_REQUIREMENTS_WIFI : DOWNLOAD_REQUIREMENTS_DEFAULT;
    }

    public OkHttpClient okHttpClient() {
        return okHttpClient;
    }

    public Gson gson() {
        return gson;
    }

    public EntityRepository repository() {
        return repository;
    }

    public DownloadManager downloadManager() {
        return downloadManager;
    }

    public ExoPlayer exoPlayer() {
        return exoPlayer;
    }

    public CastPlayer castPlayer() {
        return castPlayer;
    }

    public CastContext sharedInstance() {
        return sharedInstance;
    }

    public AudioMediaItemConverter mediaItemConverter() {
        return mediaItemConverter;
    }

    public File cacheDir() {
        return cacheDir;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AppConfig appConfig = getAppConfig(this);

        Scheduler scheduler = buildScheduler(this, appConfig);
        appConfig.addOnRefreshListeners(a -> scheduler.scheduleSync(a, true));

        cacheDir = new File(this.getCacheDir(), COVER_ART_CACHE);
        Consumer<Long> listener = s -> {
            L.i("Initializing Picasso, coverArtCacheSize=" + s);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }

            picassoCache = new okhttp3.Cache(cacheDir, s);
            OkHttp3Downloader downloader = new OkHttp3Downloader(newOkHttpClientBuilder(appConfig).cache(picassoCache).build());
            picasso = new Picasso.Builder(this).indicatorsEnabled(L_D).downloader(downloader).build();
        };
        listener.accept(appConfig.getCoverArtCacheSize());

        appConfig.addOnRefreshListeners(a -> {
            synchronized (PICASSO_LOCK) {
                try {
                    picassoCache.evictAll();
                } catch (IOException e) {
                }
                picasso.shutdown();
                listener.accept(a.getCoverArtCacheSize());
            }
        });


        okHttpClient = newOkHttpClientBuilder(appConfig).build();

        gson = new GsonBuilder().create();
        repository = new EntityRepository(this, appConfig);

        StandaloneDatabaseProvider databaseProvider = buildStandaloneDatabaseProvider(this);
        Cache downloadCache = buildDownloadCache(getCacheDir(), databaseProvider, appConfig);

        DataSource.Factory cacheDataSourceFactory = buildCacheDataSourceFactory(this, appConfig, downloadCache);

        downloadManager = buildDownloadManager(this, databaseProvider, downloadCache, cacheDataSourceFactory, appConfig);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        exoPlayer = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(cacheDataSourceFactory))
                .setRenderersFactory(new DefaultRenderersFactory(this) {
                    @Override
                    protected void buildVideoRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback, Handler eventHandler, VideoRendererEventListener eventListener, long allowedVideoJoiningTimeMs, ArrayList<Renderer> out) {
                    }

                    @Override
                    protected void buildTextRenderers(Context context, TextOutput output, Looper outputLooper, int extensionRendererMode, ArrayList<Renderer> out) {
                    }

                    @Override
                    protected void buildMetadataRenderers(Context context, MetadataOutput output, Looper outputLooper, int extensionRendererMode, ArrayList<Renderer> out) {
                    }

                    @Override
                    protected void buildCameraMotionRenderers(Context context, int extensionRendererMode, ArrayList<Renderer> out) {
                    }
                }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                        .setEnableAudioOffload(DeviceHelper.supportsAudioOffload()))
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(appConfig.isHandleAudioBecomingNoisy())
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();

        appConfig.addOnRefreshListeners(a -> exoPlayer.setHandleAudioBecomingNoisy(a.isHandleAudioBecomingNoisy()));

        sharedInstance = CastContext.getSharedInstance(this);
        mediaItemConverter = new AudioMediaItemConverter();
        castPlayer = new CastPlayer(sharedInstance, mediaItemConverter);

        MediaSessionManager manager = (MediaSessionManager) this
                .getSystemService(Context.MEDIA_SESSION_SERVICE);

        if (manager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                List<Session2Token> session2Tokens = manager.getSession2Tokens();
                session2Tokens.forEach(s -> L.i("sessions extra: " + s.getPackageName() + ", " + s.getExtras()));
            }
        }
    }

    public AppConfig appConfig() {
        return getAppConfig(this);
    }

    public Picasso picasso() {
        synchronized (PICASSO_LOCK) {
            return picasso;
        }
    }
}
