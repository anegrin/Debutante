package io.github.debutante.helper;

import java.util.function.Consumer;
import java.util.function.Function;

public final class Obj {
    private Obj() {

    }

    public static <T, R> R with(T obj, Function<T, R> with) {
        return with.apply(obj);
    }

    public static <T> T tap(T obj, Consumer<T> tap) {
        tap.accept(obj);
        return obj;
    }
}
