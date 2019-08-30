package org.mokee.warpshare.airdrop;

import android.util.Log;

import com.dd.plist.NSDictionary;
import com.dd.plist.PropertyListParser;

import java.io.IOException;

import fi.iki.elonen.NanoHTTPD;
import okio.Buffer;

import static fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT;
import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

class AirDropServer {

    private static final String TAG = "AirDropServer";

    private static final int PORT_AUTO = 0;

    private static final String MIME_OCTET_STREAM = "application/octet-stream";

    private final AirDropTrustManager mTrustManager;
    private final AirDropConfigManager mConfigManager;

    private NanoHTTPD mServer;

    AirDropServer(AirDropTrustManager trustManager, AirDropConfigManager configManager) {
        mTrustManager = trustManager;
        mConfigManager = configManager;
    }

    int start(String host) throws IOException {
        mServer = new ServerImpl(host);
        mServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);

        final int port = mServer.getListeningPort();
        Log.d(TAG, "Server running at " + host + ":" + port);

        return port;
    }

    void stop() {
        if (mServer != null) {
            mServer.stop();
            mServer = null;
        }
    }

    private NanoHTTPD.Response handleDiscover(NanoHTTPD.IHTTPSession session) throws IOException {
        final NSDictionary response = new NSDictionary();
        response.put("ReceiverComputerName", mConfigManager.getName());

        response.put("ReceiverMediaCapabilities", "{\"Version\":1,\"Codecs\":{\"hvc1\":{\"Profiles\":{\"VTPerProfileSupport\":{\"1\":{\"VTMaxPlaybackLevel\":186,\"VTIsHardwareAccelerated\":true,\"VTMaxDecodeLevel\":186},\"2\":{\"VTMaxPlaybackLevel\":186,\"VTIsHardwareAccelerated\":true,\"VTMaxDecodeLevel\":186},\"3\":{\"VTMaxPlaybackLevel\":186,\"VTIsHardwareAccelerated\":true,\"VTMaxDecodeLevel\":186}},\"VTSupportedProfiles\":[1,2,3]}}},\"ContainerFormats\":{\"public.heif-standard\":{\"HeifSubtypes\":[\"public.avci\",\"public.heic\",\"public.heif\"]}},\"Vendor\":{\"com.apple\":{\"OSVersion\":[10,14,6],\"OSBuildVersion\":\"18G87\",\"LivePhotoFormatVersion\":\"1\"}}}".getBytes());

        response.put("ReceiverModelName", "WarpShare");

        final Buffer buffer = new Buffer();
        PropertyListParser.saveAsBinary(response, buffer.outputStream());

        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, MIME_OCTET_STREAM,
                buffer.inputStream(), buffer.size());
    }

    private NanoHTTPD.Response handleAsk(NanoHTTPD.IHTTPSession session) {
        return newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, MIME_PLAINTEXT, "");
    }

    private NanoHTTPD.Response handleUpload(NanoHTTPD.IHTTPSession session) {
        return newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, MIME_PLAINTEXT, "");
    }

    private class ServerImpl extends NanoHTTPD {

        ServerImpl(String hostname) {
            super(hostname, 8770);
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
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "");
            }

            Log.w(TAG, "Unhandled: " + method + " " + uri);
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "");
        }

    }

}
