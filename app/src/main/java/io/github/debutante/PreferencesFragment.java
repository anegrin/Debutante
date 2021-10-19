package io.github.debutante;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import io.github.debutante.helper.DeviceHelper;
import io.github.debutante.service.MediaDownloadService;

public class PreferencesFragment extends PreferenceFragmentCompat {

    private static final String[] SNAPPING_PREFERENCES = {"song_cache_mb", "cover_art_cache_mb", "streaming_timeout_secs"};

    private ActivityResultLauncher<String> activityResultLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                initBTList();
            }
        });
    }

    //@RequiresApi(api = DeviceHelper.ANDROID_12)
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        for (String key : SNAPPING_PREFERENCES) {

            SeekBarPreference seekBarPreference = findPreference(key);
            int seekBarIncrement = seekBarPreference.getSeekBarIncrement();
            seekBarPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                Integer value = (Integer) newValue;

                if (value.doubleValue() / seekBarIncrement == 0) {
                    return true;
                }

                int snapped = (value / seekBarIncrement) * seekBarIncrement;
                seekBarPreference.setValue(snapped);
                return false;
            });
        }

        findPreference("info_licenses").setOnPreferenceClickListener(p -> {
            startActivity(new Intent(requireContext(), OSSActivity.class));
            return true;
        });

        findPreference("clear_caches").setOnPreferenceClickListener(p -> {

            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.clear_caches_confirmation)
                    .setPositiveButton(R.string.yes, (dialog, whichButton) -> MediaDownloadService.sendRemoveAllDownloads(requireContext()))
                    .setNegativeButton(R.string.no, null).show();

            return true;
        });


        MultiSelectListPreference autoplayBTExclusion = initBTList();

        Preference.OnPreferenceClickListener btCheck = p -> {
            if (requireActivity().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && DeviceHelper.requireBTPermissions()) {
                activityResultLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
                return true;
            } else {
                return false;
            }

        };

        autoplayBTExclusion.setOnPreferenceClickListener(btCheck);
        findPreference("autoplay_on_bt_enabled").setOnPreferenceClickListener(btCheck);

        findPreference("offload_enabled").setEnabled(DeviceHelper.supportsAudioOffload());
    }

    @NonNull
    private MultiSelectListPreference initBTList() {
        MultiSelectListPreference autoplayBTExclusion = findPreference("autoplay_bt_exclusion");

        if (requireActivity().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || DeviceHelper.doNotRequireBTPermissions()) {
            BluetoothAdapter bluetoothAdapter = DeviceHelper.doNotRequireBTPermissions() ? BluetoothAdapter.getDefaultAdapter() : ((BluetoothManager) requireContext().getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
            if (bluetoothAdapter != null) {
                @SuppressLint("MissingPermission")//done in caller
                List<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices()
                        .stream()
                        .sorted(Comparator.comparing(BluetoothDevice::getName))
                        .collect(Collectors.toList());
                autoplayBTExclusion.setEntries(pairedDevices.stream().map(BluetoothDevice::getName).toArray(CharSequence[]::new));
                autoplayBTExclusion.setEntryValues(pairedDevices.stream().map(BluetoothDevice::getAddress).toArray(CharSequence[]::new));
            } else {
                autoplayBTExclusion.setEntries(new CharSequence[0]);
                autoplayBTExclusion.setEntryValues(new CharSequence[0]);
            }
        } else {
            autoplayBTExclusion.setEntries(new CharSequence[0]);
            autoplayBTExclusion.setEntryValues(new CharSequence[0]);
        }
        return autoplayBTExclusion;
    }
}
