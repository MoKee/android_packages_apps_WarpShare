package org.mokee.warpshare.airdrop;

import android.app.Service;
import android.content.Context;
import android.util.Log;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;

import org.mokee.warpshare.ResolvedUri;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;

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
    private final AirDropServer mServer;

    private final HashMap<String, Peer> mPeers = new HashMap<>();

    private DiscoveryListener mDiscoveryListener;

    private InetAddress mLocalAddress;

    public AirDropManager(Context context) {
        mBleController = new AirDropBleController(context);
        mConfigManager = new AirDropConfigManager(context, mBleController);
        mNsdController = new AirDropNsdController(context, mConfigManager, this);

        final AirDropTrustManager trustManager = new AirDropTrustManager(context);

        mClient = new AirDropClient(trustManager);
        mServer = new AirDropServer(trustManager, mConfigManager);
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

    public void startDiscoverable() {
        if (ready() != STATUS_OK) {
            return;
        }

        final int port;
        try {
            port = mServer.start(mLocalAddress.getHostAddress());
        } catch (IOException e) {
            Log.e(TAG, "Failed starting server");
            return;
        }

        mNsdController.publish(mLocalAddress, port);
    }

    public void stopDiscoverable() {
        mNsdController.unpublish();
        mServer.stop();
    }

    public void registerTrigger(Class<? extends Service> receiverService) {
        mBleController.registerTrigger(receiverService);
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

        final Enumeration<InetAddress> addresses = iface.getInetAddresses();
        Inet6Address address6 = null;
        Inet4Address address4 = null;
        while (addresses.hasMoreElements()) {
            final InetAddress address = addresses.nextElement();
            if (address6 == null && address instanceof Inet6Address) {
                try {
                    // Recreate a non-scoped address since we are going to advertise it out
                    address6 = (Inet6Address) Inet6Address.getByAddress(null, address.getAddress());
                } catch (UnknownHostException ignored) {
                }
            } else if (address4 == null && address instanceof Inet4Address) {
                address4 = (Inet4Address) address;
            }
        }

        mLocalAddress = address4 != null ? address4 : address6;

        if (mLocalAddress == null) {
            Log.e(TAG, "No address on interface " + INTERFACE_NAME);
            return false;
        }

        return true;
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
