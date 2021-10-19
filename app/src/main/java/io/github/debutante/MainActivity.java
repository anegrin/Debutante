package io.github.debutante;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.view.Menu;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.gms.cast.framework.CastButtonFactory;

import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.debutante.databinding.ActivityMainBinding;
import io.github.debutante.helper.BindingHelper;
import io.github.debutante.helper.DeviceHelper;
import io.github.debutante.helper.EntityHelper;
import io.github.debutante.helper.PlayerWrapper;
import io.github.debutante.helper.RxHelper;
import io.github.debutante.helper.URIHelper;
import io.github.debutante.model.AppConfig;
import io.github.debutante.model.PlayMediaViewModel;
import io.github.debutante.persistence.PlayerState;
import io.github.debutante.persistence.entities.AccountEntity;
import io.github.debutante.receivers.BrowseMediaBroadcastReceiver;
import io.github.debutante.receivers.CastMenuItemBroadcastReceiver;
import io.github.debutante.receivers.PlayMediaBroadcastReceiver;

public class MainActivity extends BaseActivity {

    private static final String PLAY_VIEW_MODEL_KEY = MainActivity.class.getSimpleName() + "-PLAY_VIEW_MODEL_KEY";
    public static final String OPEN_PLAYER_KEY = MainActivity.class.getSimpleName() + "-OPEN_PLAYER_KEY";
    private static final Map<Integer, Boolean> MENU_ITEMS_STATES = new HashMap<>();

    private ActivityMainBinding binding;
    private BroadcastReceiver browseMediaBroadcastReceiver;
    private PlayMediaBroadcastReceiver playMediaBroadcastReceiver;
    private CastMenuItemBroadcastReceiver castMenuItemBroadcastReceiver;
    private NavController mainNavController;
    private NavController playerNavController;

    public MainActivity() {
        super(false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = BindingHelper.bindAndSetContent(this, ActivityMainBinding::inflate);
        binding.bCreateAccount.setOnClickListener(this::onCreate);
        binding.bLocalOnly.setOnClickListener(this::onLocalOnlyCreate);

        ActivityResultLauncher<String> activityResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (!granted) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.app_name)
                        .setMessage(String.format(getString(R.string.require_post_notification), getString(R.string.app_name)))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });

        boolean granted = (DeviceHelper.doNotRequirePostNotificationPermission() || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
        if (!granted) {
            activityResultLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

    }

    @Override
    public void onResume() {
        super.onResume();

        d().playerWrapper().setOptionalOffloadSchedulingEnabled(true);

        ViewModelProvider viewModelProvider = new ViewModelProvider(this);
        PlayMediaViewModel playMediaViewModel = viewModelProvider.get(PLAY_VIEW_MODEL_KEY, PlayMediaViewModel.class);
        playMediaBroadcastReceiver = new PlayMediaBroadcastReceiver(playMediaViewModel, d().repository());
        registerReceiver(playMediaBroadcastReceiver, new IntentFilter(PlayMediaBroadcastReceiver.ACTION), DeviceHelper.doNotRequireReceiverFlags() ? 0 : RECEIVER_EXPORTED);

        browseMediaBroadcastReceiver = new BrowseMediaBroadcastReceiver(viewModelProvider, d().repository());
        registerReceiver(browseMediaBroadcastReceiver, new IntentFilter(BrowseMediaBroadcastReceiver.ACTION), DeviceHelper.doNotRequireReceiverFlags() ? 0 : RECEIVER_EXPORTED);

        castMenuItemBroadcastReceiver = new CastMenuItemBroadcastReceiver(b -> {
            MENU_ITEMS_STATES.put(R.id.mi_cast, b);
            invalidateOptionsMenu();
        });
        registerReceiver(castMenuItemBroadcastReceiver, new IntentFilter(CastMenuItemBroadcastReceiver.ACTION), DeviceHelper.doNotRequireReceiverFlags() ? 0 : RECEIVER_EXPORTED);

        NavController.OnDestinationChangedListener destinationChangedListener = new DestinationChangedListener();

        mainNavController = Navigation.findNavController(this, R.id.f_nav);
        mainNavController.addOnDestinationChangedListener(destinationChangedListener);
        playerNavController = Navigation.findNavController(this, R.id.f_player_nav);
        playerNavController.addOnDestinationChangedListener(destinationChangedListener);

        ViewTreeObserver viewTreeObserver = binding.llPlayer.getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(() -> {
            if (binding.llPlayer.getVisibility() == View.VISIBLE && binding.llBrowsing.getPaddingBottom() == 0) {
                View v = binding.getRoot().findViewById(R.id.rl_playerino);
                if (v != null) {
                    binding.llBrowsing.setPadding(0, 0, 0, v.getHeight());
                }
            }
        });

        MutableLiveData<PlayMediaViewModel.Result> playLiveData = playMediaViewModel.get();
        if (!playLiveData.hasActiveObservers()) {

            Observer<PlayMediaViewModel.Result> observer = r -> enqueueAndPlay(r.parentMediaItem, r.mediaItems, r.mediaItemId);
            playLiveData.observe(this, observer);
        }

        RxHelper.defaultInstance().subscribe(d().repository().hasAccounts(), b -> {
            if (b) {
                onAccounts();
            } else {
                onNoAccounts();
            }
        }, this::toastLoadFailure);
    }

    @Override
    protected void onPause() {
        super.onPause();
        d().playerWrapper().setOptionalOffloadSchedulingEnabled(false);
        unregisterReceiver(playMediaBroadcastReceiver);
        unregisterReceiver(browseMediaBroadcastReceiver);
        unregisterReceiver(castMenuItemBroadcastReceiver);
    }

    @Override
    public void onBackPressed() {

        int playerNavControllerId = playerNavController.getCurrentBackStackEntry().getDestination().getId();
        int mainNavControllerId = mainNavController.getCurrentBackStackEntry().getDestination().getId();
        if (playerNavControllerId == R.id.f_player || playerNavControllerId == R.id.f_playlist) {
            playerNavController.popBackStack();
        } else if (mainNavControllerId == R.id.f_accounts) {
            super.onBackPressed();
        } else {
            boolean popped = mainNavController.popBackStack();
            if (!popped) {
                super.onBackPressed();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(),
                menu,
                R.id.mi_cast);

        for (Map.Entry<Integer, Boolean> entry : MENU_ITEMS_STATES.entrySet()) {
            menu.findItem(entry.getKey()).setVisible(entry.getValue());
        }

        return true;
    }

    private void toastLoadFailure(Throwable t) {
        Toast.makeText(MainActivity.this, getString(R.string.load_entites_failure) + "\n" + t.getMessage(), Toast.LENGTH_SHORT).show();
    }

    private void onAccounts() {
        binding.llWelcome.setVisibility(View.GONE);
        binding.llBrowsing.setVisibility(View.VISIBLE);

        if (!d().playerWrapper().player().isPlaying() && !d().playerWrapper().isCasting()) {
            Optional<Pair<MediaBrowserCompat.MediaItem, List<MediaBrowserCompat.MediaItem>>> mediaItems = PlayerState.loadMediaItems(this, Optional.empty());
            Optional<String> currentMediaItemId = PlayerState.loadCurrentMediaItemId(this, Optional.empty());
            mediaItems.ifPresent(p -> enqueue(p.getKey(), p.getValue(), currentMediaItemId.orElse(null)));
        } else {
            handlePlayerIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handlePlayerIntent(intent);
    }

    private void handlePlayerIntent(Intent intent) {

        PlayerWrapper playerWrapper = d().playerWrapper();
        if (playerWrapper.player().getMediaItemCount() > 0 || playerWrapper.isCasting()) {
            binding.llPlayer.setVisibility(View.VISIBLE);
        } else {
            binding.llPlayer.setVisibility(View.GONE);
        }

        boolean openPlayer = intent.getBooleanExtra(OPEN_PLAYER_KEY, false);

        if (openPlayer) {
            if (playerNavController != null && playerNavController.getCurrentBackStackEntry().getDestination().getId() != R.id.f_player) {
                playerNavController.popBackStack(R.id.f_playerino, false);
                playerNavController.navigate(R.id.action_f_playerino_to_f_player);
            }

        }
    }

    private void onNoAccounts() {
        binding.llWelcome.setVisibility(View.VISIBLE);
        binding.llBrowsing.setVisibility(View.GONE);
    }

    private void onCreate(View view) {
        Intent intent = new Intent(this, AccountActivity.class);
        this.startActivity(intent);
    }

    private void onLocalOnlyCreate(View view) {
        RxHelper.defaultInstance().subscribe(d().repository().insertAccount(AccountEntity.LOCAL), () -> {
            BrowseMediaBroadcastReceiver.broadcast(this, null);
            onAccounts();
        }, e -> finish());
    }

    private void enqueue(MediaBrowserCompat.MediaItem parentMediaItem, List<MediaBrowserCompat.MediaItem> mediaItems, String mediaItemId) {

        Optional<Pair<String, Long>> mediaIdAndPosition = PlayerState.loadCurrentMediaItemPosition(this);

        Long position = mediaIdAndPosition.filter(kv -> kv.getKey().equals(mediaItemId)).map(Pair::getValue).orElse(C.TIME_UNSET);

        d().playerWrapper().newPlayerPreparer().prepare(parentMediaItem, mediaItems, mediaItemId, position, () -> binding.llPlayer.setVisibility(View.VISIBLE), this::toastLoadFailure, false, false);
    }

    private void enqueueAndPlay(MediaBrowserCompat.MediaItem parentMediaItem, List<MediaBrowserCompat.MediaItem> mediaItems, String mediaItemId) {
        d().playerWrapper().newPlayerPreparer().prepareAndPlay(parentMediaItem, mediaItems, mediaItemId, () -> binding.llPlayer.setVisibility(View.VISIBLE), this::toastLoadFailure);
    }

    private final class DestinationChangedListener implements NavController.OnDestinationChangedListener {
        @Override
        public void onDestinationChanged(@NonNull NavController controller, @NonNull NavDestination destination, @Nullable Bundle arguments) {

            NavBackStackEntry currentBackStackEntry = controller.getCurrentBackStackEntry();
            Optional<String> mediaTitle = Optional.ofNullable(arguments != null ? arguments.getString(BrowseMediaBroadcastReceiver.MEDIA_TITLE_KEY) : null);

            if (currentBackStackEntry != null) {
                AppConfig appConfig = d().appConfig();
                int destinationId = currentBackStackEntry.getDestination().getId();

                String mediaId = arguments != null ? arguments.getString(BrowseMediaBroadcastReceiver.MEDIA_ID_KEY) : null;
                boolean local = mediaId != null && AccountEntity.LOCAL.uuid().equals(EntityHelper.metadata(mediaId).accountUuid);

                boolean cast = !local || appConfig.isCastLocalEnabled();

                if (destinationId == R.id.f_playerino) {
                    NavBackStackEntry currentMainBackStackEntry = mainNavController.getCurrentBackStackEntry();
                    if (currentMainBackStackEntry != null) {
                        onDestinationChanged(mainNavController, currentMainBackStackEntry.getDestination(), currentMainBackStackEntry.getArguments());
                    }
                } else if (destinationId == R.id.f_player) {

                    MediaItem currentMediaItem = d().playerWrapper().player().getCurrentMediaItem();

                    if (currentMediaItem != null && !URIHelper.isRemote(currentMediaItem.mediaMetadata.extras.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI))) {
                        local = true;
                    }

                    cast = !local || appConfig.isCastLocalEnabled();
                    updateActionBar(cast, false, true, true, mediaTitle);
                } else if (destinationId == R.id.f_accounts) {
                    updateActionBar(appConfig.isCastLocalEnabled() || !appConfig.isAccountsLocalEnabled(), false, false, true, mediaTitle);
                } else if (destinationId == R.id.f_artists) {
                    updateActionBar(cast, false, false, true, mediaTitle);
                } else if (destinationId == R.id.f_ablums) {
                    updateActionBar(cast, true, false, true, mediaTitle);
                } else if (destinationId == R.id.f_songs) {
                    updateActionBar(cast, true, false, true, mediaTitle);
                } else {
                    updateActionBar(cast, false, false, false, mediaTitle);
                }
            }
        }

        private void updateActionBar(boolean cast, boolean play, boolean playlist, boolean settings, Optional<String> mediaTitle) {

            getSupportActionBar().setSubtitle(mediaTitle.orElse(null));

            MENU_ITEMS_STATES.put(R.id.mi_cast, cast);
            MENU_ITEMS_STATES.put(R.id.mi_play, play);
            MENU_ITEMS_STATES.put(R.id.mi_playlist, playlist);
            MENU_ITEMS_STATES.put(R.id.mi_settings, settings);
            invalidateOptionsMenu();
        }
    }
}
