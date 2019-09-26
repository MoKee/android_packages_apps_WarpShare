package org.mokee.warpshare;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

public class ConfigManager {

    public static final String KEY_NAME = "name";

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

}
