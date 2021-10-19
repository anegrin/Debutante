package io.github.debutante;

import android.os.Bundle;

public class PreferencesActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.ll_preferences, new PreferencesFragment())
                .commit();

    }

    @Override
    protected void onPause() {
        super.onPause();
        d().appConfig().refresh();
    }
}
