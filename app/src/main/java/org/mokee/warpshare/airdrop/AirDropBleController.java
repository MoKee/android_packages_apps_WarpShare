package org.mokee.warpshare.airdrop;

import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_FIRST_MATCH;
import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_MATCH_LOST;
import static android.bluetooth.le.ScanSettings.MATCH_MODE_STICKY;
import static android.bluetooth.le.ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT;
import static android.bluetooth.le.ScanSettings.SCAN_MODE_BALANCED;
import static android.content.Context.BLUETOOTH_SERVICE;

class AirDropBleController {

    private static final String TAG = "AirDropBleController";

    private static final int MANUFACTURER_ID = 0x004C;
    private static final byte[] MANUFACTURER_DATA = {
            0x05, 0x12, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00};

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

    private BluetoothAdapter mAdapter;
    private BluetoothLeAdvertiser mAdvertiser;
    private BluetoothLeScanner mScanner;

    AirDropBleController(Context context) {
        mContext = context;
    }

    private void getAdapter() {
        final BluetoothManager manager = (BluetoothManager) mContext.getSystemService(BLUETOOTH_SERVICE);
        if (manager == null) {
            return;
        }

        final BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            return;
        }

        mAdapter = adapter;
    }

    private void getAdvertiser() {
        getAdapter();
        if (mAdapter == null) {
            return;
        }

        final BluetoothLeAdvertiser advertiser = mAdapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            return;
        }

        mAdvertiser = advertiser;
    }

    private void getScanner() {
        getAdapter();
        if (mAdapter == null) {
            return;
        }

        final BluetoothLeScanner scanner = mAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            return;
        }

        mScanner = scanner;
    }

    boolean ready() {
        synchronized (mLock) {
            getAdvertiser();
            getScanner();
            return mAdvertiser != null && mScanner != null;
        }
    }

    String getName() {
        synchronized (mLock) {
            getAdapter();
            if (mAdapter == null) {
                return null;
            }
        }

        return mAdapter.getName();
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

    private PendingIntent getTriggerIntent(Class<? extends Service> receiverService) {
        return PendingIntent.getForegroundService(mContext, 0,
                new Intent(mContext, receiverService),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    void registerTrigger(Class<? extends Service> receiverService) {
        synchronized (mLock) {
            getScanner();
            if (mScanner == null) {
                return;
            }
        }

        final List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder()
                .setManufacturerData(MANUFACTURER_ID, MANUFACTURER_DATA, MANUFACTURER_DATA)
                .build());

        mScanner.startScan(filters,
                new ScanSettings.Builder()
                        .setCallbackType(CALLBACK_TYPE_FIRST_MATCH | CALLBACK_TYPE_MATCH_LOST)
                        .setMatchMode(MATCH_MODE_STICKY)
                        .setNumOfMatches(MATCH_NUM_MAX_ADVERTISEMENT)
                        .setScanMode(SCAN_MODE_BALANCED)
                        .build(),
                getTriggerIntent(receiverService));
    }

    void unregisterTrigger(Class<? extends Service> receiverService) {
        mScanner.stopScan(getTriggerIntent(receiverService));
    }

    private void handleAdvertiseStartSuccess() {
        Log.d(TAG, "Start advertise succeed");
    }

    private void handleAdvertiseStartFailure(int errorCode) {
        Log.e(TAG, "Start advertise failed: " + errorCode);
    }

}
