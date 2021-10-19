package io.github.debutante.helper;

import android.net.Uri;

import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class URIHelper {

    public static final String LOCAL_UUID_PARAM = "localUuid";

    private static final String FILE_SCHEME = "file";
    private static final String ANDROID_RESOURCE_SCHEME = "android.resource";
    private static final String LOCAL_UUID_PARAM_PREFIX = LOCAL_UUID_PARAM + "=";

    private URIHelper() {
    }

    public static String urlEncode(Object value) {
        String asString = value != null ? value.toString() : "";
        try {
            return URLEncoder.encode(asString, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            return asString;
        }
    }

    public static String urlDecode(String value) {
        if (value != null) {
            try {
                return URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
            } catch (UnsupportedEncodingException e) {
                return null;
            }
        }

        return value;
    }

    public static boolean isRemote(Uri uri) {
        return uri != null && !uri.getScheme().equals(FILE_SCHEME) && !uri.getScheme().equals(ANDROID_RESOURCE_SCHEME) && !StringUtils.contains(uri.getQuery(), LOCAL_UUID_PARAM_PREFIX);
    }

    public static boolean isRemote(URI uri) {
        return uri != null && !uri.getScheme().equals(FILE_SCHEME) && !uri.getScheme().equals(ANDROID_RESOURCE_SCHEME) && !StringUtils.contains(uri.getQuery(), LOCAL_UUID_PARAM_PREFIX);
    }

    public static boolean isRemote(String uri) {
        return uri != null && uri.contains(":") && !uri.startsWith(FILE_SCHEME) && !uri.startsWith(ANDROID_RESOURCE_SCHEME) && !uri.contains(LOCAL_UUID_PARAM_PREFIX);
    }
}
