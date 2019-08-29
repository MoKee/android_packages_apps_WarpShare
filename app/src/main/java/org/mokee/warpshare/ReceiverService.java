package org.mokee.warpshare;

import android.app.Service;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import org.mokee.warpshare.airdrop.AirDropManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static android.bluetooth.le.BluetoothLeScanner.EXTRA_CALLBACK_TYPE;
import static android.bluetooth.le.BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT;
import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_FIRST_MATCH;
import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_MATCH_LOST;

public class ReceiverService extends Service {

    private static final String TAG = "ReceiverService";

    private final Set<String> devices = new HashSet<>();

    private AirDropManager mAirDropManager;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        mAirDropManager = new AirDropManager(this);
        mAirDropManager.startDiscoverable();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        mAirDropManager.stopDiscoverable();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final int callbackType = intent.getIntExtra(EXTRA_CALLBACK_TYPE, 0);
        final List<ScanResult> results = intent.getParcelableArrayListExtra(EXTRA_LIST_SCAN_RESULT);
        handleScanResult(callbackType, results);
        return START_STICKY;
    }

    private void handleScanResult(int callbackType, List<ScanResult> results) {
        if (results != null) {
            if (callbackType == CALLBACK_TYPE_FIRST_MATCH) {
                for (ScanResult result : results) {
                    devices.add(result.getDevice().getAddress());
                }
            } else if (callbackType == CALLBACK_TYPE_MATCH_LOST) {
                for (ScanResult result : results) {
                    devices.remove(result.getDevice().getAddress());
                }
            }
        }

        Log.d(TAG, "active devices: " + devices);
    }

}
