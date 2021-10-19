
package io.github.debutante;

import static android.content.Context.RECEIVER_EXPORTED;

import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.debutante.databinding.FragmentPlayerinoBinding;
import io.github.debutante.helper.BindingHelper;
import io.github.debutante.helper.DeviceHelper;
import io.github.debutante.helper.RxHelper;
import io.github.debutante.receivers.ChangeIsPlayingBroadcastReceiver;
import io.github.debutante.receivers.ChangeMediaItemBroadcastReceiver;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class PlayerinoFragment extends BaseFragment {

    private FragmentPlayerinoBinding binding;
    private ChangeMediaItemBroadcastReceiver changeMediaItemBroadcastReceiver;
    private ChangeIsPlayingBroadcastReceiver changeIsPlayingBroadcastReceiver;
    public Observable<Long> playbackProgressObservable = Observable.interval(1, TimeUnit.SECONDS, Schedulers.from(Executors.newSingleThreadExecutor(RxHelper.LOW_PRI_THREAD_FACTORY)));
    private Disposable playbackProgressDisposable;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = BindingHelper.bindAndInflate(inflater, container, FragmentPlayerinoBinding::inflate);

        changeMediaItemBroadcastReceiver = new ChangeMediaItemBroadcastReceiver(d().repository(), d()::picasso, binding.ivAlbumArt, null, null, binding.tvSong);
        changeIsPlayingBroadcastReceiver = new ChangeIsPlayingBroadcastReceiver(binding.ibPlayPause);
        binding.ibPlayPause.setOnClickListener(this::playPause);

        binding.getRoot().setOnClickListener(v -> {
            NavController navController = Navigation.findNavController(requireActivity(), R.id.f_player_nav);
            navController.navigate(R.id.action_f_playerino_to_f_player);
        });


        return binding.getRoot();
    }

    private void playPause(View view) {
        Player player = d().playerWrapper().player();
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        NavHostFragment navHostFragment = (NavHostFragment) requireActivity().getSupportFragmentManager().findFragmentById(R.id.f_nav);
        NavController navController = navHostFragment.getNavController();

        ActionBar supportActionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (supportActionBar != null) {
            boolean enabled = navController.getPreviousBackStackEntry() != null;
            supportActionBar.setDisplayHomeAsUpEnabled(enabled);
        }

        requireActivity().registerReceiver(changeMediaItemBroadcastReceiver, new IntentFilter(ChangeMediaItemBroadcastReceiver.ACTION), DeviceHelper.doNotRequireReceiverFlags() ? 0 : RECEIVER_EXPORTED);
        requireActivity().registerReceiver(changeIsPlayingBroadcastReceiver, new IntentFilter(ChangeIsPlayingBroadcastReceiver.ACTION), DeviceHelper.doNotRequireReceiverFlags() ? 0 : RECEIVER_EXPORTED);

        final Player player = d().playerWrapper().player();
        MediaItem currentMediaItem = player.getCurrentMediaItem();
        if (currentMediaItem != null) {
            ChangeMediaItemBroadcastReceiver.broadcast(requireActivity(), currentMediaItem.mediaId);
        }
        binding.ibPlayPause.setImageResource(player.isPlaying() ? R.drawable.exo_styled_controls_pause : R.drawable.exo_styled_controls_play);

        updateProgress(player);
        playbackProgressDisposable = playbackProgressObservable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(progress -> updateProgress(player));
    }

    private void updateProgress(Player player) {
        if (player.isPlaying()) {
            long duration = player.getDuration();

            if (duration != C.TIME_UNSET) {
                long currentPosition = player.getCurrentPosition();
                updateProgress(currentPosition * 100d / duration);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        playbackProgressDisposable.dispose();
        requireActivity().unregisterReceiver(changeMediaItemBroadcastReceiver);
        requireActivity().unregisterReceiver(changeIsPlayingBroadcastReceiver);
    }

    private void updateProgress(double percent) {
        ViewGroup.LayoutParams layoutParams = binding.vProgress.getLayoutParams();
        int oneHundredPercent = requireActivity().getWindow().getDecorView().getWidth();
        layoutParams.width = (int) ((oneHundredPercent / 100) * percent);
        binding.vProgress.setLayoutParams(layoutParams);
    }
}
