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

package org.mokee.warpshare;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

public class ConfigManager {

    public static final String KEY_NAME = "name";

    public static final String KEY_DISCOVERABLE = "discoverable";

    private static final String TAG = "ConfigManager";

    private final Context mContext;
    private final SharedPreferences mPref;

    public ConfigManager(Context context) {
        mContext = context;
        mPref = PreferenceManager.getDefaultSharedPreferences(context);
    }

    private String getBluetoothAdapterName() {
        final BluetoothManager manager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) {
            return null;
        }

        final BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null) {
            return null;
        }

        return adapter.getName();
    }

    public String getDefaultName() {
        final String name = getBluetoothAdapterName();
        return TextUtils.isEmpty(name) ? "Android" : name;
    }

    public String getNameWithoutDefault() {
        return mPref.getString(KEY_NAME, "");
    }

    public String getName() {
        final String name = getNameWithoutDefault();
        return TextUtils.isEmpty(name) ? getDefaultName() : name;
    }

    public boolean isDiscoverable() {
        return mPref.getBoolean(KEY_DISCOVERABLE, false);
    }

}
