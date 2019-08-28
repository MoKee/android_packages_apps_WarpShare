package org.mokee.fileshare.airdrop;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;
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

    private final HashMap<String, Peer> mPeers = new HashMap<>();

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

                final Peer peer = new Peer(id, nameNode.toJavaObject(String.class), url);
                mPeers.put(id, peer);

                mCallback.onAirDropPeerFound(peer);
            }
        });
    }

    void onServiceLost(String id) {
        final Peer peer = mPeers.remove(id);
        if (peer != null) {
            mCallback.onAirDropPeerDisappeared(peer);
        }
    }

    public void ask(final Peer peer, List<ResolvedUri> uris, final AskCallback callback) {
        final NSDictionary req = new NSDictionary();
        req.put("SenderID", mPref.getString("id", null));
        req.put("SenderComputerName", mBleController.getName());
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

        mClient.post(peer.url + "/Ask", req, new AirDropClient.AirDropClientCallback() {
            @Override
            public void onFailure(IOException e) {
                Log.w(TAG, "Failed to ask: " + peer.id, e);
                callback.onAskResult(false);
            }

            @Override
            public void onResponse(NSDictionary response) {
                Log.d(TAG, "Ask accepted");
                callback.onAskResult(true);
            }
        });
    }

    public void upload(final Peer peer, List<ResolvedUri> uris, final UploadCallback callback) {
        mClient.post(peer.url + "/Upload", uris, new AirDropClient.AirDropClientCallback() {
            @Override
            public void onFailure(IOException e) {
                Log.w(TAG, "Failed to upload: " + peer.id, e);
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

    public static class Peer implements Parcelable {

        public static final Creator<Peer> CREATOR = new Creator<Peer>() {
            @Override
            public Peer createFromParcel(Parcel in) {
                return new Peer(in);
            }

            @Override
            public Peer[] newArray(int size) {
                return new Peer[size];
            }
        };

        public final String id;
        public final String name;

        final String url;

        Peer(String id, String name, String url) {
            this.id = id;
            this.name = name;
            this.url = url;
        }

        Peer(Parcel in) {
            id = in.readString();
            name = in.readString();
            url = in.readString();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(id);
            dest.writeString(name);
            dest.writeString(url);
        }
    }

    public interface Callback {

        void onAirDropPeerFound(Peer peer);

        void onAirDropPeerDisappeared(Peer peer);

    }

    public interface AskCallback {

        void onAskResult(boolean accepted);

    }

    public interface UploadCallback {

        void onUploadResult(boolean done);

    }

}
