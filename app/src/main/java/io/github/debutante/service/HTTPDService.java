package io.github.debutante.service;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import com.google.android.exoplayer2.util.MimeTypes;

import org.apache.commons.collections4.CollectionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import javax.crypto.Cipher;

import fi.iki.elonen.NanoHTTPD;
import io.github.debutante.Debutante;
import io.github.debutante.R;
import io.github.debutante.helper.L;
import io.github.debutante.helper.Obj;
import io.github.debutante.helper.SubsonicHelper;
import io.github.debutante.helper.URIHelper;
import io.github.debutante.persistence.EntityRepository;
import io.github.debutante.persistence.entities.SongEntity;

public class HTTPDService extends BaseForegroundService {

    private static final int MIN_PORT = 44444;
    private static final int MAX_PORT = 55555;
    public static final int SERVER_PORT = new Random().nextInt(MAX_PORT - MIN_PORT) + MIN_PORT;
    private static final KeyPair KEY_PAIR;
    private static final int NOTIFICATION_ID = Debutante.NOTIFICATION_ID + 1;
    private static final Object GLOBAL_LOCK = new Object();

    private static final String ALGORITHM = "RSA";

    static {
        KeyPair keyPair;
        try {
            SecureRandom secureRandom = new SecureRandom(UUID.randomUUID().toString().getBytes());
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM);
            keyPairGenerator.initialize(2048, secureRandom);
            keyPair = keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            keyPair = null;
        }

        KEY_PAIR = keyPair;
    }

    private DebutanteHTTPD httpd;

    public HTTPDService() {
        super(R.string.httpd_service_notification_content, NOTIFICATION_ID);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        synchronized (GLOBAL_LOCK) {

            String hostname = hostname(this);

            int startCommand = super.onStartCommand(intent, flags, startId);

            if (hostname != null && d().appConfig().isCastLocalEnabled()) {

                L.i("Starting foreground HTTPD service " + hostname + ":" + SERVER_PORT);

                if (httpd == null || !httpd.isAlive()) {
                    try {
                        httpd = new DebutanteHTTPD(hostname, SERVER_PORT, d().repository());
                        httpd.start((int) (d().appConfig().getStreamingTimeoutSecs() * 1000L));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                return startCommand;
            } else {
                return START_NOT_STICKY;
            }
        }
    }

    public static String encrypt(String text) {
        try {
            if (KEY_PAIR != null) {

                Cipher cipher = Cipher.getInstance(ALGORITHM);

                cipher.init(Cipher.ENCRYPT_MODE, KEY_PAIR.getPrivate());

                byte[] bytes = cipher.doFinal(text.getBytes());
                return SubsonicHelper.byteArrayToHex(bytes);
            }
        } catch (Exception e) {
            L.e("Can't encrypt string", e);
        }
        return null;
    }

    private static String decrypt(String encryptedText) {
        try {
            if (KEY_PAIR != null) {
                Cipher cipher
                        = Cipher.getInstance(ALGORITHM);

                cipher.init(Cipher.DECRYPT_MODE, KEY_PAIR.getPublic());
                byte[] result = cipher.doFinal(SubsonicHelper.hexStringToByteArray(encryptedText));

                return new String(result);
            }
        } catch (Exception e) {
            L.e("Can't decrypt string", e);
        }
        return null;
    }

    public static String hostname(Context context) {
        try {
            WifiManager wifiMgr = (WifiManager) context.getSystemService(WIFI_SERVICE);
            WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
            int ipAddress = wifiInfo.getIpAddress();
            return String.format(Locale.getDefault(), "%d.%d.%d.%d",
                    (ipAddress & 0xff),
                    (ipAddress >> 8 & 0xff),
                    (ipAddress >> 16 & 0xff),
                    (ipAddress >> 24 & 0xff));
        } catch (Exception e) {
            L.e("Can't get wifi ip address", e);
            return null;
        }
    }

    @Override
    protected void doStopSelf() {
        stopHTTPD();
        super.doStopSelf();
    }

    private void stopHTTPD() {
        try {
            if (httpd != null) {
                httpd.stop();
            }
        } catch (Exception e) {
            L.e("Can't stop HTTPD", e);
        }
    }

    @Override
    public boolean stopService(Intent name) {
        return Obj.tap(super.stopService(name), b -> stopHTTPD());
    }

    @Override
    public void onDestroy() {
        stopHTTPD();
        super.onDestroy();
    }

    private static final class DebutanteHTTPD extends NanoHTTPD {
        private final EntityRepository repository;

        public DebutanteHTTPD(String hostname, int port, EntityRepository repository) {
            super(hostname, port);
            this.repository = repository;
        }

        @Override
        public Response serve(IHTTPSession session) {
            List<String> localUuids = session.getParameters().get(URIHelper.LOCAL_UUID_PARAM);

            if (CollectionUtils.size(localUuids) == 1) {
                String localUuid = decrypt(localUuids.get(0));

                if (localUuid == null) {
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MimeTypes.BASE_TYPE_TEXT, "");
                }

                try {
                    SongEntity songEntity = repository.findSongByUuid(localUuid).blockingGet();

                    File file = new File(songEntity.remoteUuid());

                    L.i("Streaming " + file + " from device");

                    FileInputStream data = null;
                    try {
                        data = new FileInputStream(file);
                        return new DebutanteResponse(data, file.length(), songEntity.duration);
                    } catch (IOException ioe) {
                        L.e("Error serving " + localUuid, ioe);

                        if (data != null) {
                            try {
                                data.close();
                            } catch (IOException e) {
                                L.e("Can't close " + file, e);
                            }
                        }

                        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MimeTypes.BASE_TYPE_TEXT, ioe.getMessage());
                    }
                } catch (Exception e) {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MimeTypes.BASE_TYPE_TEXT, e.getMessage());
                }


            } else {
                return super.serve(session);//404 is the default
            }
        }

        private static final class DebutanteResponse extends Response {

            private final InputStream data;

            private DebutanteResponse(InputStream data, long totalBytes, int durationSecs) {
                super(Response.Status.OK, MimeTypes.BASE_TYPE_AUDIO, data, totalBytes);
                this.data = data;
                addHeader("accept-ranges", "bytes");
                String range = "bytes 0-" + (totalBytes - 1) + "/" + totalBytes;
                L.v("Content-Range header " + range);
                addHeader("Content-Range", range);
                addHeader("x-content-duration", String.valueOf(durationSecs));
            }

            @Override
            protected void send(OutputStream outputStream) {
                try (InputStream is = this.data) {
                    super.send(outputStream);
                } catch (IOException e) {
                    L.e("Can't close response data", e);
                }
            }
        }
    }
}
