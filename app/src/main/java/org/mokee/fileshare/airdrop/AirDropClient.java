package org.mokee.fileshare.airdrop;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.dd.plist.NSDictionary;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;

import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.xml.parsers.ParserConfigurationException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import static org.apache.commons.compress.archivers.cpio.CpioConstants.C_IRGRP;
import static org.apache.commons.compress.archivers.cpio.CpioConstants.C_IROTH;
import static org.apache.commons.compress.archivers.cpio.CpioConstants.C_IRUSR;
import static org.apache.commons.compress.archivers.cpio.CpioConstants.C_ISREG;
import static org.apache.commons.compress.archivers.cpio.CpioConstants.C_IWUSR;
import static org.apache.commons.compress.archivers.cpio.CpioConstants.FORMAT_OLD_ASCII;

class AirDropClient {

    private static final String TAG = "AirDropClient";

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private OkHttpClient mHttpClient;

    AirDropClient(Context context) {
        final AirDropTrustManager trustManager = new AirDropTrustManager(context);

        mHttpClient = new OkHttpClient.Builder()
                .sslSocketFactory(trustManager.getSslSocketFactory(), trustManager.getTrustManager())
                .hostnameVerifier(new HostnameVerifier() {
                    @SuppressLint("BadHostnameVerifier")
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                })
                .build();
    }

    void post(final String url, NSDictionary body, AirDropClientCallback callback) {
        final Buffer buffer = new Buffer();

        try {
            PropertyListParser.saveAsBinary(body, buffer.outputStream());
        } catch (IOException e) {
            callback.onFailure(e);
            return;
        }

        post(url, RequestBody.create(
                buffer.readByteString(), MediaType.get("application/octet-stream")),
                callback);

        buffer.close();
    }

    void post(final String url, String fileName, InputStream stream, AirDropClientCallback callback) {
        final Buffer buffer = new Buffer();
        final Buffer archive = new Buffer();

        try {
            buffer.readFrom(stream);
            stream.close();
        } catch (IOException e) {
            buffer.close();
            callback.onFailure(e);
            return;
        }

        final byte[] content = buffer.readByteArray();

        try (final GzipCompressorOutputStream gzipStream = new GzipCompressorOutputStream(archive.outputStream());
             final CpioArchiveOutputStream cpioStream = new CpioArchiveOutputStream(gzipStream, FORMAT_OLD_ASCII)) {
            final CpioArchiveEntry entry = new CpioArchiveEntry(FORMAT_OLD_ASCII, "./" + fileName, content.length);
            entry.setMode(C_ISREG | C_IRUSR | C_IWUSR | C_IRGRP | C_IROTH);
            cpioStream.putArchiveEntry(entry);
            cpioStream.write(content);
            cpioStream.closeArchiveEntry();
            cpioStream.finish();
        } catch (IOException e) {
            archive.close();
            callback.onFailure(e);
            return;
        } finally {
            buffer.close();
        }

        post(url, RequestBody.create(
                archive.readByteArray(), MediaType.get("application/x-cpio")),
                callback);

        archive.close();
    }

    private void post(final String url, RequestBody body, final AirDropClientCallback callback) {
        mHttpClient.newCall(new Request.Builder()
                .url(url)
                .post(body)
                .build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NotNull Call call, @NotNull IOException e) {
                        Log.e(TAG, "Request failed: " + url, e);
                        postFailure(callback, e);
                    }

                    @Override
                    public void onResponse(@NotNull Call call, @NotNull Response response) {
                        final int statusCode = response.code();
                        if (statusCode != 200) {
                            postFailure(callback, new IOException("Request failed: " + statusCode));
                            return;
                        }

                        final ResponseBody responseBody = response.body();
                        if (responseBody == null) {
                            postFailure(callback, new IOException("Response body null"));
                            return;
                        }

                        try {
                            NSDictionary root = (NSDictionary) PropertyListParser.parse(responseBody.byteStream());
                            postResponse(callback, root);
                        } catch (PropertyListFormatException | ParseException | ParserConfigurationException | SAXException e) {
                            postFailure(callback, new IOException(e));
                        } catch (IOException e) {
                            postFailure(callback, e);
                        }
                    }
                });
    }

    private void postResponse(final AirDropClientCallback callback,
                              final NSDictionary response) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onResponse(response);
            }
        });
    }

    private void postFailure(final AirDropClientCallback callback, final IOException e) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onFailure(e);
            }
        });
    }

    interface AirDropClientCallback {

        void onFailure(IOException e);

        void onResponse(NSDictionary response);

    }

}
