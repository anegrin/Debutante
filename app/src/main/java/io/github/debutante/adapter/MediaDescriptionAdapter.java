package io.github.debutante.adapter;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.Optional;
import java.util.function.Supplier;

import io.github.debutante.MainActivity;
import io.github.debutante.R;
import io.github.debutante.helper.URIHelper;

public class MediaDescriptionAdapter implements PlayerNotificationManager.MediaDescriptionAdapter {
    public static final int OPEN_ACTIVITY_INTENT_REQUEST_CODE = 1;
    private final Bitmap songIcon;
    private final Context context;
    private final Supplier<Picasso> picassoSupplier;

    public MediaDescriptionAdapter(Context context, Supplier<Picasso> picassoSupplier) {
        songIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_song);
        this.context = context;
        this.picassoSupplier = picassoSupplier;
    }

    @Override
    public CharSequence getCurrentContentTitle(Player player) {
        return Optional.ofNullable(player.getCurrentMediaItem()).map(m -> m.mediaMetadata.title).orElse("");
    }

    @Nullable
    @Override
    public PendingIntent createCurrentContentIntent(Player player) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.OPEN_PLAYER_KEY, true);
        return PendingIntent.getActivity(context, OPEN_ACTIVITY_INTENT_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Nullable
    @Override
    public CharSequence getCurrentContentText(Player player) {
        return Optional.ofNullable(player.getCurrentMediaItem()).map(m -> m.mediaMetadata.albumTitle).orElse("");
    }

    @Nullable
    @Override
    public Bitmap getCurrentLargeIcon(Player player, PlayerNotificationManager.BitmapCallback callback) {
        Optional.ofNullable(player.getCurrentMediaItem()).map(m -> m.mediaMetadata.artworkUri).ifPresent(u -> {

                    String url = u.toString();

                    try {
                        if (URIHelper.isRemote(url)) {

                            picassoSupplier.get()
                                    .load(url)
                                    //.centerInside()
                                    .placeholder(R.drawable.ic_song)
                                    .error(R.drawable.ic_song)
                                    .into(new Target() {
                                        @Override
                                        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                                            callback.onBitmap(bitmap);
                                        }

                                        @Override
                                        public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                                            callback.onBitmap(songIcon);
                                        }

                                        @Override
                                        public void onPrepareLoad(Drawable placeHolderDrawable) {

                                        }
                                    });
                        } else {
                            callback.onBitmap(songIcon);
                        }
                    } catch (Exception e) {
                        callback.onBitmap(songIcon);
                    }
                }
        );
        return null;
    }
}
