/*
 * Copyright (C) 2019 The MoKee Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mokee.warpshare.airdrop;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

class AirDropNsdController {

    private static final String TAG = "AirDropNsdController";

    private static final String SERVICE_TYPE = "_airdrop._tcp.local.";

    private static final int FLAG_SUPPORTS_MIXED_TYPES = 0x08;
    private static final int FLAG_SUPPORTS_DISCOVER_MAYBE = 0x80;

    private final WifiManager.MulticastLock mMulticastLock;
    private final AirDropConfigManager mConfigManager;
    private final AirDropManager mParent;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final ServiceListener mDiscoveryListener = new ServiceListener() {
        @Override
        public void serviceAdded(ServiceEvent event) {
        }

        @Override
        public void serviceRemoved(ServiceEvent event) {
            handleServiceLost(event.getInfo());
        }

        @Override
        public void serviceResolved(ServiceEvent event) {
            handleServiceResolved(event.getInfo());
        }
    };

    private JmDNS mJmdns;

    private HandlerThread mNetworkingThread;
    private Handler mNetworkingHandler;

    @SuppressWarnings("ConstantConditions")
    AirDropNsdController(Context context, AirDropConfigManager configManager, AirDropManager parent) {
        final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mMulticastLock = wifiManager.createMulticastLock(TAG);
        mMulticastLock.setReferenceCounted(false);

        mNetworkingThread = new HandlerThread("networking");
        mNetworkingThread.start();

        mNetworkingHandler = new Handler(mNetworkingThread.getLooper());

        mConfigManager = configManager;
        mParent = parent;
    }

    void destroy() {
        mNetworkingHandler.post(() -> {
            if (mJmdns != null) {
                try {
                    mJmdns.close();
                } catch (IOException ignored) {
                }
                mJmdns = null;
            }
            mNetworkingHandler.removeCallbacksAndMessages(null);
            mNetworkingThread.quit();
        });
    }

    private void createJmdns(InetAddress address) {
        // TODO: should handle address change
        if (mJmdns == null) {
            try {
                mJmdns = JmDNS.create(address);
            } catch (IOException e) {
                throw new RuntimeException("Failed creating JmDNS instance", e);
            }
        }
    }

    void startDiscover(final InetAddress address) {
        mNetworkingHandler.post(() -> {
            mMulticastLock.acquire();
            createJmdns(address);
            mJmdns.addServiceListener(SERVICE_TYPE, mDiscoveryListener);
        });
    }

    void stopDiscover() {
        mNetworkingHandler.post(() -> {
            if (mJmdns != null) {
                mJmdns.removeServiceListener(SERVICE_TYPE, mDiscoveryListener);
            }
            mMulticastLock.release();
        });
    }

    void publish(final InetAddress address, final int port) {
        mNetworkingHandler.post(() -> {
            createJmdns(address);

            final Map<String, String> props = new HashMap<>();
            props.put("flags", Integer.toString(
                    FLAG_SUPPORTS_MIXED_TYPES | FLAG_SUPPORTS_DISCOVER_MAYBE));

            final ServiceInfo serviceInfo = ServiceInfo.create(SERVICE_TYPE,
                    mConfigManager.getId(), port, 0, 0, props);
            Log.d(TAG, "Publishing " + serviceInfo);
            try {
                mJmdns.registerService(serviceInfo);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    void unpublish() {
        mNetworkingHandler.post(() -> mJmdns.unregisterAllServices());
    }

    private void handleServiceLost(ServiceInfo serviceInfo) {
        Log.d(TAG, "Disappeared: " + serviceInfo.getName());
        postServiceLost(serviceInfo.getName());
    }

    private void handleServiceResolved(ServiceInfo serviceInfo) {
        if (mConfigManager.getId().equals(serviceInfo.getName())) {
            return;
        }

        Log.d(TAG, "Resolved: " + serviceInfo.getName() +
                ", flags=" + serviceInfo.getPropertyString("flags"));

        final Inet4Address[] addresses = serviceInfo.getInet4Addresses();
        if (addresses.length > 0) {
            final String url = String.format(Locale.US, "https://%s:%d",
                    addresses[0].getHostAddress(), serviceInfo.getPort());

            postServiceResolved(serviceInfo.getName(), url);
        } else {
            Log.w(TAG, "No IPv4 address available, ignored: " + serviceInfo.getName());
        }
    }

    private void postServiceResolved(final String id, final String url) {
        mHandler.post(() -> mParent.onServiceResolved(id, url));
    }

    private void postServiceLost(final String id) {
        mHandler.post(() -> mParent.onServiceLost(id));
    }

}
