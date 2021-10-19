
package io.github.debutante.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.apache.commons.lang3.StringUtils;

import java.util.function.Consumer;
import java.util.function.Supplier;

import io.github.debutante.R;
import io.github.debutante.helper.EntityHelper;
import io.github.debutante.helper.L;
import io.github.debutante.helper.Obj;
import io.github.debutante.helper.RxHelper;
import io.github.debutante.helper.SubsonicHelper;
import io.github.debutante.persistence.EntityRepository;
import io.github.debutante.service.PlayerService;

public class ChangeMediaItemBroadcastReceiver extends BroadcastReceiver {
    public static final String ACTION = ChangeMediaItemBroadcastReceiver.class.getSimpleName() + "-ACTION";
    public static final String MEDIA_ID_KEY = ChangeMediaItemBroadcastReceiver.class.getSimpleName() + "-MEDIA_ID_KEY";
    private final Consumer<String> onReceive;
    private final ImageView ivAlbumArt;
    private final TextView tvArtist;
    private final TextView tvAlbum;
    private final TextView tvSong;
    private final EntityRepository repository;
    private final Supplier<Picasso> picassoSupplier;
    private final String nameYearPattern;

    public ChangeMediaItemBroadcastReceiver(EntityRepository repository, Supplier<Picasso> picassoSupplier, Consumer<String> onReceive) {
        this(repository, picassoSupplier, onReceive, null, null, null, null);
    }

    public ChangeMediaItemBroadcastReceiver(EntityRepository repository,
                                            Supplier<Picasso> picassoSupplier,
                                            ImageView ivAlbumArt,
                                            TextView tvArtist,
                                            TextView tvAlbum,
                                            TextView tvSong) {
        this(repository, picassoSupplier, null, ivAlbumArt, tvArtist, tvAlbum, tvSong);
    }

    private ChangeMediaItemBroadcastReceiver(EntityRepository repository,
                                             Supplier<Picasso> picassoSupplier,
                                             Consumer<String> onReceive,
                                             ImageView ivAlbumArt,
                                             TextView tvArtist,
                                             TextView tvAlbum,
                                             TextView tvSong) {
        this.repository = repository;
        this.picassoSupplier = picassoSupplier;
        this.onReceive = onReceive;
        this.ivAlbumArt = ivAlbumArt;
        this.tvArtist = tvArtist;
        this.tvAlbum = tvAlbum;
        nameYearPattern = tvAlbum != null ? tvAlbum.getContext().getString(R.string.name_year_pattern) : "";
        this.tvSong = tvSong;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        L.i("Receiving " + intent.getAction() + " " + L.toString(intent.getExtras()));

        String mediaId = intent.getStringExtra(MEDIA_ID_KEY);

        if (onReceive != null) {
            onReceive.accept(mediaId);
        }

        EntityHelper.EntityMetadata metadata = EntityHelper.metadata(mediaId);
        if (ivAlbumArt != null) {
            String coverArt = metadata.params.get(EntityHelper.EntityMetadata.COVER_ART_PARAM);
            if (StringUtils.isNotEmpty(coverArt)) {
                RxHelper.defaultInstance().subscribe(repository.findAccountByUuid(metadata.accountUuid), a -> {
                    String coverArtUri = SubsonicHelper.buildCoverArtUrl(a.url, a.username, a.token, coverArt);
                    if (StringUtils.isNotEmpty(coverArtUri)) {
                        try {
                            picassoSupplier.get()
                                    .load(coverArtUri)
                                    //.centerInside()
                                    .placeholder(R.drawable.ic_song)
                                    .error(R.drawable.ic_song)
                                    .into(ivAlbumArt);
                        } catch (Exception e) {
                            ivAlbumArt.setImageResource(R.drawable.ic_album);
                        }
                    } else {
                        ivAlbumArt.setImageResource(R.drawable.ic_album);
                    }
                }, Throwable::printStackTrace);
            } else {
                ivAlbumArt.setImageResource(R.drawable.ic_album);
            }
        }

        RxHelper.defaultInstance().subscribe(repository.findSongByUuid(metadata.uuid), s -> {
            if (tvArtist != null && s.artist != null) {
                tvArtist.setText(s.artist);
            }
            if (tvAlbum != null && s.album != null) {
                if (s.year > 0)
                    tvAlbum.setText(String.format(nameYearPattern, s.album, s.year));
                else
                    tvAlbum.setText(s.album);
            }
            if (tvSong != null && s.title != null) {
                tvSong.setText(s.title);
            }
        }, Throwable::printStackTrace);
    }

    private static void logBroadcast(Intent intent) {
        L.i("Broadcasting " + ACTION + " " + L.toString(intent.getExtras()));
    }

    public static void broadcast(Context context, String mediaItemId) {
        if (StringUtils.isNotEmpty(mediaItemId)) {
            Intent intent = new Intent(ChangeMediaItemBroadcastReceiver.ACTION);
            intent.putExtra(MEDIA_ID_KEY, mediaItemId);
            context.sendBroadcast(Obj.tap(intent, ChangeMediaItemBroadcastReceiver::logBroadcast));
            Intent service = new Intent(context, PlayerService.class);
            service.setAction(PlayerService.ACTION_WAKE);
            context.startForegroundService(service);
        }
    }
}
