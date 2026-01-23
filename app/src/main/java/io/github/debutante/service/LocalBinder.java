package io.github.debutante.service;

import android.os.Binder;

public class LocalBinder<T> extends Binder {
    private T service;

    public LocalBinder(T service) {
        this.service = service;
    }

    T getService() {
        return service;
    }
}