package io.github.debutante;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.function.IntConsumer;

import io.github.debutante.adapter.MediaItemAdapter;
import io.github.debutante.databinding.FragmentArtistsBinding;
import io.github.debutante.helper.EntityHelper;
import io.github.debutante.receivers.SyncAccountBroadcastReceiver;

public class ArtistsFragment extends BrowsingFragment<FragmentArtistsBinding> {

    public static final String ARTISTS_KEY = AccountActivity.class.getSimpleName() + "-ARTISTS_KEY";

    public ArtistsFragment() {
        super(ARTISTS_KEY, R.id.action_f_artists_to_f_albums, FragmentArtistsBinding::inflate);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        binding.fabRefresh.setOnClickListener(v -> {
            EntityHelper.EntityMetadata metadata = EntityHelper.metadata(getParentMediaId());
            SyncAccountBroadcastReceiver.broadcast(requireActivity(), metadata.accountUuid);
        });

        RecyclerView.LayoutManager layoutManager = binding.rvArtists.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager) {
            ((GridLayoutManager) layoutManager).setSpanCount(d().appConfig().isArtistPicEnabled() ? 3 : 1);
            layoutManager.setItemPrefetchEnabled(d().appConfig().isArtistPicEnabled());
        }


        return view;
    }

    @Override
    @NonNull
    protected MediaItemAdapter createMediaItemAdapter(FragmentActivity owner, IntConsumer onClick) {
        return d().appConfig().isArtistPicEnabled() ? MediaItemAdapter.withArt(owner, onClick, d().repository(), d()::picasso) : super.createMediaItemAdapter(owner, onClick);
    }

    @Override
    protected RecyclerView getRecyclerView() {
        return binding.rvArtists;
    }
}
