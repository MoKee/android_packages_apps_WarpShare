package org.mokee.fileshare.airdrop;

import android.content.Context;
import android.util.Log;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;

import java.io.IOException;

public class AirDropManager {

    private static final String TAG = "AirDropManager";

    public static final int STATUS_OK = 0;
    public static final int STATUS_NO_BLUETOOTH = 1;
    public static final int STATUS_NO_WIFI = 2;

    private final Callback mCallback;

    private final AirDropBleController mBleController;
    private final AirDropNsdController mNsdController;

    private final AirDropClient mClient;

    public AirDropManager(Context context, Callback callback) {
        mCallback = callback;

        mBleController = new AirDropBleController(context);
        mNsdController = new AirDropNsdController(context, this);

        mClient = new AirDropClient(context);
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

    void onServiceResolved(final String id, String url) {
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

                mCallback.onAirDropPeerFound(id, nameNode.toJavaObject(String.class));
            }
        });
    }

    void onServiceLost(String id) {
        mCallback.onAirDropPeerDisappeared(id);
    }

    public interface Callback {

        void onAirDropPeerFound(String id, String name);

        void onAirDropPeerDisappeared(String id);

    }

}
