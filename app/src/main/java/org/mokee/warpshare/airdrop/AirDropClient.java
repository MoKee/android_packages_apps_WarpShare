package org.mokee.warpshare.airdrop;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.dd.plist.NSDictionary;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.body.StreamBody;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.xml.parsers.ParserConfigurationException;

import okio.Buffer;

class AirDropClient {

    private static final String TAG = "AirDropClient";

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private AsyncHttpClient mHttpClient;

    AirDropClient(AirDropTrustManager trustManager) {
        mHttpClient = AsyncHttpClient.getDefaultInstance();
        mHttpClient.getSSLSocketMiddleware().setSSLContext(trustManager.getSSLContext());
        mHttpClient.getSSLSocketMiddleware().setTrustManagers(trustManager.getTrustManagers());
        mHttpClient.getSSLSocketMiddleware().setHostnameVerifier(new HostnameVerifier() {
            @SuppressLint("BadHostnameVerifier")
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        });
    }

    void post(final String url, NSDictionary body, AirDropClientCallback callback) {
        final Buffer buffer = new Buffer();

        try {
            PropertyListParser.saveAsBinary(body, buffer.outputStream());
        } catch (IOException e) {
            callback.onFailure(e);
            return;
        }

        post(url, "application/octet-stream", buffer.inputStream(), callback);
        buffer.close();
    }

    void post(final String url, final InputStream input, AirDropClientCallback callback) {
        post(url, "application/x-cpio", input, callback);
    }

    private void post(final String url, String contentType, InputStream input, final AirDropClientCallback callback) {
        final AsyncHttpPost post = new AsyncHttpPost(url);
        post.setHeader("Content-Type", contentType);
        post.setBody(new StreamBody(input, -1));

        mHttpClient.executeByteBufferList(post, new AsyncHttpClient.DownloadCallback() {
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse source, ByteBufferList result) {
                if (e != null) {
                    Log.e(TAG, "Request failed: " + url, e);
                    postFailure(callback, e);
                    return;
                }

                final int statusCode = source.code();
                if (statusCode != 200) {
                    postFailure(callback, new IOException("Request failed: " + statusCode));
                    return;
                }

                try {
                    NSDictionary root = (NSDictionary) PropertyListParser.parse(result.getAllByteArray());
                    postResponse(callback, root);
                } catch (PropertyListFormatException | ParseException | ParserConfigurationException | SAXException | IOException ex) {
                    postFailure(callback, ex);
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

    private void postFailure(final AirDropClientCallback callback, final Exception e) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onFailure(e);
            }
        });
    }

    interface AirDropClientCallback {

        void onFailure(Exception e);

        void onResponse(NSDictionary response);

    }

}
