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
                address6 = (Inet6Address) address;
            } else if (address4 == null && address instanceof Inet4Address) {
                address4 = (Inet4Address) address;
            }
        }
        if (address4 == null && address6 == null) {
            Log.e(TAG, "Cannot get local address for " + INTERFACE_NAME);
            mLocalAddress = null;
            return;
        }

        mLocalAddress = address6 != null ? address6 : address4;
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

    InetAddress getUnboundLocalAddress() {
        synchronized (mLock) {
            getLocalAddressInternal();
            if (mLocalAddress == null) {
                return null;
            }
        }

        if (mLocalAddress instanceof Inet6Address) {
            try {
                return Inet6Address.getByAddress(null, mLocalAddress.getAddress());
            } catch (UnknownHostException e) {
                Log.e(TAG, "Failed to get non-scoped IPv6 address", e);
                return mLocalAddress;
            }
        } else {
            return mLocalAddress;
        }
    }

}
