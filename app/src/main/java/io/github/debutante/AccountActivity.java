package io.github.debutante;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.debutante.databinding.ActivityAccountBinding;
import io.github.debutante.helper.BindingHelper;
import io.github.debutante.helper.GsonRequest;
import io.github.debutante.helper.L;
import io.github.debutante.helper.RxHelper;
import io.github.debutante.helper.SubsonicHelper;
import io.github.debutante.model.api.PingResponse;
import io.github.debutante.persistence.entities.AccountEntity;
import io.github.debutante.receivers.SyncAccountBroadcastReceiver;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;

public class AccountActivity extends BaseActivity {

    public static final String ACCOUNT_UUID_KEY = AccountActivity.class.getSimpleName() + "-ACCOUNT_UUID_KEY";

    private ActivityAccountBinding binding;
    private AccountEntity loaded;
    private Set<String> existingAliases = Collections.emptySet();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = BindingHelper.bindAndSetContent(this, ActivityAccountBinding::inflate);

        String accountUuid = getIntent().getStringExtra(ACCOUNT_UUID_KEY);

        if (StringUtils.isNotEmpty(accountUuid)) {
            L.i("Loading account " + accountUuid);
            RxHelper.defaultInstance().subscribe(d().repository().findAccountByUuid(accountUuid),
                    AccountActivity.this::setCurrentAccount,
                    t -> Toast.makeText(AccountActivity.this, getString(R.string.load_entites_failure) + "\n" + t.getMessage(), Toast.LENGTH_SHORT).show());
        } else {
            initForm(Optional.empty());
        }

    }

    private void setCurrentAccount(AccountEntity loaded) {
        L.i("Loaded account " + loaded);
        this.loaded = loaded;
        binding.etAlias.setText(loaded.alias);
        binding.etUrl.setText(loaded.url);
        binding.etUsername.setText(loaded.username);
        initForm(Optional.of(loaded));
    }

    private void initForm(Optional<AccountEntity> loaded) {
        RxHelper.defaultInstance().subscribe(d().repository().getAllAccounts(), l -> {
            existingAliases = l.stream().map(a -> a.alias).filter(s -> !s.equals(loaded.map(o -> o.alias).orElse(null))).collect(Collectors.toSet());
            binding.bTest.setOnClickListener(this::onTest);
            binding.bSave.setOnClickListener(this::onSave);
        }, t -> Toast.makeText(AccountActivity.this, getString(R.string.load_entites_failure) + "\n" + t.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void onTest(View view) {
        if (validate()) {
            String baseUrl = binding.etUrl.getText().toString();
            String username = binding.etUsername.getText().toString();
            String password = binding.etPassword.getText().toString();
            GsonRequest<PingResponse> request = SubsonicHelper.buildPingRequest(d().gson(), baseUrl, username, SubsonicHelper.token(username, password), r -> {
                if (r.subsonicResponse.isOk()) {
                    toast(getString(R.string.test_connectivity_success));
                } else {
                    toast(getString(R.string.test_connectivity_failure) + "\n" + getString(r.subsonicResponse.error.stringResId()));
                }
            }, e -> toast(getString(R.string.test_connectivity_failure) + "\n" + e.getMessage()));

            request.enqueue(d().okHttpClient());
        }
    }

    private void toast(String message) {
        AndroidSchedulers.mainThread().scheduleDirect(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    private void onSave(View view) {
        if (validate()) {
            String alias = binding.etAlias.getText().toString();
            String baseUrl = binding.etUrl.getText().toString();
            String username = binding.etUsername.getText().toString();
            String token = SubsonicHelper.token(username, binding.etPassword.getText().toString());

            boolean updating = loaded != null;
            AccountEntity accountEntity = updating ? new AccountEntity(loaded.uuid, alias, baseUrl, username, token) : new AccountEntity(alias, baseUrl, username, token);
            Completable completable = updating ? d().repository().updateAccount(accountEntity) : d().repository().insertAccount(accountEntity);

            RxHelper rxHelper = RxHelper.defaultInstance();
            rxHelper.subscribe(completable, () -> {
                L.i("Account " + (updating ? "updated" : "created") + ": " + accountEntity);
                Toast.makeText(this, R.string.save_account_success, Toast.LENGTH_SHORT).show();
                SyncAccountBroadcastReceiver.broadcast(AccountActivity.this, accountEntity);
                rxHelper.subscribe(d().repository().getAllAccounts(), l -> {
                    if (l.stream().anyMatch(a -> AccountEntity.LOCAL.uuid().equals(a.uuid()))) {
                        finish();
                    } else {
                        L.i("Local account is missing, creating it");
                        RxHelper.defaultInstance().subscribe(d().repository().insertAccount(AccountEntity.LOCAL), this::finish, e -> finish());
                    }
                }, Throwable::printStackTrace);

            }, t -> Toast.makeText(AccountActivity.this, getString(R.string.save_account_failure) + "\n" + t.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private boolean validate() {
        for (TextView tv : new EditText[]{binding.etAlias, binding.etUrl, binding.etUsername, binding.etPassword}) {

            if (StringUtils.isBlank(tv.getText())) {
                tv.requestFocus();
                tv.setError(getString(R.string.tv_error_required_field));
                return false;
            }
        }

        if (existingAliases.contains(binding.etAlias.getText().toString())) {
            binding.etAlias.requestFocus();
            binding.etAlias.setError(getString(R.string.tv_error_duplicated_alias));
            return false;
        }

        CharSequence url = binding.etUrl.getText();
        try {
            new URL(url.toString());
        } catch (MalformedURLException e) {
            binding.etUrl.requestFocus();
            binding.etUrl.setError(getString(R.string.tv_error_invalid_url));
            return false;
        }

        return true;
    }
}
