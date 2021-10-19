package io.github.debutante.helper;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.viewbinding.ViewBinding;

import org.apache.commons.lang3.function.TriFunction;

import java.util.function.Function;

public final class BindingHelper {

    private BindingHelper() {
    }

    public static <T extends ViewBinding> T bindAndSetContent(Activity activity, Function<LayoutInflater, T> inflate) {
        T binding = inflate.apply(activity.getLayoutInflater());
        activity.setContentView(binding.getRoot());
        return binding;
    }

    public static <T extends ViewBinding> T bindAndInflate(LayoutInflater inflater, ViewGroup container, TriFunction<LayoutInflater, ViewGroup, Boolean, T> inflate) {
        return inflate.apply(inflater, container, false);

    }
}
