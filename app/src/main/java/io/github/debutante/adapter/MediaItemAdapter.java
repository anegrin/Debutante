package io.github.debutante.adapter;

import android.content.Context;
import android.support.v4.media.MediaBrowserCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.apachat.swipereveallayout.core.SwipeLayout;
import com.google.common.collect.ImmutableMap;
import com.l4digital.fastscroll.FastScroller;
import com.squareup.picasso.Picasso;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

import io.github.debutante.R;
import io.github.debutante.helper.EntityHelper;
import io.github.debutante.helper.L;
import io.github.debutante.helper.RxHelper;
import io.github.debutante.helper.SubsonicHelper;
import io.github.debutante.helper.URIHelper;
import io.github.debutante.persistence.EntityRepository;
import io.github.debutante.persistence.entities.AccountEntity;
import io.github.debutante.persistence.entities.AlbumEntity;
import io.github.debutante.persistence.entities.ArtistEntity;
import io.github.debutante.persistence.entities.BaseEntity;
import io.github.debutante.persistence.entities.SongEntity;

public class MediaItemAdapter extends RecyclerView.Adapter<MediaItemAdapter.ViewHolder> implements FastScroller.SectionIndexer {

    private static final Map<Class<? extends BaseEntity>, Integer> ART_FALLBACK_DRAWABLES = new ImmutableMap.Builder<Class<? extends BaseEntity>, Integer>()
            .put(AccountEntity.class, R.drawable.ic_account)
            .put(ArtistEntity.class, R.drawable.ic_artist)
            .put(AlbumEntity.class, R.drawable.ic_album)
            .put(SongEntity.class, R.drawable.ic_song)
            .build();

    private final ArrayList<MediaBrowserCompat.MediaItem> mediaItems;
    private final Context context;
    private final IntConsumer onClick;
    private final IntPredicate openOnLongPress;
    private final int resId;
    private final EntityRepository repository;
    private final IntConsumer onEdit;
    private final IntConsumer onDelete;
    private final Supplier<Picasso> picassoSupplier;

    private MediaItemAdapter(@NonNull Context context,
                             IntConsumer onClick,
                             IntConsumer onEdit,
                             IntConsumer onDelete,
                             IntPredicate openOnLongPress,
                             int resId,
                             EntityRepository repository,
                             Supplier<Picasso> picassoSupplier) {
        super();
        this.context = context;
        this.onClick = onClick;
        this.onEdit = onEdit;
        this.onDelete = onDelete;
        this.openOnLongPress = openOnLongPress;
        this.resId = resId;
        this.repository = repository;
        this.picassoSupplier = picassoSupplier;
        this.mediaItems = new ArrayList<>();
    }

    public static MediaItemAdapter withArt(Context context, IntConsumer onClick, EntityRepository repository, Supplier<Picasso> picassoSupplier) {
        return new MediaItemAdapter(context, onClick, null, null, null, R.layout.image_list_item, repository, picassoSupplier);
    }

    public static MediaItemAdapter withIcon(Context context, IntConsumer onClick) {
        return new MediaItemAdapter(context, onClick, null, null, null, R.layout.icon_list_item, null, null);
    }

    public static MediaItemAdapter withIcon(Context context, IntConsumer onClick, IntConsumer onEdit, IntConsumer onDelete, IntPredicate openOnLongPress) {
        return new MediaItemAdapter(context, onClick, onEdit, onDelete, openOnLongPress, R.layout.swipe_list_item, null, null);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        L.v("Creating view holder");
        View inflated = LayoutInflater.from(context).inflate(resId, parent, false);
        final View v = inflated.getId() == R.id.rv_item ? inflated : inflated.findViewById(R.id.rv_item);
        v.setOnClickListener(v1 -> onClick.accept(getPosition(inflated)));

        ImageView ivDelete = inflated.findViewById(R.id.iv_delete);
        ImageView ivEdit = inflated.findViewById(R.id.iv_edit);

        boolean withActions = false;
        if (onEdit != null && ivEdit != null) {
            L.v("Setting view holder onEdit");
            withActions = true;
            ivEdit.setOnClickListener(v2 -> onEdit.accept(getPosition(inflated)));
        }
        if (onDelete != null && ivDelete != null) {
            L.v("Setting view holder onDelete");
            withActions = true;
            ivDelete.setOnClickListener(v2 -> onDelete.accept(getPosition(inflated)));
        }

        if (withActions && inflated instanceof SwipeLayout) {
            L.v("Setting view holder openOnLongPress");
            v.setOnLongClickListener(view -> {
                boolean open = openOnLongPress.test(getPosition(inflated));
                if (open) {
                    SwipeLayout itemView = (SwipeLayout) inflated;
                    itemView.open(true);
                }
                return open;
            });
        }

        return new ViewHolder(inflated);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MediaBrowserCompat.MediaItem mediaItem = mediaItems.get(position);

        EntityHelper.EntityMetadata metadata = EntityHelper.metadata(mediaItem.getMediaId());
        if (holder.itemView instanceof SwipeLayout) {
            SwipeLayout itemView = (SwipeLayout) holder.itemView;
            itemView.setLockDrag(AccountEntity.LOCAL.uuid.equals(metadata.accountUuid) || onEdit == null || onDelete == null);
            itemView.close(false);
        }

        holder.tvTitle.setText(mediaItem.getDescription().getTitle());
        if (holder.tvDescription != null) {
            if (StringUtils.isNotBlank(mediaItem.getDescription().getDescription())) {
                holder.tvDescription.setText(mediaItem.getDescription().getDescription());
            } else {
                holder.tvDescription.setHeight(0);
            }
        }

        if (holder.ivIcon != null && mediaItem.getDescription().getIconUri() != null) {
            holder.ivIcon.setImageURI(mediaItem.getDescription().getIconUri());
        }

        if (holder.ivArt != null) {
            String coverArt = metadata.params.get(EntityHelper.EntityMetadata.COVER_ART_PARAM);
            Integer defaultImage = ART_FALLBACK_DRAWABLES.getOrDefault(metadata.type, R.drawable.ic_song);
            if (StringUtils.isNotEmpty(coverArt)) {
                RxHelper.defaultInstance().subscribe(repository.findAccountByUuid(metadata.accountUuid), a -> {
                    String coverArtUri = URIHelper.isRemote(coverArt) ? coverArt : SubsonicHelper.buildCoverArtUrl(a.url, a.username, a.token, coverArt);
                    if (StringUtils.isNotEmpty(coverArtUri)) {
                        try {
                            picassoSupplier.get()
                                    .load(coverArtUri)
                                    //.centerInside()
                                    .placeholder(defaultImage)
                                    .error(defaultImage)
                                    .into(holder.ivArt);
                        } catch (Exception e) {
                            clear(holder.ivArt, defaultImage);
                        }
                    } else {
                        clear(holder.ivArt, defaultImage);
                    }
                }, Throwable::printStackTrace);
            } else {
                clear(holder.ivArt, defaultImage);
            }
        }

        holder.setPosition(position);
    }

    private void clear(ImageView ivArt, Integer defaultImage) {
        picassoSupplier.get().cancelRequest(ivArt);
        ivArt.setImageResource(defaultImage);
    }

    @Override
    public int getItemCount() {
        return mediaItems.size();
    }

    public MediaBrowserCompat.MediaItem getItem(int position) {
        return mediaItems.get(position);
    }

    private static Integer getPosition(View view) {
        return (Integer) view.getTag();
    }

    public void clear(boolean notify) {
        mediaItems.clear();
        if (notify) {
            notifyDataSetChanged();
        }
    }

    public void addAll(List<MediaBrowserCompat.MediaItem> mediaItems) {
        this.mediaItems.addAll(mediaItems);
        notifyDataSetChanged();
    }

    @Override
    public CharSequence getSectionText(int position) {
        if (mediaItems.size() > position) {
            MediaBrowserCompat.MediaItem mediaItem = mediaItems.get(position);
            CharSequence title = mediaItem.getDescription().getTitle();
            if (title != null && title.length() > 1) {
                return String.valueOf(Character.toUpperCase(title.charAt(0)));
            }
        }

        return "";
    }

    protected static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvDescription;
        private final ImageView ivIcon;
        private final ImageView ivArt;

        public ViewHolder(View view) {
            super(view);
            tvTitle = view.findViewById(android.R.id.text1);
            tvDescription = view.findViewById(android.R.id.text2);
            ivIcon = view.findViewById(android.R.id.icon);
            ivArt = view.findViewById(R.id.iv_album_art);
        }

        private void setPosition(int position) {
            itemView.setTag(position);
        }
    }
}
