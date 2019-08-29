package org.mokee.warpshare;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import org.mokee.warpshare.airdrop.AirDropManager;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.bluetooth.le.BluetoothLeScanner.EXTRA_CALLBACK_TYPE;
import static android.bluetooth.le.BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT;
import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_FIRST_MATCH;
import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_MATCH_LOST;

public class ReceiverService extends Service {

    private static final String TAG = "ReceiverService";

    private static final String NOTIFICATION_CHANNEL = "receiver";
    private static final int NOTIFICATION_ACTIVE = 1;

    private final Set<String> devices = new HashSet<>();

    private boolean mRunning = false;

    private AirDropManager mAirDropManager;
    private NotificationManager mNotificationManager;

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

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Objects.requireNonNull(mNotificationManager).createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL, getString(R.string.notification_channel), IMPORTANCE_DEFAULT));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
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

        Log.d(TAG, "active devices: " + devices + ", " + mRunning + ", " + devices.isEmpty());

        if (mRunning && devices.isEmpty()) {
            Log.d(TAG, "Peers lost, sleep");

            mAirDropManager.stopDiscoverable();
            mNotificationManager.cancel(NOTIFICATION_ACTIVE);

            mRunning = false;
        } else if (!mRunning && !devices.isEmpty()) {
            Log.d(TAG, "Peers discovering");

            mAirDropManager.startDiscoverable();

            mNotificationManager.notify(NOTIFICATION_ACTIVE,
                    new Notification.Builder(this, NOTIFICATION_CHANNEL)
                            .setContentTitle(getString(R.string.notification_receiver_running))
                            .setContentText(getString(R.string.notification_receiver_description))
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setOngoing(true)
                            .build());

            mRunning = true;
        }
    }

}
