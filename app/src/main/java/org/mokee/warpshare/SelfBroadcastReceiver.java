package org.mokee.warpshare;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

abstract class SelfBroadcastReceiver extends BroadcastReceiver {

    private final Object mLock = new Object();

    private final IntentFilter mIntentFilter = new IntentFilter();

    private boolean mRegistered = false;

    SelfBroadcastReceiver(String... actions) {
        for (String action : actions) {
            mIntentFilter.addAction(action);
        }
    }

    void register(Context context) {
        synchronized (mLock) {
            if (!mRegistered) {
                context.registerReceiver(this, mIntentFilter);
                mRegistered = true;
            }
        }
    }

    void unregister(Context context) {
        synchronized (mLock) {
            if (mRegistered) {
                context.unregisterReceiver(this);
                mRegistered = false;
            }
        }
    }

}
