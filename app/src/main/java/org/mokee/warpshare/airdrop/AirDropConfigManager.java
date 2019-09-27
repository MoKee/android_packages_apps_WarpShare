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
