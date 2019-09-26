package org.mokee.warpshare.airdrop;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.dd.plist.NSDictionary;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;
import com.mokee.warpshare.CertificateManager;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketAddress;
import java.text.ParseException;

import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;
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
import okio.Okio;

class AirDropClient {

    private static final String TAG = "AirDropClient";

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private OkHttpClient mHttpClient;
    private NetworkInterface mInterface;

    AirDropClient(CertificateManager certificateManager) {
        mHttpClient = new OkHttpClient.Builder()
                .socketFactory(new LinkLocalAddressSocketFactory())
                .sslSocketFactory(
                        certificateManager.getSSLContext().getSocketFactory(),
                        (X509TrustManager) certificateManager.getTrustManagers()[0])
                .hostnameVerifier(new HostnameVerifier() {
                    @SuppressLint("BadHostnameVerifier")
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                })
                .build();
    }

    void setNetworkInterface(NetworkInterface iface) {
        mInterface = iface;
    }

    Call post(final String url, NSDictionary body, AirDropClientCallback callback) {
        final Buffer buffer = new Buffer();

        try {
            PropertyListParser.saveAsBinary(body, buffer.outputStream());
        } catch (IOException e) {
            callback.onFailure(e);
            return null;
        }

        final Call call = post(url, RequestBody.create(
                buffer.readByteString(), MediaType.get("application/octet-stream")),
                callback);

        buffer.close();

        return call;
    }

    Call post(final String url, final InputStream input, AirDropClientCallback callback) {
        return post(url, new RequestBody() {
                    @Override
                    public MediaType contentType() {
                        return MediaType.get("application/x-cpio");
                    }

                    @Override
                    public void writeTo(BufferedSink bufferedSink) throws IOException {
                        bufferedSink.writeAll(Okio.source(input));
                        input.close();
                    }
                },
                callback);
    }

    private Call post(final String url, RequestBody body, final AirDropClientCallback callback) {
        final Call call = mHttpClient.newCall(new Request.Builder()
                .url(url)
                .post(body)
                .build());

        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (call.isCanceled()) {
                    Log.w(TAG, "Request canceled", e);
                } else {
                    Log.e(TAG, "Request failed: " + url, e);
                    postFailure(callback, e);
                }
            }

            @Override
            public void onResponse(Call call, Response response) {
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

        return call;
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

    private class LinkLocalAddressSocketFactory extends SocketFactory {

        @Override
        public Socket createSocket() {
            return new Socket() {
                @Override
                public void connect(SocketAddress endpoint, int timeout) throws IOException {
                    if (mInterface != null && endpoint instanceof InetSocketAddress) {
                        final InetSocketAddress socketAddress = (InetSocketAddress) endpoint;
                        if (socketAddress.getAddress() instanceof Inet6Address) {
                            final Inet6Address address = (Inet6Address) socketAddress.getAddress();

                            // OkHttp can't parse link-local IPv6 addresses properly, so we need to
                            // set the scope of the address here
                            final InetAddress addressWithScope = Inet6Address.getByAddress(
                                    null, address.getAddress(), mInterface);

                            endpoint = new InetSocketAddress(addressWithScope, socketAddress.getPort());
                        }
                    }
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

    }

}
