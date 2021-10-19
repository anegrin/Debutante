package io.github.debutante;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import java.util.function.IntConsumer;

import io.github.debutante.adapter.MediaItemAdapter;
import io.github.debutante.databinding.FragmentAlbumsBinding;
import io.github.debutante.helper.EntityHelper;
import io.github.debutante.receivers.SyncAccountBroadcastReceiver;

public class AlbumsFragment extends BrowsingFragment<FragmentAlbumsBinding> {

    public static final String ALBUMS_KEY = AccountActivity.class.getSimpleName() + "-ALBUMS_KEY";

    public AlbumsFragment() {
        super(ALBUMS_KEY, R.id.action_f_albums_to_f_songs, FragmentAlbumsBinding::inflate);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        binding.fabRefresh.setOnClickListener(v -> {
            EntityHelper.EntityMetadata metadata = EntityHelper.metadata(getParentMediaId());
            SyncAccountBroadcastReceiver.broadcast(requireActivity(), metadata.accountUuid, metadata.uuid);
        });

        binding.rvAlbums.getLayoutManager().setItemPrefetchEnabled(true);

        return view;
    }

    @Override
    @NonNull
    protected MediaItemAdapter createMediaItemAdapter(FragmentActivity owner, IntConsumer onClick) {
        return MediaItemAdapter.withArt(owner, onClick, d().repository(), d()::picasso);
    }

    @Override
    protected RecyclerView getRecyclerView() {
        return binding.rvAlbums;
    }
}
