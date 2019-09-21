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

    private NetworkInterface mInterface;
    private InetAddress mLocalAddress;

    private void getAddress() {
        try {
            mInterface = NetworkInterface.getByName(INTERFACE_NAME);
        } catch (SocketException e) {
            Log.e(TAG, "Failed getting " + INTERFACE_NAME, e);
            mInterface = null;
            mLocalAddress = null;
            return;
        }
        if (mInterface == null) {
            Log.e(TAG, "Cannot get " + INTERFACE_NAME);
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
        getAddress();
        return mInterface != null && mLocalAddress != null;
    }

    NetworkInterface getInterface() {
        getAddress();
        return mInterface;
    }

    InetAddress getLocalAddress() {
        getAddress();
        return mLocalAddress;
    }

}
