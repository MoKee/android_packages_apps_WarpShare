package org.mokee.warpshare.airdrop;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.util.Log;

import static android.content.Context.BLUETOOTH_SERVICE;

class AirDropBleController {

    private static final String TAG = "AirDropBleController";

    private static final int MANUFACTURER_ID = 0x004C;
    private static final byte[] MANUFACTURER_DATA = {
            0x05, 0x12, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00};

    private BluetoothLeAdvertiser mAdvertiser;

    private final Object mLock = new Object();

    private final Context mContext;

    private final AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            handleAdvertiseStartSuccess();
        }

        @Override
        public void onStartFailure(int errorCode) {
            handleAdvertiseStartFailure(errorCode);
        }
    };

    AirDropBleController(Context context) {
        mContext = context;
    }

    String getName() {
        final BluetoothManager manager = (BluetoothManager) mContext.getSystemService(BLUETOOTH_SERVICE);
        if (manager == null) {
            return null;
        }

        final BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            return null;
        }

        return adapter.getName();
    }

    private void getAdvertiser() {
        final BluetoothManager manager = (BluetoothManager) mContext.getSystemService(BLUETOOTH_SERVICE);
        if (manager == null) {
            return;
        }

        final BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            return;
        }

        final BluetoothLeAdvertiser advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            return;
        }

        mAdvertiser = advertiser;
    }

    boolean ready() {
        synchronized (mLock) {
            getAdvertiser();
            return mAdvertiser != null;
        }
    }

    void triggerDiscoverable() {
        synchronized (mLock) {
            getAdvertiser();
            if (mAdvertiser == null) {
                return;
            }
        }

        mAdvertiser.startAdvertising(
                new AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                        .setConnectable(true)
                        .build(),
                new AdvertiseData.Builder()
                        .addManufacturerData(MANUFACTURER_ID, MANUFACTURER_DATA)
                        .build(),
                mAdvertiseCallback);
    }

    void stop() {
        synchronized (mLock) {
            getAdvertiser();
            if (mAdvertiser == null) {
                return;
            }
        }

        mAdvertiser.stopAdvertising(mAdvertiseCallback);
    }

    private void handleAdvertiseStartSuccess() {
        Log.d(TAG, "Start advertise succeed");
    }

    private void handleAdvertiseStartFailure(int errorCode) {
        Log.e(TAG, "Start advertise failed: " + errorCode);
    }

}
