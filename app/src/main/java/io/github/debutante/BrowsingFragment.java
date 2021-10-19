package io.github.debutante;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.TriFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import io.github.debutante.adapter.MediaItemAdapter;
import io.github.debutante.adapter.MediaStoreAdapter;
import io.github.debutante.helper.BindingHelper;
import io.github.debutante.helper.DeviceHelper;
import io.github.debutante.helper.EntityHelper;
import io.github.debutante.helper.MediaBrowserHelper;
import io.github.debutante.helper.Obj;
import io.github.debutante.model.BrowseMediaViewModel;
import io.github.debutante.persistence.entities.AccountEntity;
import io.github.debutante.receivers.BrowseMediaBroadcastReceiver;
import io.github.debutante.receivers.PlayMediaBroadcastReceiver;
import io.github.debutante.service.MediaService;

public abstract class BrowsingFragment<B extends ViewBinding> extends BaseFragment {

    private final TriFunction<LayoutInflater, ViewGroup, Boolean, B> inflate;
    private final String viewModelKey;
    private final int nextActionId;
    protected B binding;
    protected MediaItemAdapter mediaItemAdapter;
    private NavController navController;
    private ActivityResultLauncher<String> activityResultLauncher;
    private String currentSessionId;
    private BrowseMediaViewModel browseMediaViewModel;

    public BrowsingFragment(String viewModelKey, int nextActionId, TriFunction<LayoutInflater, ViewGroup, Boolean, B> inflate) {
        this.viewModelKey = viewModelKey;
        this.nextActionId = nextActionId;
        this.inflate = inflate;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        activityResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                Toast.makeText(requireContext(), R.string.local_access_granted, Toast.LENGTH_SHORT).show();
                Bundle bundle = new Bundle();
                bundle.putString(BrowseMediaBroadcastReceiver.MEDIA_ID_KEY, EntityHelper.mediaId(AccountEntity.LOCAL));
                bundle.putString(BrowseMediaBroadcastReceiver.MEDIA_TITLE_KEY, AccountEntity.LOCAL.alias);
                navController.navigate(R.id.action_f_accounts_to_f_artists, bundle);
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = BindingHelper.bindAndInflate(inflater, container, inflate);

        View root = binding.getRoot();

        NavHostFragment navHostFragment = (NavHostFragment) requireActivity().getSupportFragmentManager().findFragmentById(R.id.f_nav);
        navController = navHostFragment.getNavController();

        FragmentActivity owner = requireActivity();
        browseMediaViewModel = new ViewModelProvider(owner).get(viewModelKey, BrowseMediaViewModel.class);
        IntConsumer onClick = position -> {
            MediaBrowserCompat.MediaItem item = mediaItemAdapter.getItem(position);
            if (nextActionId > -1) {

                Bundle bundle = new Bundle();
                bundle.putString(BrowseMediaBroadcastReceiver.MEDIA_ID_KEY, item.getMediaId());
                bundle.putString(BrowseMediaBroadcastReceiver.MEDIA_TITLE_KEY, item.getDescription().getTitle().toString());
                if (MediaBrowserHelper.isNotLocalAccount(item)) {
                    navController.navigate(nextActionId, bundle);
                } else {
                    String permission = DeviceHelper.requireSpecificReadAudioPermissions() ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
                    int checkResult = requireActivity().checkSelfPermission(permission);
                    if (checkResult == PackageManager.PERMISSION_GRANTED) {
                        navController.navigate(nextActionId, bundle);
                    } else {
                        activityResultLauncher.launch(permission);
                    }
                }
            } else {
                doPlay(item.getMediaId());
            }
        };
        mediaItemAdapter = createMediaItemAdapter(owner, onClick);
        Obj.tap(getRecyclerView(), rv -> {
            rv.setHasFixedSize(true);
            rv.setAdapter(mediaItemAdapter);
        });

        mediaItemAdapter.clear(true);

        MutableLiveData<BrowseMediaViewModel.Result> liveData = browseMediaViewModel.get();
        if (!liveData.hasActiveObservers()) {

            Observer<BrowseMediaViewModel.Result> observer = r -> {
                mediaItemAdapter.clear(false);
                if (!d().appConfig().isAccountsLocalEnabled()) {
                    mediaItemAdapter.addAll(r.mediaItems.stream().filter(MediaBrowserHelper::isNotLocalAccount).collect(Collectors.toList()));
                } else {
                    mediaItemAdapter.addAll(r.mediaItems);
                }

                View fabRefresh = root.findViewById(R.id.fab_refresh);

                if (fabRefresh != null && r.parentMediaId != null) {
                    EntityHelper.EntityMetadata metadata = EntityHelper.metadata(r.parentMediaId);
                    fabRefresh.setVisibility(MediaStoreAdapter.isLocal(metadata.uuid) ? View.GONE : View.VISIBLE);
                }

            };
            liveData.observe(getViewLifecycleOwner(), observer);
        }

        BrowseMediaBroadcastReceiver.broadcast(requireActivity(), getParentMediaId());

        return root;
    }

    @NonNull
    protected MediaItemAdapter createMediaItemAdapter(FragmentActivity owner, IntConsumer onClick) {
        return MediaItemAdapter.withIcon(owner, onClick);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();

        String previousSessionId = currentSessionId;

        currentSessionId = MediaService.currentSessionId();

        if (browseMediaViewModel != null && !StringUtils.equals(previousSessionId, currentSessionId)) {
            Optional.ofNullable(browseMediaViewModel.get())
                    .map(MutableLiveData::getValue)
                    .ifPresent(r -> browseMediaViewModel.put(r.parentMediaId, new ArrayList<>(r.mediaItems)));
        }

        ActionBar supportActionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (supportActionBar != null) {
            boolean enabled = navController.getPreviousBackStackEntry() != null;
            supportActionBar.setDisplayHomeAsUpEnabled(enabled);
        }

        //getRecyclerView().invalidateItemDecorations();
    }

    @Nullable
    protected String getParentMediaId() {
        Bundle arguments = getArguments();
        return arguments != null ? EntityHelper.mediaId(arguments.getString(BrowseMediaBroadcastReceiver.MEDIA_ID_KEY), Collections.singletonMap(MediaBrowserHelper.PREPEND_ACTIONS, false)) : null;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.mi_play) {
            doPlay();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void doPlay(String mediaId) {
        PlayMediaBroadcastReceiver.broadcast(requireActivity(), getParentMediaId(), mediaId);
        NavController navController = Navigation.findNavController(requireActivity(), R.id.f_player_nav);
        navController.navigate(R.id.action_f_playerino_to_f_player);
    }

    private void doPlay() {
        doPlay(null);
    }

    protected abstract RecyclerView getRecyclerView();
}
