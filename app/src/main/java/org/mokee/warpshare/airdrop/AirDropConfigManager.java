package org.mokee.warpshare.airdrop;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import java.util.Random;

import okio.ByteString;

class AirDropConfigManager {

    private static final String TAG = "AirDropConfigManager";

    private final SharedPreferences mPref;
    private final AirDropBleController mBleController;

    @SuppressLint("ApplySharedPref")
    AirDropConfigManager(Context context, AirDropBleController bleController) {
        mPref = context.getSharedPreferences("airdrop", Context.MODE_PRIVATE);
        if (!mPref.contains("id")) {
            mPref.edit().putString("id", generateId()).commit();
            Log.d(TAG, "Generate id: " + mPref.getString("id", null));
        }

        mBleController = bleController;
    }

    private String generateId() {
        byte[] id = new byte[6];
        new Random().nextBytes(id);
        return ByteString.of(id).hex();
    }

    String getId() {
        return mPref.getString("id", null);
    }

    String getName() {
        final String name = mPref.getString("name", mBleController.getName());
        return TextUtils.isEmpty(name) ? "Android" : name;
    }

}
