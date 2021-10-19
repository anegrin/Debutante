
package io.github.debutante;

import static android.content.Context.RECEIVER_EXPORTED;

import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.transition.Transition;
import androidx.transition.TransitionInflater;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.github.debutante.databinding.FragmentPlaylistBinding;
import io.github.debutante.helper.BindingHelper;
import io.github.debutante.helper.DeviceHelper;
import io.github.debutante.helper.Obj;
import io.github.debutante.helper.PlayerWrapper;
import io.github.debutante.receivers.ChangeMediaItemBroadcastReceiver;

public class PlaylistFragment extends BaseFragment {

    private ChangeMediaItemBroadcastReceiver changeMediaItemBroadcastReceiver;
    private String currentMediaId;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Transition transition = TransitionInflater.from(requireContext()).inflateTransition(R.transition.slide_top);
        setEnterTransition(transition);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        FragmentPlaylistBinding binding = BindingHelper.bindAndInflate(inflater, container, FragmentPlaylistBinding::inflate);

        changeMediaItemBroadcastReceiver = new ChangeMediaItemBroadcastReceiver(d().repository(), d()::picasso, s -> currentMediaId = s);

        PlayerWrapper playerWrapper = d().playerWrapper();

        Player player = playerWrapper.player();

        MediaItem currentMediaItem = player.getCurrentMediaItem();
        currentMediaId = currentMediaItem != null ? currentMediaItem.mediaId : null;

        ArrayAdapter<MediaItem> mediaItemsAdapter = new ArrayAdapter<MediaItem>(requireContext(), android.R.layout.simple_list_item_2, android.R.id.text1,
                IntStream.range(0, player.getMediaItemCount())
                        .mapToObj(player::getMediaItemAt)
                        .collect(Collectors.toList())
        ) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                return Obj.tap(super.getView(position, convertView, parent), v -> {
                            MediaItem item = getItem(position);

                            TextView tv1 = v.findViewById(android.R.id.text1);
                            tv1.setText(item.mediaMetadata.title);
                            TextView tv2 = v.findViewById(android.R.id.text2);
                            tv2.setText(item.mediaMetadata.description);
                            if (item.mediaId.equals(currentMediaId)) {
                                tv1.setTypeface(Typeface.DEFAULT_BOLD);
                                tv2.setTypeface(Typeface.DEFAULT_BOLD);
                            } else {
                                tv1.setTypeface(Typeface.DEFAULT);
                                tv2.setTypeface(Typeface.DEFAULT);
                            }

                        }
                );
            }
        };

        binding.lvPlayitems.setAdapter(mediaItemsAdapter);

        binding.lvPlayitems.setOnItemClickListener((parent, view, position, id) -> {
            player.seekTo(position, C.TIME_UNSET);
            requireActivity().onBackPressed();
        });

        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();

        ActionBar supportActionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(true);
        }

        requireActivity().registerReceiver(changeMediaItemBroadcastReceiver, new IntentFilter(ChangeMediaItemBroadcastReceiver.ACTION), DeviceHelper.doNotRequireReceiverFlags() ? 0 : RECEIVER_EXPORTED);
    }

    @Override
    public void onPause() {
        super.onPause();
        requireActivity().unregisterReceiver(changeMediaItemBroadcastReceiver);
    }
}
