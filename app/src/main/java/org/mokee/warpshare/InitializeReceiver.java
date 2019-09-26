package org.mokee.warpshare;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.mokee.warpshare.airdrop.AirDropManager;

public class InitializeReceiver extends BroadcastReceiver {

    private static final String TAG = "InitializeReceiver";

    @Override
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    public void onReceive(Context context, Intent intent) {
        final AirDropManager airDropManager = new AirDropManager(context);
        airDropManager.registerTrigger(TriggerReceiver.getTriggerIntent(context));
        Log.d(TAG, "Initialized");
    }

}
