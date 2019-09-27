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

import android.util.Log;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

class AirDropWlanController {

    private static final String TAG = "AirDropWlanController";

    private static final String INTERFACE_NAME = "wlan0";

    private final Object mLock = new Object();

    private NetworkInterface mInterface;
    private InetAddress mLocalAddress;

    private void getInterfaceInternal() {
        final NetworkInterface iface;
        try {
            iface = NetworkInterface.getByName(INTERFACE_NAME);
        } catch (SocketException e) {
            Log.e(TAG, "Failed getting " + INTERFACE_NAME, e);
            mInterface = null;
            return;
        }
        if (iface == null) {
            Log.e(TAG, "Cannot get " + INTERFACE_NAME);
            mInterface = null;
            return;
        }

        mInterface = iface;
    }

    private void getLocalAddressInternal() {
        getInterfaceInternal();
        if (mInterface == null) {
            mLocalAddress = null;
            return;
        }

        final Enumeration<InetAddress> addresses = mInterface.getInetAddresses();
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
        if (address4 == null && address6 == null) {
            Log.e(TAG, "Cannot get local address for " + INTERFACE_NAME);
            mLocalAddress = null;
            return;
        }

        mLocalAddress = address4 != null ? address4 : address6;
    }

    boolean ready() {
        synchronized (mLock) {
            getLocalAddressInternal();
            return mInterface != null && mLocalAddress != null;
        }
    }

    NetworkInterface getInterface() {
        synchronized (mLock) {
            getInterfaceInternal();
            if (mInterface == null) {
                return null;
            }
        }

        return mInterface;
    }

    InetAddress getLocalAddress() {
        synchronized (mLock) {
            getLocalAddressInternal();
            if (mLocalAddress == null) {
                return null;
            }
        }

        return mLocalAddress;
    }

}
