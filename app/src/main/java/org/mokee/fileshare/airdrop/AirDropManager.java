package org.mokee.fileshare.airdrop;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;

import org.mokee.fileshare.ResolvedUri;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import okio.ByteString;

public class AirDropManager {

    private static final String TAG = "AirDropManager";

    public static final int STATUS_OK = 0;
    public static final int STATUS_NO_BLUETOOTH = 1;
    public static final int STATUS_NO_WIFI = 2;

    private final Callback mCallback;

    private final AirDropBleController mBleController;
    private final AirDropNsdController mNsdController;

    private final AirDropClient mClient;

    private final SharedPreferences mPref;

    private final HashMap<String, String> mPeers = new HashMap<>();

    @SuppressLint("ApplySharedPref")
    public AirDropManager(Context context, Callback callback) {
        mCallback = callback;

        mBleController = new AirDropBleController(context);
        mNsdController = new AirDropNsdController(context, this);

        mClient = new AirDropClient(context);

        mPref = context.getSharedPreferences("airdrop", Context.MODE_PRIVATE);
        if (!mPref.contains("id")) {
            mPref.edit().putString("id", generateId()).commit();
            Log.d(TAG, "Generate id: " + mPref.getString("id", null));
        }
    }

    public int ready() {
        if (!mBleController.ready()) {
            return STATUS_NO_BLUETOOTH;
        }

        return STATUS_OK;
    }

    public void startDiscover() {
        if (ready() != STATUS_OK) {
            return;
        }

        mBleController.triggerDiscoverable();
        mNsdController.startDiscover();
    }

    public void stopDiscover() {
        mBleController.stop();
        mNsdController.stopDiscover();
    }

    void onServiceResolved(final String id, final String url) {
        final NSDictionary req = new NSDictionary();

        mClient.post(url + "/Discover", req, new AirDropClient.AirDropClientCallback() {
            @Override
            public void onFailure(IOException e) {
                Log.w(TAG, "Failed to discover: " + id, e);
            }

            @Override
            public void onResponse(NSDictionary response) {
                NSObject nameNode = response.get("ReceiverComputerName");
                if (nameNode == null) {
                    Log.w(TAG, "Name is null: " + id);
                    return;
                }

                mPeers.put(id, url);

                mCallback.onAirDropPeerFound(id, nameNode.toJavaObject(String.class));
            }
        });
    }

    void onServiceLost(String id) {
        mPeers.remove(id);
        mCallback.onAirDropPeerDisappeared(id);
    }

    public void ask(final String id, List<ResolvedUri> uris, final AskCallback callback) {
        final String url = mPeers.get(id);

        final NSDictionary req = new NSDictionary();
        req.put("SenderID", mPref.getString("id", null));
        req.put("SenderComputerName", "Android");
        req.put("BundleID", "com.apple.finder");
        req.put("ConvertMediaFormats", false);

        final List<NSDictionary> files = new ArrayList<>();
        for (ResolvedUri uri : uris) {
            final NSDictionary file = new NSDictionary();
            file.put("FileName", uri.name);
            file.put("FileType", "public.content");
            file.put("FileBomPath", uri.path);
            file.put("FileIsDirectory", false);
            file.put("ConvertMediaFormats", 0);
            files.add(file);
        }

        req.put("Files", files);

        mClient.post(url + "/Ask", req, new AirDropClient.AirDropClientCallback() {
            @Override
            public void onFailure(IOException e) {
                Log.w(TAG, "Failed to ask: " + id, e);
                callback.onAskResult(false);
            }

            @Override
            public void onResponse(NSDictionary response) {
                Log.d(TAG, "Ask accepted");
                callback.onAskResult(true);
            }
        });
    }

    public void upload(final String id, List<ResolvedUri> uris, final UploadCallback callback) {
        final String url = mPeers.get(id);
        mClient.post(url + "/Upload", uris, new AirDropClient.AirDropClientCallback() {
            @Override
            public void onFailure(IOException e) {
                Log.w(TAG, "Failed to upload: " + id, e);
                callback.onUploadResult(false);
            }

            @Override
            public void onResponse(NSDictionary response) {
                callback.onUploadResult(true);
            }
        });
    }

    private String generateId() {
        byte[] id = new byte[6];
        new Random().nextBytes(id);
        return ByteString.of(id).hex();
    }

    public interface Callback {

        void onAirDropPeerFound(String id, String name);

        void onAirDropPeerDisappeared(String id);

    }

    public interface AskCallback {

        void onAskResult(boolean accepted);

    }

    public interface UploadCallback {

        void onUploadResult(boolean done);

    }

}
