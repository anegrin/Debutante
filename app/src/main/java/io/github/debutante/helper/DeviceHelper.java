package io.github.debutante.helper;

import android.os.Build;

public final class DeviceHelper {

    private DeviceHelper() {
    }

    public static boolean requireSpecificReadAudioPermissions() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    public static boolean doNotRequirePostNotificationPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU;
    }

    public static boolean doNotRequireReceiverFlags() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU;
    }

    public static boolean supportsAudioOffload() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    public static boolean requireBTPermissions() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static boolean doNotRequireBTPermissions() {
        return !requireBTPermissions();
    }

    public static boolean needsStopPlayerButton() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R;
    }
}
