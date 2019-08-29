package org.mokee.warpshare.airdrop;

import android.util.Log;

import com.dd.plist.NSDictionary;
import com.dd.plist.PropertyListParser;
import com.koushikdutta.async.AsyncNetworkSocket;
import com.koushikdutta.async.AsyncSSLSocketWrapper;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.koushikdutta.async.http.server.UnknownRequestBody;

import java.io.IOException;
import java.net.InetAddress;

import okio.Buffer;
import okio.Pipe;
import okio.Source;

class AirDropServer {

    private static final String TAG = "AirDropServer";

    private static final int PORT = 8770;

    private static final String MIME_OCTET_STREAM = "application/octet-stream";

    private final AirDropTrustManager mTrustManager;
    private final AirDropManager mParent;

    private AsyncHttpServer mServer;

    AirDropServer(AirDropTrustManager trustManager, AirDropManager parent) {
        mTrustManager = trustManager;
        mParent = parent;
    }

    int start(String host) {
        mServer = new AsyncHttpServer();
        mServer.listenSecure(PORT, mTrustManager.getSSLContext());
        mServer.post("/Discover", new NSDictionaryHttpServerRequestCallback() {
            @Override
            protected void onRequest(InetAddress remote, NSDictionary request, NSDictionaryHttpServerResponse response) {
                handleDiscover(remote, request, response);
            }
        });
        mServer.post("/Ask", new NSDictionaryHttpServerRequestCallback() {
            @Override
            protected void onRequest(InetAddress remote, NSDictionary request, NSDictionaryHttpServerResponse response) {
                handleAsk(remote, request, response);
            }
        });
        mServer.post("/Upload", new SourceHttpServerRequestCallback() {
            @Override
            protected void onRequest(InetAddress remote, Source source, NSDictionaryHttpServerResponse response) {
                handleUpload(remote, source, response);
            }
        });
        Log.d(TAG, "Server running at " + host + ":" + PORT);
        return PORT;
    }

    void stop() {
        mServer.stop();
    }

    private void handleDiscover(InetAddress remote, NSDictionary request, final NSDictionaryHttpServerResponse response) {
        mParent.handleDiscover(remote.getHostAddress(), request, new ResultCallback() {
            @Override
            public void call(NSDictionary result) {
                if (result != null) {
                    response.send(result);
                } else {
                    response.send(401);
                }
            }
        });
    }

    private void handleAsk(InetAddress remote, NSDictionary request, final NSDictionaryHttpServerResponse response) {
        mParent.handleAsk(remote.getHostAddress(), request, new ResultCallback() {
            @Override
            public void call(NSDictionary result) {
                if (result != null) {
                    response.send(result);
                } else {
                    response.send(401);
                }
            }
        });
    }

    private void handleUpload(InetAddress remote, Source source, final NSDictionaryHttpServerResponse response) {
        mParent.handleUpload(remote.getHostAddress(), source, new ResultCallback() {
            @Override
            public void call(NSDictionary result) {
                if (result != null) {
                    response.send(200);
                } else {
                    response.send(401);
                }
            }
        });
    }

    public interface ResultCallback {

        void call(NSDictionary result);

    }

    private interface NSDictionaryHttpServerResponse {

        void send(int code);

        void send(NSDictionary response);

    }

    private abstract class NSDictionaryHttpServerRequestCallback implements HttpServerRequestCallback {

        @Override
        public final void onRequest(final AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
            Log.d(TAG, "Request: " + request.getMethod() + " " + request.getPath());

            final AsyncSSLSocketWrapper socketWrapper = (AsyncSSLSocketWrapper) request.getSocket();
            final AsyncNetworkSocket socket = (AsyncNetworkSocket) socketWrapper.getSocket();
            final InetAddress address = socket.getRemoteAddress().getAddress();

            final UnknownRequestBody body = (UnknownRequestBody) request.getBody();
            final DataEmitter emitter = body.getEmitter();

            final Buffer buffer = new Buffer();

            emitter.setDataCallback(new DataCallback() {
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
                        req = (NSDictionary) PropertyListParser.parse(buffer.readByteArray());
                    } catch (Exception e) {
                        Log.e(TAG, "Failed deserializing request", e);
                        response.code(500).end();
                        return;
                    } finally {
                        buffer.close();
                    }

                    onRequest(address, req, new NSDictionaryHttpServerResponse() {
                        @Override
                        public void send(int code) {
                            response.code(code).end();
                        }

                        @Override
                        public void send(NSDictionary res) {
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
            });
        }

        protected abstract void onRequest(InetAddress remote, NSDictionary request, NSDictionaryHttpServerResponse response);

    }

    private abstract class SourceHttpServerRequestCallback implements HttpServerRequestCallback {

        @Override
        public final void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
            Log.d(TAG, "Request: " + request.getMethod() + " " + request.getPath());

            final AsyncSSLSocketWrapper socketWrapper = (AsyncSSLSocketWrapper) request.getSocket();
            final AsyncNetworkSocket socket = (AsyncNetworkSocket) socketWrapper.getSocket();
            final InetAddress address = socket.getRemoteAddress().getAddress();

            final UnknownRequestBody body = (UnknownRequestBody) request.getBody();
            final DataEmitter emitter = body.getEmitter();

            final Pipe pipe = new Pipe(1024);

            emitter.setDataCallback(new DataCallback() {
                @Override
                public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                    try (final Buffer buffer = new Buffer()) {
                        buffer.write(bb.getAllByteArray());
                        pipe.sink().write(buffer, buffer.size());
                    } catch (IOException e) {
                        Log.e(TAG, "Failed receiving upload", e);
                        response.code(500).end();
                    }
                }
            });

            request.setEndCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    try {
                        pipe.sink().flush();
                        pipe.sink().close();
                    } catch (IOException e) {
                        Log.e(TAG, "Failed receiving upload", e);
                        response.code(500).end();
                    }
                }
            });

            onRequest(address, pipe.source(), new NSDictionaryHttpServerResponse() {
                @Override
                public void send(int code) {
                    response.code(code).end();
                }

                @Override
                public void send(NSDictionary response) {
                    throw new UnsupportedOperationException();
                }
            });
        }

        protected abstract void onRequest(InetAddress remote, Source source, NSDictionaryHttpServerResponse response);

    }

}
