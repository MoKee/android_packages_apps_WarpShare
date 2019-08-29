package org.mokee.warpshare.airdrop;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.dd.plist.NSDictionary;
import com.dd.plist.PropertyListParser;
import com.koushikdutta.async.AsyncNetworkSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import java.io.InputStream;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import okio.Buffer;

class AirDropServer {

    private static final String TAG = "AirDropServer";

    private static final int PORT = 8770;

    private static final String MIME_OCTET_STREAM = "application/octet-stream";

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final AirDropTrustManager mTrustManager;
    private final AirDropManager mParent;

    private AsyncHttpServer mServer;

    AirDropServer(AirDropTrustManager trustManager, AirDropManager parent) {
        mTrustManager = trustManager;
        mParent = parent;

        mServer = new AsyncHttpServer();
        mServer.post("/Discover", new NSDictionaryHttpServerRequestCallback() {
            @Override
            protected NSDictionary onRequest(InetAddress remote, NSDictionary request) {
                Log.d(TAG, "POST /Discover (" + remote.getHostAddress() + ")");
                return handleDiscover(remote, request);
            }
        });
        mServer.post("/Ask", new NSDictionaryHttpServerRequestCallback() {
            @Override
            protected NSDictionary onRequest(InetAddress remote, NSDictionary request) {
                Log.d(TAG, "POST /Ask (" + remote.getHostAddress() + ")");
                return handleAsk(remote, request);
            }
        });
        mServer.post("/Upload", new InputStreamHttpServerRequestCallback() {
            @Override
            protected NSDictionary onRequest(InetAddress remote, InputStream input) {
                Log.d(TAG, "POST /Upload (" + remote.getHostAddress() + ")");
                return handleUpload(remote, input);
            }
        });
    }

    int start(String host) {
        mServer.listenSecure(PORT, mTrustManager.getSSLContext());
        Log.d(TAG, "Server running at " + host + ":" + PORT);
        return PORT;
    }

    void stop() {
        mServer.stop();
    }

    private NSDictionary handleDiscover(final InetAddress remote, final NSDictionary request) {
        final AtomicReference<NSDictionary> ref = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mParent.handleDiscover(remote.getHostAddress(), request, new ResultCallback() {
                    @Override
                    public void call(NSDictionary result) {
                        ref.set(result);
                        latch.countDown();
                    }
                });
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            return null;
        }

        return ref.get();
    }

    private NSDictionary handleAsk(final InetAddress remote, final NSDictionary request) {
        final AtomicReference<NSDictionary> ref = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mParent.handleAsk(remote.getHostAddress(), request, new ResultCallback() {
                    @Override
                    public void call(NSDictionary result) {
                        ref.set(result);
                        latch.countDown();
                    }
                });
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            return null;
        }

        return ref.get();
    }

    private NSDictionary handleUpload(final InetAddress remote, final InputStream stream) {
        final AtomicReference<NSDictionary> ref = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        mParent.handleUpload(remote.getHostAddress(), stream, new ResultCallback() {
            @Override
            public void call(NSDictionary result) {
                ref.set(result);
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            return null;
        }

        return ref.get();
    }

    public interface ResultCallback {

        void call(NSDictionary result);

    }

    private abstract class NSDictionaryHttpServerRequestCallback implements HttpServerRequestCallback {

        @Override
        public final void onRequest(final AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
            final AsyncNetworkSocket socket = (AsyncNetworkSocket) request.getSocket();
            final InetAddress address = socket.getRemoteAddress().getAddress();

            final Buffer buffer = new Buffer();

            request.setDataCallback(new DataCallback() {
                @Override
                public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                    buffer.write(bb.getAllByteArray());
                }
            });

            request.setEndCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    buffer.flush();

                    if (ex != null) {
                        Log.e(TAG, "Failed receiving request", ex);
                        buffer.close();
                        response.code(500).end();
                        return;
                    }

                    final NSDictionary req;
                    try {
                        req = (NSDictionary) PropertyListParser.parse(buffer.inputStream());
                    } catch (Exception e) {
                        Log.e(TAG, "Failed deserializing request", e);
                        buffer.close();
                        response.code(500).end();
                        return;
                    }

                    buffer.close();

                    final NSDictionary res = onRequest(address, req);

                    if (res == null) {
                        response.code(401).end();
                        return;
                    }

                    try {
                        final Buffer buffer = new Buffer();
                        PropertyListParser.saveAsBinary(res, buffer.outputStream());
                        response.send(MIME_OCTET_STREAM, buffer.readByteArray());
                    } catch (Exception e) {
                        Log.e(TAG, "Failed serializing response", e);
                        response.code(500).end();
                    }
                }
            });
        }

        protected abstract NSDictionary onRequest(InetAddress remote, NSDictionary request);

    }

    private abstract class InputStreamHttpServerRequestCallback implements HttpServerRequestCallback {

        @Override
        public final void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
            final AsyncNetworkSocket socket = (AsyncNetworkSocket) request.getSocket();
            final InetAddress address = socket.getRemoteAddress().getAddress();

            final Buffer buffer = new Buffer();

            request.setDataCallback(new DataCallback() {
                @Override
                public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                    buffer.write(bb.getAllByteArray());
                }
            });

            request.setEndCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    buffer.flush();
                }
            });

            final NSDictionary res = onRequest(address, buffer.inputStream());

            buffer.close();

            if (res == null) {
                response.code(401).end();
                return;
            }

            response.end();
        }

        protected abstract NSDictionary onRequest(InetAddress remote, InputStream input);

    }

}
