package io.github.debutante.adapter;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.stream.Collectors;

public class LicenseAdapter extends ArrayAdapter<String> {

    private final SortedMap<String, SortedMap<String, List<String>>> lic2lib2dep;

    public LicenseAdapter(@NonNull Context context, SortedMap<String, SortedMap<String, List<String>>> lic2lib2dep) {
        super(context, android.R.layout.simple_list_item_2, android.R.id.text1, new ArrayList<>(lic2lib2dep.keySet()));
        this.lic2lib2dep = lic2lib2dep;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = super.getView(position, convertView, parent);

        ((TextView)view.findViewById(android.R.id.text1)).setTypeface(Typeface.DEFAULT_BOLD);

        String link = getItem(position);

        TextView text2 = view.findViewById(android.R.id.text2);
        //noinspection ConstantConditions,SimplifyStreamApiCallChains
        text2.setText(lic2lib2dep.getOrDefault(link, Collections.emptySortedMap())
                .entrySet()
                .stream()
                .map(e -> e.getKey() + " (" + e.getValue().stream().collect(Collectors.joining(", ")) + ")")
                .collect(Collectors.joining("\n")));

        return view;
    }
}
