package io.github.debutante.receivers;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Parcelable;

import org.apache.commons.collections4.CollectionUtils;

import io.github.debutante.Debutante;
import io.github.debutante.helper.DeviceHelper;
import io.github.debutante.helper.L;
import io.github.debutante.helper.Obj;
import io.github.debutante.model.AppConfig;
import io.github.debutante.service.InitService;

public class A2DPReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
        int previousState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, state);
        L.i("A2DP Bluetooth device connection state switching from " + stateToString(previousState) + " to " + stateToString(state));

        Parcelable parcelableExtra = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

        if (parcelableExtra instanceof BluetoothDevice) {
            L.d("A2DP Bluetooth device: " + parcelableExtra);

            if (state != previousState && state == BluetoothProfile.STATE_CONNECTED) {
                if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || DeviceHelper.doNotRequireBTPermissions()) {
                    AppConfig appConfig = Debutante.loadAppConfig(context);
                    BluetoothDevice bluetoothDevice = (BluetoothDevice) parcelableExtra;
                    if (!CollectionUtils.containsAny(appConfig.getAutoplayBTExclusion(), bluetoothDevice.getAddress())) {
                        context.startForegroundService(Obj.tap(new Intent(context, InitService.class), i -> i.setAction(InitService.ACTION_PLAY)));
                    }
                }
            }
        }

    }

    private String stateToString(int state) {
        switch (state) {
            case BluetoothProfile.STATE_CONNECTED:
                return "STATE_CONNECTED";
            case BluetoothProfile.STATE_CONNECTING:
                return "STATE_CONNECTING";
            case BluetoothProfile.STATE_DISCONNECTED:
                return "STATE_DISCONNECTED";
            case BluetoothProfile.STATE_DISCONNECTING:
                return "STATE_DISCONNECTING";
            default:
                return "STATE_UNKNOWN";
        }
    }
}
