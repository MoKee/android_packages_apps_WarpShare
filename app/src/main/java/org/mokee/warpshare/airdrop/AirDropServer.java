/*
 * Copyright (C) 2019 The MoKee Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mokee.warpshare.airdrop;

import android.util.Log;

import com.dd.plist.NSDictionary;
import com.dd.plist.PropertyListParser;
import com.koushikdutta.async.AsyncNetworkSocket;
import com.koushikdutta.async.AsyncSSLSocketWrapper;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.koushikdutta.async.http.server.UnknownRequestBody;
import com.mokee.warpshare.CertificateManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;

import okio.Buffer;
import okio.Okio;
import okio.Pipe;

class AirDropServer {

    private static final String TAG = "AirDropServer";

    private static final int PORT = 8770;

    private static final String MIME_OCTET_STREAM = "application/octet-stream";

    private final CertificateManager mCertificateManager;
    private final AirDropManager mParent;

    private AsyncHttpServer mServer;

    AirDropServer(CertificateManager certificateManager, AirDropManager parent) {
        mCertificateManager = certificateManager;
        mParent = parent;
    }

    int start(String host) {
        mServer = new AsyncHttpServer();
        mServer.listenSecure(PORT, mCertificateManager.getSSLContext());
        mServer.post("/Discover", new NSDictionaryHttpServerRequestCallback() {
            @Override
            protected void onRequest(InetAddress remote, NSDictionary request,
                                     NSDictionaryHttpServerResponse response) {
                handleDiscover(remote, request, response);
            }
        });
        mServer.post("/Ask", new NSDictionaryHttpServerRequestCallback() {
            @Override
            protected void onRequest(InetAddress remote, NSDictionary request,
                                     NSDictionaryHttpServerResponse response) {
                handleAsk(remote, request, response);
            }

            @Override
            protected void onCanceled(InetAddress remote) {
                handleAskCanceled(remote);
            }
        });
        mServer.post("/Upload", new InputStreamHttpServerRequestCallback() {
            @Override
            protected void onRequest(InetAddress remote, InputStream request,
                                     NSDictionaryHttpServerResponse response) {
                handleUpload(remote, request, response);
            }
        });
        Log.d(TAG, "Server running at " + host + ":" + PORT);
        return PORT;
    }

    void stop() {
        mServer.stop();
    }

    private void handleDiscover(InetAddress remote, NSDictionary request,
                                final NSDictionaryHttpServerResponse response) {
        mParent.handleDiscover(remote.getHostAddress(), request, result -> {
            if (result != null) {
                response.send(result);
            } else {
                response.send(401);
            }
        });
    }

    private void handleAsk(InetAddress remote, NSDictionary request,
                           final NSDictionaryHttpServerResponse response) {
        mParent.handleAsk(remote.getHostAddress(), request, result -> {
            if (result != null) {
                response.send(result);
            } else {
                response.send(401);
            }
        });
    }

    private void handleAskCanceled(InetAddress remote) {
        mParent.handleAskCanceled(remote.getHostAddress());
    }

    private void handleUpload(InetAddress remote, InputStream request,
                              final NSDictionaryHttpServerResponse response) {
        mParent.handleUpload(remote.getHostAddress(), request, result -> {
            if (result != null) {
                response.send(200);
            } else {
                response.send(401);
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
        public final void onRequest(final AsyncHttpServerRequest request,
                                    final AsyncHttpServerResponse response) {
            Log.d(TAG, "Request: " + request.getMethod() + " " + request.getPath());

            final AsyncSSLSocketWrapper socketWrapper = (AsyncSSLSocketWrapper) request.getSocket();
            final AsyncNetworkSocket socket = (AsyncNetworkSocket) socketWrapper.getSocket();
            final InetAddress address = socket.getRemoteAddress().getAddress();

            final UnknownRequestBody body = (UnknownRequestBody) request.getBody();
            final DataEmitter emitter = body.getEmitter();

            final Buffer buffer = new Buffer();

            socket.setClosedCallback(ex -> onCanceled(address));

            emitter.setDataCallback((emitter1, bb) -> buffer.write(bb.getAllByteArray()));

            request.setEndCallback(ex -> {
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
                            final Buffer buffer1 = new Buffer();
                            PropertyListParser.saveAsBinary(res, buffer1.outputStream());
                            response.send(MIME_OCTET_STREAM, buffer1.readByteArray());
                        } catch (Exception e) {
                            Log.e(TAG, "Failed serializing response", e);
                            response.code(500).end();
                        }
                    }
                });
            });
        }

        protected abstract void onRequest(InetAddress remote, NSDictionary request,
                                          NSDictionaryHttpServerResponse response);

        protected void onCanceled(InetAddress remote) {
        }

    }

    private abstract class InputStreamHttpServerRequestCallback implements HttpServerRequestCallback {

        @Override
        public final void onRequest(AsyncHttpServerRequest request,
                                    final AsyncHttpServerResponse response) {
            Log.d(TAG, "Request: " + request.getMethod() + " " + request.getPath());

            final AsyncSSLSocketWrapper socketWrapper = (AsyncSSLSocketWrapper) request.getSocket();
            final AsyncNetworkSocket socket = (AsyncNetworkSocket) socketWrapper.getSocket();
            final InetAddress address = socket.getRemoteAddress().getAddress();

            final UnknownRequestBody body = (UnknownRequestBody) request.getBody();
            final DataEmitter emitter = body.getEmitter();

            final Pipe pipe = new Pipe(Long.MAX_VALUE);

            emitter.setDataCallback((emitter1, bb) -> {
                try (final Buffer buffer = new Buffer()) {
                    buffer.write(bb.getAllByteArray());
                    bb.recycle();
                    pipe.sink().write(buffer, buffer.size());
                } catch (IOException e) {
                    Log.e(TAG, "Failed receiving upload", e);
                    socketWrapper.close();
                }
            });

            request.setEndCallback(ex -> {
                try {
                    pipe.sink().flush();
                    pipe.sink().close();
                } catch (IOException e) {
                    Log.e(TAG, "Failed receiving upload", e);
                    response.code(500).end();
                }
            });

            onRequest(address, Okio.buffer(pipe.source()).inputStream(), new NSDictionaryHttpServerResponse() {
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

        protected abstract void onRequest(InetAddress remote, InputStream request,
                                          NSDictionaryHttpServerResponse response);

    }

}
