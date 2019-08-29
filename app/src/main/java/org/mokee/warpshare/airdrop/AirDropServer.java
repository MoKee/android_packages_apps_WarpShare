package org.mokee.warpshare.airdrop;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.dd.plist.NSDictionary;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.parsers.ParserConfigurationException;

import fi.iki.elonen.NanoHTTPD;
import okio.Buffer;

import static fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT;
import static fi.iki.elonen.NanoHTTPD.Response.Status.INTERNAL_ERROR;
import static fi.iki.elonen.NanoHTTPD.Response.Status.NOT_FOUND;
import static fi.iki.elonen.NanoHTTPD.Response.Status.OK;
import static fi.iki.elonen.NanoHTTPD.Response.Status.UNAUTHORIZED;
import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

class AirDropServer {

    private static final String TAG = "AirDropServer";

    private static final int PORT_AUTO = 0;

    private static final String MIME_OCTET_STREAM = "application/octet-stream";

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final AirDropTrustManager mTrustManager;
    private final AirDropManager mParent;

    private HandlerThread mServerThread;
    private Handler mServerHandler;

    private NanoHTTPD mServer;

    AirDropServer(AirDropTrustManager trustManager, AirDropManager parent) {
        mTrustManager = trustManager;
        mParent = parent;
    }

    int start(String host) throws IOException {
        mServer = new ServerImpl(host);
        mServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);

        final int port = mServer.getListeningPort();
        Log.d(TAG, "Server running at " + host + ":" + port);

        mServerThread = new HandlerThread("receiver");
        mServerThread.start();

        mServerHandler = new Handler(mServerThread.getLooper());

        return port;
    }

    void stop() {
        if (mServerHandler != null) {
            mServerHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mServer != null) {
                        mServer.stop();
                    }

                    mServerHandler.removeCallbacks(null);

                    mServer = null;
                    mServerHandler = null;
                    mServerThread = null;
                }
            });
            mServerThread.quitSafely();
        }
    }

    private NanoHTTPD.Response handleDiscover(NanoHTTPD.IHTTPSession session) throws IOException {
        final NSDictionary request;
        try {
            request = (NSDictionary) PropertyListParser.parse(session.getInputStream());
        } catch (PropertyListFormatException | SAXException | ParserConfigurationException | ParseException e) {
            throw new IOException(e);
        }

        final String ip = session.getHeaders().get("http-client-ip");

        final AtomicReference<NSDictionary> ref = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mParent.handleDiscover(ip, request, new ResultCallback() {
                    @Override
                    public void call(NSDictionary result) {
                        ref.set(result);
                        latch.countDown();
                    }
                });
            }
        });

        try {
            latch.wait();
        } catch (InterruptedException e) {
            return newFixedLengthResponse(UNAUTHORIZED, MIME_PLAINTEXT, null);
        }

        final NSDictionary response = ref.get();
        if (response == null) {
            return newFixedLengthResponse(UNAUTHORIZED, MIME_PLAINTEXT, null);
        } else {
            final Buffer buffer = new Buffer();
            PropertyListParser.saveAsBinary(response, buffer.outputStream());
            return newFixedLengthResponse(OK, MIME_OCTET_STREAM,
                    buffer.inputStream(), buffer.size());
        }
    }

    private NanoHTTPD.Response handleAsk(NanoHTTPD.IHTTPSession session) throws IOException {
        final NSDictionary request;
        try {
            request = (NSDictionary) PropertyListParser.parse(session.getInputStream());
        } catch (PropertyListFormatException | SAXException | ParserConfigurationException | ParseException e) {
            throw new IOException(e);
        }

        final String ip = session.getHeaders().get("http-client-ip");

        final AtomicReference<NSDictionary> ref = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mParent.handleAsk(ip, request, new ResultCallback() {
                    @Override
                    public void call(NSDictionary result) {
                        ref.set(result);
                        latch.countDown();
                    }
                });
            }
        });

        try {
            latch.wait();
        } catch (InterruptedException e) {
            return newFixedLengthResponse(UNAUTHORIZED, MIME_PLAINTEXT, null);
        }

        final NSDictionary response = ref.get();
        if (response == null) {
            return newFixedLengthResponse(UNAUTHORIZED, MIME_PLAINTEXT, null);
        } else {
            final Buffer buffer = new Buffer();
            PropertyListParser.saveAsBinary(response, buffer.outputStream());
            return newFixedLengthResponse(OK, MIME_OCTET_STREAM,
                    buffer.inputStream(), buffer.size());
        }
    }

    private NanoHTTPD.Response handleUpload(NanoHTTPD.IHTTPSession session) {
        final InputStream stream = session.getInputStream();

        final String ip = session.getHeaders().get("http-client-ip");

        final AtomicReference<NSDictionary> ref = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mParent.handleUpload(ip, stream, new ResultCallback() {
                    @Override
                    public void call(NSDictionary result) {
                        ref.set(result);
                        latch.countDown();
                    }
                });
            }
        });

        try {
            latch.wait();
        } catch (InterruptedException e) {
            return newFixedLengthResponse(UNAUTHORIZED, MIME_PLAINTEXT, null);
        }

        final NSDictionary response = ref.get();
        if (response == null) {
            return newFixedLengthResponse(UNAUTHORIZED, MIME_PLAINTEXT, null);
        } else {
            return newFixedLengthResponse(OK, MIME_OCTET_STREAM, null);
        }
    }

    public interface ResultCallback {

        void call(NSDictionary result);

    }

    private class ServerImpl extends NanoHTTPD {

        ServerImpl(String hostname) {
            super(hostname, PORT_AUTO);
            makeSecure(mTrustManager.getSslServerSocketFactory(), null);
        }

        @Override
        public Response serve(IHTTPSession session) {
            final Method method = session.getMethod();
            final String uri = session.getUri();

            Log.d(TAG, method + " " + uri);

            try {
                if (method == Method.POST && uri.equals("/Discover")) {
                    return handleDiscover(session);
                } else if (method == Method.POST && uri.equals("/Ask")) {
                    return handleAsk(session);
                } else if (method == Method.POST && uri.equals("/Upload")) {
                    return handleUpload(session);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error handling request: " + uri, e);
                return newFixedLengthResponse(INTERNAL_ERROR, MIME_PLAINTEXT, "");
            }

            Log.w(TAG, "Unhandled: " + method + " " + uri);
            return newFixedLengthResponse(NOT_FOUND, MIME_PLAINTEXT, "");
        }

    }

}
