package org.mokee.warpshare;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import static android.content.Context.CONNECTIVITY_SERVICE;

class WifiStateMonitor extends ConnectivityManager.NetworkCallback {

    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    private ConnectivityManager getConnectivityManager(Context context) {
        return (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
    }

    void register(Context context) {
        getConnectivityManager(context).registerNetworkCallback(new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build(), this, mMainThreadHandler);
    }

    void unregister(Context context) {
        getConnectivityManager(context).unregisterNetworkCallback(this);
        mMainThreadHandler.removeCallbacksAndMessages(null);
    }

}
