package org.mokee.warpshare;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import org.mokee.warpshare.airdrop.AirDropManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okio.BufferedSink;
import okio.Okio;
import okio.Source;

import static android.app.Notification.CATEGORY_SERVICE;
import static android.app.Notification.CATEGORY_STATUS;
import static android.app.NotificationManager.IMPORTANCE_HIGH;
import static android.app.NotificationManager.IMPORTANCE_MIN;
import static android.bluetooth.le.BluetoothLeScanner.EXTRA_CALLBACK_TYPE;
import static android.bluetooth.le.BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT;
import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_FIRST_MATCH;
import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_MATCH_LOST;
import static org.mokee.warpshare.airdrop.AirDropManager.STATUS_OK;

public class ReceiverService extends Service implements AirDropManager.ReceiverListener {

    static final String ACTION_SCAN_RESULT = "org.mokee.warpshare.SCAN_RESULT";

    private static final String TAG = "ReceiverService";

    private static final String ACTION_TRANSFER_ACCEPT = "org.mokee.warpshare.TRANSFER_ACCEPT";
    private static final String ACTION_TRANSFER_REJECT = "org.mokee.warpshare.TRANSFER_REJECT";

    private static final String NOTIFICATION_CHANNEL_SERVICE = "receiver";
    private static final String NOTIFICATION_CHANNEL_TRANSFER = "transfer";

    private static final int NOTIFICATION_ACTIVE = 1;
    private static final int NOTIFICATION_TRANSFER = 2;

    private final Set<String> devices = new HashSet<>();

    private boolean mRunning = false;

    private NotificationManager mNotificationManager;
    private AirDropManager mAirDropManager;

    private String mPendingTransferName = null;
    private int mPendingTransferCount = 0;
    private AirDropManager.ReceiverCallback mPendingTransferCallback = null;

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

        final NotificationChannel serviceChannel = new NotificationChannel(NOTIFICATION_CHANNEL_SERVICE,
                getString(R.string.notif_recv_service_channel), IMPORTANCE_MIN);
        serviceChannel.enableLights(false);
        serviceChannel.enableVibration(false);
        serviceChannel.setShowBadge(false);

        mNotificationManager.createNotificationChannel(serviceChannel);

        final NotificationChannel transferChannel = new NotificationChannel(NOTIFICATION_CHANNEL_TRANSFER,
                getString(R.string.notif_recv_transfer_channel), IMPORTANCE_HIGH);

        mNotificationManager.createNotificationChannel(transferChannel);

        startForeground(NOTIFICATION_ACTIVE, new Notification.Builder(this, NOTIFICATION_CHANNEL_SERVICE)
                .setCategory(CATEGORY_SERVICE)
                .setContentTitle(getString(R.string.notif_recv_active_title))
                .setContentText(getString(R.string.notif_recv_active_description))
                .setSmallIcon(R.drawable.ic_discoverable_24dp)
                .setOngoing(true)
                .build());

        if (mAirDropManager.ready() != STATUS_OK) {
            Log.w(TAG, "Hardware not ready, quit");
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        mAirDropManager.destroy();
        stopForeground(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent == null ? null : intent.getAction();
        if (ACTION_SCAN_RESULT.equals(action)) {
            final int callbackType = intent.getIntExtra(EXTRA_CALLBACK_TYPE, 0);
            final List<ScanResult> results = intent.getParcelableArrayListExtra(EXTRA_LIST_SCAN_RESULT);
            handleScanResult(callbackType, results);
        } else if (ACTION_TRANSFER_ACCEPT.equals(action)) {
            handleTransferAccept();
        } else if (ACTION_TRANSFER_REJECT.equals(action)) {
            handleTransferReject();
        }
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

        if (mRunning && devices.isEmpty()) {
            Log.d(TAG, "Peers lost, sleep");

            mAirDropManager.stopDiscoverable();

            stopSelf();

            mRunning = false;
        } else if (!mRunning && !devices.isEmpty()) {
            Log.d(TAG, "Peers discovering");

            if (mAirDropManager.ready() != STATUS_OK) {
                Log.w(TAG, "Hardware not ready, quit");
                stopSelf();
                return;
            }

            mAirDropManager.startDiscoverable(this);

            mRunning = true;
        }
    }

    @Override
    public void onAirDropRequest(String name, List<String> fileNames, AirDropManager.ReceiverCallback callback) {
        Log.d(TAG, "Asking from " + name);

        mPendingTransferName = name;
        mPendingTransferCount = fileNames.size();
        mPendingTransferCallback = callback;

        mNotificationManager.notify(NOTIFICATION_TRANSFER,
                new Notification.Builder(this, NOTIFICATION_CHANNEL_TRANSFER)
                        .setCategory(CATEGORY_STATUS)
                        .setContentTitle(getString(R.string.notif_recv_transfer_title, name, fileNames.size()))
                        .setContentText(getString(R.string.notif_recv_transfer_description))
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .addAction(new Notification.Action.Builder(
                                Icon.createWithResource(this, R.mipmap.ic_launcher),
                                getString(R.string.notif_recv_transfer_accept),
                                getTransferIntent(ACTION_TRANSFER_ACCEPT))
                                .build())
                        .addAction(new Notification.Action.Builder(
                                Icon.createWithResource(this, R.mipmap.ic_launcher),
                                getString(R.string.notif_recv_transfer_reject),
                                getTransferIntent(ACTION_TRANSFER_REJECT))
                                .build())
                        .setDeleteIntent(getTransferIntent(ACTION_TRANSFER_REJECT))
                        .build());
    }

    private void handleTransferAccept() {
        if (mPendingTransferCallback != null) {
            Log.d(TAG, "Transfer accepted");
            mPendingTransferCallback.accept();
            mPendingTransferCallback = null;

            mNotificationManager.notify(NOTIFICATION_TRANSFER,
                    new Notification.Builder(this, NOTIFICATION_CHANNEL_TRANSFER)
                            .setCategory(CATEGORY_STATUS)
                            .setContentTitle(getString(R.string.notif_recv_transfer_progress_title,
                                    mPendingTransferName))
                            .setContentText(getString(R.string.notif_recv_transfer_progress_description,
                                    0, mPendingTransferCount))
                            .setProgress(0, 0, true)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setOngoing(true)
                            .setOnlyAlertOnce(true)
                            .build());
        }
    }

    private void handleTransferReject() {
        if (mPendingTransferCallback != null) {
            Log.d(TAG, "Transfer rejected");
            mPendingTransferCallback.reject();
            mPendingTransferCallback = null;
        }

        mNotificationManager.cancel(NOTIFICATION_TRANSFER);
    }

    @Override
    public void onAirDropTransfer(String fileName, InputStream input) {
        Log.d(TAG, "Transferring " + fileName);
        final File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        final File file = new File(downloadDir, fileName);
        try {
            final Source source = Okio.source(input);
            final BufferedSink sink = Okio.buffer(Okio.sink(file));
            sink.writeAll(source);
            sink.flush();
            sink.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed writing file to " + file.getAbsolutePath(), e);
        }
    }

    @Override
    public void onAirDropTransferProgress(String name, String fileName,
                                          long bytesReceived, long bytesTotal,
                                          int index, int count) {
        mNotificationManager.notify(NOTIFICATION_TRANSFER,
                new Notification.Builder(this, NOTIFICATION_CHANNEL_TRANSFER)
                        .setCategory(CATEGORY_STATUS)
                        .setContentTitle(getString(R.string.notif_recv_transfer_progress_title,
                                name))
                        .setContentText(getString(R.string.notif_recv_transfer_progress_description,
                                index + 1, count))
                        .setProgress((int) bytesTotal, (int) bytesReceived, false)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .build());
    }

    @Override
    public void onAirDropTransferDone(String name) {
        mNotificationManager.notify(NOTIFICATION_TRANSFER,
                new Notification.Builder(this, NOTIFICATION_CHANNEL_TRANSFER)
                        .setCategory(CATEGORY_STATUS)
                        .setContentTitle(getString(R.string.notif_recv_transfer_done_title))
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .build());
    }

    @Override
    public void onAirDropTransferFailed(String name) {
        mNotificationManager.notify(NOTIFICATION_TRANSFER,
                new Notification.Builder(this, NOTIFICATION_CHANNEL_TRANSFER)
                        .setCategory(CATEGORY_STATUS)
                        .setContentTitle(getString(R.string.notif_recv_transfer_failed_title))
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .build());
    }

    private PendingIntent getTransferIntent(String action) {
        return PendingIntent.getForegroundService(this, 0,
                new Intent(action, null, this, getClass()),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

}
