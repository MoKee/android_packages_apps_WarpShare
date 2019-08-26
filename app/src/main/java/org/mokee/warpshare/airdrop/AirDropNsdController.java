package org.mokee.warpshare.airdrop;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.Inet6Address;
import java.util.Locale;

import static android.content.Context.NSD_SERVICE;

class AirDropNsdController {

    private static final String TAG = "AirDropNsdController";

    private static final String SERVICE_TYPE = "_airdrop._tcp";

    private final NsdManager mNsdManager;
    private final AirDropManager mParent;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final NsdManager.DiscoveryListener mDiscoveryListener = new NsdManager.DiscoveryListener() {
        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
        }

        @Override
        public void onDiscoveryStarted(String serviceType) {
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
        }

        @Override
        public void onServiceFound(NsdServiceInfo serviceInfo) {
            handleServiceFound(serviceInfo);
        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            handleServiceLost(serviceInfo);
        }
    };

    AirDropNsdController(Context context, AirDropManager parent) {
        mNsdManager = (NsdManager) context.getSystemService(NSD_SERVICE);
        mParent = parent;
    }

    void startDiscover() {
        mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    void stopDiscover() {
        mNsdManager.stopServiceDiscovery(mDiscoveryListener);
    }

    private void handleServiceFound(NsdServiceInfo serviceInfo) {
        mNsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.w(TAG, String.format(Locale.US, "Resolve failed, ignored. name=%s, err=%d",
                        serviceInfo.getServiceName(), errorCode));
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                handleServiceResolved(serviceInfo);
            }
        });
    }

    private void handleServiceLost(NsdServiceInfo serviceInfo) {
        Log.d(TAG, "Disappeared: " + serviceInfo.getServiceName());
        postServiceLost(serviceInfo.getServiceName());
    }

    private void handleServiceResolved(NsdServiceInfo serviceInfo) {
        Log.d(TAG, "Resolved: " + serviceInfo.getServiceName());

        final String url;
        if (serviceInfo.getHost() instanceof Inet6Address) {
            url = String.format(Locale.US, "https://[%s]:%d",
                    serviceInfo.getHost().getHostAddress(), serviceInfo.getPort());
        } else {
            url = String.format(Locale.US, "https://%s:%d",
                    serviceInfo.getHost().getHostAddress(), serviceInfo.getPort());
        }

        postServiceResolved(serviceInfo.getServiceName(), url);
    }

    private void postServiceResolved(final String id, final String url) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mParent.onServiceResolved(id, url);
            }
        });
    }

    private void postServiceLost(final String id) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mParent.onServiceLost(id);
            }
        });
    }

}
