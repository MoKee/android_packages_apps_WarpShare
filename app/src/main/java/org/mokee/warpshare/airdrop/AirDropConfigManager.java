package org.mokee.warpshare.airdrop;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.mokee.warpshare.ConfigManager;

import java.util.Random;

import okio.ByteString;

@SuppressLint("ApplySharedPref")
class AirDropConfigManager {

    private static final String TAG = "AirDropConfigManager";

    private static final String KEY_ID = "airdrop_id";

    private final ConfigManager mParent;
    private final SharedPreferences mPref;

    @SuppressLint("ApplySharedPref")
    AirDropConfigManager(Context context) {
        mParent = new ConfigManager(context);
        mPref = PreferenceManager.getDefaultSharedPreferences(context);
        if (!mPref.contains(KEY_ID)) {
            mPref.edit().putString(KEY_ID, generateId()).commit();
            Log.d(TAG, "Generate id: " + mPref.getString(KEY_ID, null));
        }
    }

    private String generateId() {
        byte[] id = new byte[6];
        new Random().nextBytes(id);
        return ByteString.of(id).hex();
    }

    String getId() {
        return mPref.getString(KEY_ID, null);
    }

    String getName() {
        return mParent.getName();
    }

    boolean isDiscoverable() {
        return mParent.isDiscoverable();
    }

}
