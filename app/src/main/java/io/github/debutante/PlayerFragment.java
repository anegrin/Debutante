
package io.github.debutante;

import static android.content.Context.RECEIVER_EXPORTED;

import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.transition.Transition;
import androidx.transition.TransitionInflater;

import com.google.android.exoplayer2.MediaItem;

import io.github.debutante.databinding.FragmentPlayerBinding;
import io.github.debutante.helper.BindingHelper;
import io.github.debutante.helper.DeviceHelper;
import io.github.debutante.receivers.ChangeMediaItemBroadcastReceiver;
import io.github.debutante.receivers.SwitchPlayerBroadcastReceiver;

public class PlayerFragment extends BaseFragment {

    private FragmentPlayerBinding binding;
    private SwitchPlayerBroadcastReceiver switchPlayerBroadcastReceiver;
    private ChangeMediaItemBroadcastReceiver changeMediaItemBroadcastReceiver;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        Transition transition = TransitionInflater.from(requireContext()).inflateTransition(R.transition.slide_bottom);
        setEnterTransition(transition);
        setExitTransition(transition);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = BindingHelper.bindAndInflate(inflater, container, FragmentPlayerBinding::inflate);

        binding.spvPlayer.setPlayer(((Debutante) requireActivity().getApplication()).playerWrapper().player());
        binding.spvPlayer.showController();

        if (DeviceHelper.needsStopPlayerButton()) {
            ImageButton ibStop = binding.spvPlayer.findViewById(R.id.exo_stop);
            ibStop.setVisibility(View.VISIBLE);
            ibStop.setOnClickListener(v -> {
                binding.spvPlayer.getPlayer().stop();
                d().mediaSession().setActive(false);
            });
        }

        switchPlayerBroadcastReceiver = new SwitchPlayerBroadcastReceiver(binding.spvPlayer);
        changeMediaItemBroadcastReceiver = new ChangeMediaItemBroadcastReceiver(d().repository(), d()::picasso, binding.ivAlbumArt,
                binding.tvArtist, binding.tvAlbum, binding.tvSong);

        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();

        ActionBar supportActionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (supportActionBar != null) {
            //boolean enabled = navController.getPreviousBackStackEntry() != null;
            supportActionBar.setDisplayHomeAsUpEnabled(true);
        }

        requireActivity().registerReceiver(switchPlayerBroadcastReceiver, new IntentFilter(SwitchPlayerBroadcastReceiver.ACTION), DeviceHelper.doNotRequireReceiverFlags() ? 0 : RECEIVER_EXPORTED);
        requireActivity().registerReceiver(changeMediaItemBroadcastReceiver, new IntentFilter(ChangeMediaItemBroadcastReceiver.ACTION), DeviceHelper.doNotRequireReceiverFlags() ? 0 : RECEIVER_EXPORTED);

        MediaItem currentMediaItem = d().playerWrapper().player().getCurrentMediaItem();
        if (currentMediaItem != null) {
            ChangeMediaItemBroadcastReceiver.broadcast(requireActivity(), currentMediaItem.mediaId);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        requireActivity().unregisterReceiver(switchPlayerBroadcastReceiver);
        requireActivity().unregisterReceiver(changeMediaItemBroadcastReceiver);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            return true;
        } else if (itemId == R.id.mi_playlist) {
            NavController navController = Navigation.findNavController(requireActivity(), R.id.f_player_nav);
            navController.navigate(R.id.action_f_player_to_f_playlist);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
