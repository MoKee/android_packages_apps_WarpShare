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

    private static final String[] INTERFACES = new String[]{"wlan1", "wlan0"};

    private final Object mLock = new Object();

    private NetworkInterface mInterface;
    private InetAddress mLocalAddress;

    private void getLocalAddressInternal() {
        NetworkInterface iface = null;
        InetAddress address = null;

        for (String name : INTERFACES) {
            try {
                iface = NetworkInterface.getByName(name);
            } catch (SocketException e) {
                Log.w(TAG, "Failed getting interface " + name, e);
                continue;
            }
            if (iface == null) {
                Log.w(TAG, "Failed getting interface " + name);
                continue;
            }

            address = null;

            final Enumeration<InetAddress> addresses = iface.getInetAddresses();
            Inet6Address address6 = null;
            Inet4Address address4 = null;
            while (addresses.hasMoreElements()) {
                final InetAddress addr = addresses.nextElement();
                if (address6 == null && addr instanceof Inet6Address) {
                    try {
                        // Recreate a non-scoped address since we are going to advertise it out
                        address6 = (Inet6Address) Inet6Address.getByAddress(null, addr.getAddress());
                    } catch (UnknownHostException ignored) {
                    }
                } else if (address4 == null && addr instanceof Inet4Address) {
                    address4 = (Inet4Address) addr;
                }
            }

            if (address4 != null) {
                address = address4;
            } else if (address6 != null) {
                address = address6;
            }

            if (address != null) {
                break;
            }
        }

        if (iface == null) {
            Log.e(TAG, "No available interface found");
            mInterface = null;
            mLocalAddress = null;
        } else if (address == null) {
            Log.e(TAG, "No address available for interface " + iface.getName());
            mInterface = null;
            mLocalAddress = null;
        } else {
            Log.d(TAG, "Found available interface " + iface.getName() + ", " + address.getHostAddress());
            mInterface = iface;
            mLocalAddress = address;
        }
    }

    boolean ready() {
        synchronized (mLock) {
            getLocalAddressInternal();
            return mInterface != null && mLocalAddress != null;
        }
    }

    NetworkInterface getInterface() {
        synchronized (mLock) {
            getLocalAddressInternal();
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
