package io.github.debutante.helper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GsonRequest<T> {
    private final String url;
    private final Class<T> clazz;
    private final Gson gson;
    private final Consumer<Throwable> onError;
    private final Consumer<T> onSuccess;

    public GsonRequest(String url, Class<T> clazz, Gson gson, Consumer<T> onSuccess, @Nullable Consumer<Throwable> onError) {
        this.url = url;
        this.onError = onError;
        this.clazz = clazz;
        this.gson = gson;
        this.onSuccess = onSuccess;
    }

    public void enqueue(OkHttpClient okHttpClient) {
        Request request = new Request.Builder().get().url(url).build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                onError.accept(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                T parsed = gson.fromJson(response.body().charStream(), clazz);
                onSuccess.accept(parsed);
            }
        });

    }
}
