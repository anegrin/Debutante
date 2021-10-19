package io.github.debutante;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

import io.github.debutante.adapter.MediaItemAdapter;
import io.github.debutante.databinding.FragmentAccountsBinding;
import io.github.debutante.helper.EntityHelper;
import io.github.debutante.helper.L;
import io.github.debutante.helper.RxHelper;
import io.github.debutante.persistence.EntityRepository;
import io.github.debutante.persistence.PlayerState;
import io.github.debutante.persistence.entities.AccountEntity;
import io.github.debutante.receivers.BrowseMediaBroadcastReceiver;
import io.reactivex.rxjava3.core.Completable;

public class AccountsFragment extends BrowsingFragment<FragmentAccountsBinding> {

    public static final String ACCOUNTS_KEY = AccountActivity.class.getSimpleName() + "-ACCOUNTS_KEY";

    public AccountsFragment() {
        super(ACCOUNTS_KEY, R.id.action_f_accounts_to_f_artists, FragmentAccountsBinding::inflate);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        binding.fabAddAccount.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AccountActivity.class);
            requireActivity().startActivity(intent);
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        BrowseMediaBroadcastReceiver.broadcast(requireActivity(), null);
    }

    @NonNull
    protected MediaItemAdapter createMediaItemAdapter(FragmentActivity owner, IntConsumer onClick) {
        IntConsumer onEdit = position -> {
            MediaBrowserCompat.MediaItem item = mediaItemAdapter.getItem(position);
            EntityHelper.EntityMetadata metadata = EntityHelper.metadata(item.getMediaId());
            if (!AccountEntity.LOCAL.uuid().equals(metadata.uuid)) {
                Intent intent = new Intent(getContext(), AccountActivity.class);
                intent.putExtra(AccountActivity.ACCOUNT_UUID_KEY, metadata.uuid);
                this.startActivity(intent);

            }
        };
        IntConsumer onDelete = position -> {
            MediaBrowserCompat.MediaItem item = mediaItemAdapter.getItem(position);
            EntityHelper.EntityMetadata metadata = EntityHelper.metadata(item.getMediaId());
            if (!AccountEntity.LOCAL.uuid().equals(metadata.uuid)) {
                deleteAccount(metadata.uuid);
            }
        };

        IntPredicate openOnLongPress = position -> {
            MediaBrowserCompat.MediaItem item = mediaItemAdapter.getItem(position);
            EntityHelper.EntityMetadata metadata = EntityHelper.metadata(item.getMediaId());
            return !AccountEntity.LOCAL.uuid().equals(metadata.uuid);
        };

        return MediaItemAdapter.withIcon(owner, onClick, onEdit, onDelete, openOnLongPress);
    }

    @Override
    protected RecyclerView getRecyclerView() {
        return binding.rvAccounts;
    }


    private void deleteAccount(String accountUuid) {

        L.i("Deleting account " + accountUuid);

        EntityRepository r = d().repository();

        PlayerState.clear(requireContext(), accountUuid);

        RxHelper.defaultInstance().subscribe(Completable.concatArray(r.deleteAccountByUuid(accountUuid),
                r.deleteAllArtistsByAccountUuid(accountUuid),
                r.deleteAllAlbumsByAccountUuid(accountUuid),
                r.deleteAllSongsByAccountUuid(accountUuid)), () -> {
            mediaItemAdapter.clear(true);
            BrowseMediaBroadcastReceiver.broadcast(requireContext(), null);
        }, Throwable::printStackTrace);
    }
}
