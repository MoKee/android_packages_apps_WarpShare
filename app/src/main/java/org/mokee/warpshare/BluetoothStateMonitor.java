package org.mokee.warpshare;

import android.bluetooth.BluetoothAdapter;

abstract class BluetoothStateMonitor extends SelfBroadcastReceiver {
    BluetoothStateMonitor() {
        super(BluetoothAdapter.ACTION_STATE_CHANGED);
    }
}
