package io.github.debutante;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import io.github.debutante.adapter.LicenseAdapter;
import io.github.debutante.helper.L;
import io.github.debutante.helper.Obj;

public class OSSActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oss);
        ListView lvLicenses = findViewById(R.id.lv_licenses);

        try (InputStream is = getAssets().open("open_source_licenses.json");
             Reader reader = new InputStreamReader(is)) {
            Library[] libraries = d().gson().fromJson(reader, Library[].class);

            SortedMap<String, SortedMap<String, List<String>>> lic2lib2dep = Arrays.stream(libraries)
                    .collect(Collectors.groupingBy(library -> library.licenses
                            .stream()
                            .findFirst()
                            .map(l -> l.license_url)
                            .orElse("UNKNOWN"))
                    )
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            e1 -> e1.getValue()
                                    .stream()
                                    .collect(Collectors.groupingBy(l1 -> l1.project))
                                    .entrySet()
                                    .stream()
                                    .collect(Collectors.toMap(Map.Entry::getKey, e2 -> e2.getValue()
                                            .stream()
                                            .map(l2 -> noVer(l2.dependency))
                                            .sorted()
                                            .collect(Collectors.toList()), (v1, v2) -> v1, TreeMap::new)),
                            (v1, v2) -> v1,
                            TreeMap::new
                            )
                    );

            add(lic2lib2dep, "http://www.apache.org/licenses/LICENSE-2.0.txt", "extension-flac", "com.google.android.exoplayer:extension-flac");
            add(lic2lib2dep, "https://xiph.org/flac/license.html", "libflac", "com.google.android.exoplayer:extension-flac");
            add(lic2lib2dep, "http://www.apache.org/licenses/LICENSE-2.0.txt", "extension-opus", "com.google.android.exoplayer:extension-opus");
            add(lic2lib2dep, "https://opus-codec.org/license/", "libopus", "com.google.android.exoplayer:extension-opus");

            LicenseAdapter adapter = new LicenseAdapter(this, lic2lib2dep);

            lvLicenses.setAdapter(adapter);

            lvLicenses.setOnItemClickListener(this::onItemClick);

        } catch (IOException ioe) {
            onLoadingError();
        }
    }

    private void add(SortedMap<String, SortedMap<String, List<String>>> lic2lib2dep, String lic, String lib, String dep) {
        lic2lib2dep.computeIfAbsent(lic, k -> new TreeMap<>())
                .computeIfAbsent(lib, k -> new ArrayList<>())
                .add(dep);
    }

    private String noVer(String dependency) {
        if (dependency.matches(".+:.+:.+")) {
            return Obj.with(dependency.split(":"), a -> a[0] + ":" + a[1]);
        }

        return dependency;
    }

    private void onLoadingError() {
        L.e("Can't load OSS licenses");
    }

    @SuppressWarnings("unused")
    private void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        String link = adapterView.getAdapter().getItem(position).toString();
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
    }

    private static class Library {
        public String project;
        public String dependency;
        public List<License> licenses;

        private static class License {
            public String license_url;
        }
    }
}
