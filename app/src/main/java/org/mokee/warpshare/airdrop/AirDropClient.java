package org.mokee.warpshare.airdrop;

import android.annotation.SuppressLint;
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
import org.mokee.warpshare.ResolvedUri;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketAddress;
import java.text.ParseException;
import java.util.List;

import javax.net.SocketFactory;
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
import okio.BufferedSink;

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

    AirDropClient(AirDropTrustManager trustManager) {
        mHttpClient = new OkHttpClient.Builder()
                .socketFactory(new SocketFactory() {
                    @Override
                    public Socket createSocket() {
                        Log.d(TAG, "0");
                        return new Socket() {
                            @Override
                            public void connect(SocketAddress endpoint, int timeout) throws IOException {
                                Log.d(TAG, "connect2: " + endpoint);
                                if (endpoint instanceof InetSocketAddress) {
                                    final InetSocketAddress socketAddress = (InetSocketAddress) endpoint;
                                    if (socketAddress.getAddress() instanceof Inet6Address) {
                                        final Inet6Address address = (Inet6Address) socketAddress.getAddress();
                                        final InetAddress addressWithScope = Inet6Address.getByAddress(
                                                address.getHostAddress(), address.getAddress(), NetworkInterface.getByName("wlan0"));
                                        endpoint = new InetSocketAddress(addressWithScope, socketAddress.getPort());
                                    }
                                }
                                Log.d(TAG, "connect/: " + endpoint);
                                super.connect(endpoint, timeout);
                            }
                        };
                    }

                    @Override
                    public Socket createSocket(String host, int port) {
                        return null;
                    }

                    @Override
                    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) {
                        return null;
                    }

                    @Override
                    public Socket createSocket(InetAddress host, int port) {
                        return null;
                    }

                    @Override
                    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) {
                        return null;
                    }
                })
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

    void post(final String url, List<ResolvedUri> uris, AirDropClientCallback callback) {
        final Buffer archive = new Buffer();

        try (final GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(archive.outputStream());
             final CpioArchiveOutputStream cpio = new CpioArchiveOutputStream(gzip, FORMAT_OLD_ASCII)) {
            for (ResolvedUri uri : uris) {
                final Buffer buffer = new Buffer();
                buffer.readFrom(uri.stream());
                final byte[] content = buffer.readByteArray();
                buffer.close();

                final CpioArchiveEntry entry = new CpioArchiveEntry(FORMAT_OLD_ASCII, uri.path, content.length);
                entry.setMode(C_ISREG | C_IRUSR | C_IWUSR | C_IRGRP | C_IROTH);

                cpio.putArchiveEntry(entry);
                cpio.write(content);
                cpio.closeArchiveEntry();
            }
        } catch (IOException e) {
            archive.close();
            callback.onFailure(e);
            return;
        }

        post(url, new RequestBody() {
                    @Override
                    public MediaType contentType() {
                        return MediaType.get("application/x-cpio");
                    }

                    @Override
                    public void writeTo(@NotNull BufferedSink bufferedSink) throws IOException {
                        bufferedSink.writeAll(archive);
                    }
                },
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
