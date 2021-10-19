
package io.github.debutante;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import io.github.debutante.databinding.FragmentSongsBinding;

public class SongsFragment extends BrowsingFragment<FragmentSongsBinding> {

    public static final String SONGS_KEY = AccountActivity.class.getSimpleName() + "-SONGS_KEY";

    public SongsFragment() {
        super(SONGS_KEY, -1, FragmentSongsBinding::inflate);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    protected RecyclerView getRecyclerView() {
        return binding.rvSongs;
    }

}
