package io.github.debutante.service;

import android.os.Binder;

public class LocalBinder<T> extends Binder {
    private T service;

    public LocalBinder(T service) {
        this.service = service;
    }

    public T getService() {
        return service;
    }
}