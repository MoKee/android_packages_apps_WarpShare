package org.mokee.warpshare.airdrop;

import android.content.Context;
import android.util.Log;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;

import org.mokee.warpshare.ResolvedUri;

import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okio.Buffer;

public class AirDropManager {

    public static final int STATUS_OK = 0;
    public static final int STATUS_NO_BLUETOOTH = 1;
    public static final int STATUS_NO_WIFI = 2;

    private static final String TAG = "AirDropManager";

    private static final String INTERFACE_NAME = "wlan0";

    private final AirDropConfigManager mConfigManager;

    private final AirDropBleController mBleController;
    private final AirDropNsdController mNsdController;

    private final AirDropClient mClient;

    private final HashMap<String, Peer> mPeers = new HashMap<>();

    private DiscoveryListener mDiscoveryListener;

    public AirDropManager(Context context) {
        mBleController = new AirDropBleController(context);
        mNsdController = new AirDropNsdController(context, this);

        mConfigManager = new AirDropConfigManager(context, mBleController);

        final AirDropTrustManager trustManager = new AirDropTrustManager(context);

        mClient = new AirDropClient(trustManager);
    }

    public int ready() {
        if (!mBleController.ready()) {
            return STATUS_NO_BLUETOOTH;
        }

        if (!checkNetwork()) {
            return STATUS_NO_WIFI;
        }

        return STATUS_OK;
    }

    public void startDiscover(DiscoveryListener discoveryListener) {
        if (ready() != STATUS_OK) {
            return;
        }

        mDiscoveryListener = discoveryListener;

        mBleController.triggerDiscoverable();
        mNsdController.startDiscover();
    }

    public void stopDiscover() {
        mBleController.stop();
        mNsdController.stopDiscover();
    }

    private boolean checkNetwork() {
        NetworkInterface iface = null;
        try {
            iface = NetworkInterface.getByName(INTERFACE_NAME);
        } catch (SocketException e) {
            Log.e(TAG, "Failed getting " + INTERFACE_NAME, e);
        }

        mClient.setNetworkInterface(iface);

        if (iface == null) {
            Log.e(TAG, "Cannot get " + INTERFACE_NAME);
            return false;
        }

        return true;
    }

    void onServiceResolved(final String id, final String url) {
        final NSDictionary req = new NSDictionary();

        mClient.post(url + "/Discover", req, new AirDropClient.AirDropClientCallback() {
            @Override
            public void onFailure(Exception e) {
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

                mDiscoveryListener.onAirDropPeerFound(peer);
            }
        });
    }

    void onServiceLost(String id) {
        final Peer peer = mPeers.remove(id);
        if (peer != null) {
            mDiscoveryListener.onAirDropPeerDisappeared(peer);
        }
    }

    public void ask(final Peer peer, List<ResolvedUri> uris, final AskCallback callback) {
        final NSDictionary req = new NSDictionary();
        req.put("SenderID", mConfigManager.getId());
        req.put("SenderComputerName", mConfigManager.getName());
        req.put("BundleID", "com.apple.finder");
        req.put("ConvertMediaFormats", false);

        final List<NSDictionary> files = new ArrayList<>();
        for (ResolvedUri uri : uris) {
            final NSDictionary file = new NSDictionary();
            file.put("FileName", uri.name());
            file.put("FileType", "public.content");
            file.put("FileBomPath", uri.path());
            file.put("FileIsDirectory", false);
            file.put("ConvertMediaFormats", 0);
            files.add(file);
        }

        req.put("Files", files);

        mClient.post(peer.url + "/Ask", req, new AirDropClient.AirDropClientCallback() {
            @Override
            public void onFailure(Exception e) {
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
        final Buffer archive = new Buffer();

        try {
            AirDropArchiveUtil.pack(uris, archive.outputStream());
        } catch (IOException e) {
            Log.e(TAG, "Failed to pack upload payload: " + peer.id, e);
            callback.onUploadResult(false);
            return;
        }

        mClient.post(peer.url + "/Upload", archive.inputStream(), new AirDropClient.AirDropClientCallback() {
            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Failed to upload: " + peer.id, e);
                callback.onUploadResult(false);
            }

            @Override
            public void onResponse(NSDictionary response) {
                callback.onUploadResult(true);
            }
        });
    }

    public interface DiscoveryListener {

        void onAirDropPeerFound(Peer peer);

        void onAirDropPeerDisappeared(Peer peer);

    }

    public interface AskCallback {

        void onAskResult(boolean accepted);

    }

    public interface UploadCallback {

        void onUploadResult(boolean done);

    }

    public class Peer {

        public final String id;
        public final String name;

        final String url;

        Peer(String id, String name, String url) {
            this.id = id;
            this.name = name;
            this.url = url;
        }

    }

}
