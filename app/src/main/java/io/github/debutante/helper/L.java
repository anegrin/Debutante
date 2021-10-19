package io.github.debutante.helper;

import static io.github.debutante.BuildConfig.L_D;
import static io.github.debutante.Debutante.TAG;

import android.os.BaseBundle;
import android.os.Bundle;
import android.util.Log;

import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

public final class L {

    private L() {
    }

    public static void v(String m) {
        if (L_D) {
            Log.v(TAG, m);
        }
    }

    public static void d(String m) {
        if (L_D) {
            Log.d(TAG, m);
        }
    }

    public static void i(String m) {
        Log.i(TAG, m);
    }

    public static void w(String m) {
        Log.w(TAG, m);
    }

    public static void e(String m) {
        Log.e(TAG, m);
    }

    public static void v(String m, Throwable t) {
        if (L_D) {
            Log.v(TAG, m, t);
        }
    }

    public static void d(String m, Throwable t) {
        if (L_D) {
            Log.d(TAG, m, t);
        }
    }

    public static void i(String m, Throwable t) {
        Log.i(TAG, m, t);
    }

    public static void w(String m, Throwable t) {
        Log.w(TAG, m, t);
    }

    public static void e(String m, Throwable t) {
        Log.e(TAG, m, t);
    }

    public static String toString(Bundle extras) {
        return "{" + Optional.ofNullable(extras)
                .map(BaseBundle::keySet)
                .orElse(Collections.emptySet())
                .stream()
                .map(k -> k + "=" + extras.get(k))
                .collect(Collectors.joining(", ")) + "}";
    }
}
