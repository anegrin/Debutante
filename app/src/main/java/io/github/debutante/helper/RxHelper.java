package io.github.debutante.helper;

import androidx.annotation.NonNull;

import java.time.Duration;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

@SuppressWarnings("UnusedReturnValue")
public final class RxHelper {
    public static final ThreadFactory LOW_PRI_THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    };
    private static final RxHelper INSTANCE = new RxHelper(Schedulers.io(), AndroidSchedulers.mainThread());
    private final Scheduler subscribeOn;
    private final Scheduler observeOn;

    private RxHelper(Scheduler subscribeOn, Scheduler observeOn) {
        this.subscribeOn = subscribeOn;
        this.observeOn = observeOn;
    }

    public static RxHelper defaultInstance() {
        return INSTANCE;
    }

    public static RxHelper newInstance(Scheduler subscribeOn, Scheduler observeOn) {
        return new RxHelper(subscribeOn, observeOn);
    }

    public Disposable subscribe(Duration delay, Completable completable, @NonNull Action onComplete, @NonNull Consumer<? super Throwable> onError) {
        return completable.delay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .subscribeOn(subscribeOn)
                .observeOn(observeOn)
                .doOnDispose(() -> L.v("Disposable has been disposed"))
                .subscribe(onComplete, onError);
    }

    public Disposable subscribe(Completable completable, @NonNull Action onComplete, @NonNull Consumer<? super Throwable> onError) {
        return completable.subscribeOn(subscribeOn)
                .observeOn(observeOn)
                .doOnDispose(() -> L.v("Disposable has been disposed"))
                .subscribe(onComplete, onError);
    }

    public <T> Disposable subscribe(Single<T> completable, @NonNull Consumer<? super T> onSuccess, @NonNull Consumer<? super Throwable> onError) {
        return completable.subscribeOn(subscribeOn)
                .observeOn(observeOn)
                .doOnDispose(() -> L.v("Disposable has been disposed"))
                .subscribe(onSuccess, onError);
    }

    public <T> Disposable subscribe(Maybe<T> completable, @NonNull Consumer<? super T> onSuccess, @NonNull Consumer<? super Throwable> onError) {
        return completable.subscribeOn(subscribeOn)
                .observeOn(observeOn)
                .doOnDispose(() -> L.v("Disposable has been disposed"))
                .subscribe(onSuccess, onError);
    }
}
