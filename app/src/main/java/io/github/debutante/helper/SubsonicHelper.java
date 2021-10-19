package io.github.debutante.helper;

import androidx.annotation.Nullable;

import com.google.gson.Gson;

import org.apache.commons.lang3.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.github.debutante.Debutante;
import io.github.debutante.model.api.PingResponse;

public final class SubsonicHelper {

    public static final int IMG_SIZE = 512;

    private SubsonicHelper() {
    }

    public static final String API_VERSION = "1.13.0";
    public static final String API_CLIENT = Debutante.TAG;
    public static final String CAST_CLIENT = Debutante.TAG + "Cast";

    private static final MessageDigest md5;

    static {
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static IntStream intStream(byte[] array) {
        return IntStream.range(0, array.length).map(idx -> array[idx]);
    }

    public static String salt(String username) {
        return String.valueOf(intStream(username.getBytes()).sum());
    }

    public static String token(String username, String password) {
        byte[] digest = md5.digest((password + salt(username)).getBytes());
        return byteArrayToHex(digest);
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for (byte b : a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static String buildUrl(String baseUrl, String url, String username, String token) {
        return String.format("%s%s?_rnd=%s&u=%s&t=%s&s=%s&f=json&v=%s&c=%s", normalizeUrl(baseUrl), url, Math.random(), username, token, salt(username), API_VERSION, API_CLIENT);
    }

    public static String buildUrl(String baseUrl, String url, String username, String token, Map<String, Object> additionalParams) {
        String suffix = additionalParams
                .entrySet()
                .stream()
                .map(e -> URIHelper.urlEncode(e.getKey()) + "=" + URIHelper.urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
        return buildUrl(baseUrl, url, username, token) + "&" + suffix;
    }

    public static String buildStreamUrl(String baseUrl, String username, String token, String remoteId, Optional<String> streamingFormat, Optional<String> streamingBitrate) {
        String url = String.format("%s%s?u=%s&t=%s&s=%s&v=%s&c=%s&id=%s", normalizeUrl(baseUrl), "rest/stream", username, token, salt(username), API_VERSION, API_CLIENT, remoteId);
        if (streamingFormat.isPresent()) {
            url += "&format=" + streamingFormat.get();
            if (streamingBitrate.isPresent()) {
                url += "&maxBitRate=" + streamingBitrate.get();
            }
        }
        return url;
    }

    public static String buildCoverArtUrl(String baseUrl, String username, String token, String remoteId) {
        return StringUtils.isEmpty(remoteId) ? null : String.format("%s%s?u=%s&t=%s&s=%s&v=%s&c=%s&size=%s&id=%s", normalizeUrl(baseUrl), "rest/getCoverArt", username, token, salt(username), API_VERSION, API_CLIENT, IMG_SIZE, remoteId);
    }

    private static String normalizeUrl(String baseUrl) {
        if (!baseUrl.endsWith("/")) {
            return baseUrl + "/";
        } else {
            return baseUrl;
        }
    }

    public static String withCastClient(String uri) {
        return replaceClient(uri, API_CLIENT, CAST_CLIENT);

    }

    public static String withAppClient(String uri) {
        return replaceClient(uri, CAST_CLIENT, API_CLIENT);
    }

    @Nullable
    private static String replaceClient(String uri, String from, String to) {
        return uri != null ? uri.replace("&c=" + from + "&", "&c=" + to + "&") : null;
    }

    public static GsonRequest<PingResponse> buildPingRequest(Gson gson, String baseUrl, String username, String token, Consumer<PingResponse> onSuccess, Consumer<Throwable> onError) {
        String fullURL = SubsonicHelper.buildUrl(baseUrl, "rest/ping", username, token);
        return new GsonRequest<>(fullURL, PingResponse.class, gson, onSuccess, onError);
    }
}
