package org.mokee.warpshare;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import org.mokee.warpshare.airdrop.AirDropManager;
import org.mokee.warpshare.airdrop.AirDropManager.ReceivingSession;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private static final String ACTION_TRANSFER_CANCEL = "org.mokee.warpshare.TRANSFER_CANCEL";

    private static final String NOTIFICATION_CHANNEL_SERVICE = "receiver";
    private static final String NOTIFICATION_CHANNEL_TRANSFER = "transfer";

    private static final int NOTIFICATION_ACTIVE = 1;
    private static final int NOTIFICATION_TRANSFER = 2;

    private final Set<String> mDevices = new HashSet<>();

    private final Map<String, ReceivingSession> mSessions = new HashMap<>();

    private boolean mRunning = false;

    private NotificationManager mNotificationManager;
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

        startForeground(NOTIFICATION_ACTIVE, getNotificationBuilder(NOTIFICATION_CHANNEL_SERVICE, CATEGORY_SERVICE)
                .setContentTitle(getString(R.string.notif_recv_active_title))
                .setContentText(getString(R.string.notif_recv_active_desc))
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
        Log.d(TAG, "onStartCommand: " + intent);
        final String action = intent == null ? null : intent.getAction();
        if (ACTION_SCAN_RESULT.equals(action)) {
            final int callbackType = intent.getIntExtra(EXTRA_CALLBACK_TYPE, 0);
            final List<ScanResult> results = intent.getParcelableArrayListExtra(EXTRA_LIST_SCAN_RESULT);
            handleScanResult(callbackType, results);
        } else if (ACTION_TRANSFER_ACCEPT.equals(action)) {
            final Uri data = intent.getData();
            if (data != null) {
                handleTransferAccept(data.getPath());
            }
        } else if (ACTION_TRANSFER_REJECT.equals(action)) {
            final Uri data = intent.getData();
            if (data != null) {
                handleTransferReject(data.getPath());
            }
        } else if (ACTION_TRANSFER_CANCEL.equals(action)) {
            final Uri data = intent.getData();
            if (data != null) {
                handleTransferCancel(data.getPath());
            }
        }
        return START_STICKY;
    }

    private void handleScanResult(int callbackType, List<ScanResult> results) {
        if (results != null) {
            if (callbackType == CALLBACK_TYPE_FIRST_MATCH) {
                for (ScanResult result : results) {
                    mDevices.add(result.getDevice().getAddress());
                }
            } else if (callbackType == CALLBACK_TYPE_MATCH_LOST) {
                for (ScanResult result : results) {
                    mDevices.remove(result.getDevice().getAddress());
                }
            }
        }

        if (mRunning && mDevices.isEmpty()) {
            Log.d(TAG, "Peers lost, sleep");

            mAirDropManager.stopDiscoverable();

            stopSelf();

            mRunning = false;
        } else if (!mRunning && !mDevices.isEmpty()) {
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
    public void onAirDropRequest(ReceivingSession session) {
        Log.d(TAG, "Asking from " + session.name + " (" + session.ip + ")");

        mSessions.put(session.ip, session);

        mNotificationManager.notify(session.ip, NOTIFICATION_TRANSFER,
                getNotificationBuilder(NOTIFICATION_CHANNEL_TRANSFER, CATEGORY_STATUS)
                        .setContentTitle(getString(R.string.notif_recv_transfer_request_title))
                        .setContentText(getString(R.string.notif_recv_transfer_request_desc,
                                session.name, session.files.size()))
                        .addAction(new Notification.Action.Builder(null,
                                getString(R.string.notif_recv_transfer_request_accept),
                                getTransferIntent(ACTION_TRANSFER_ACCEPT, session.ip))
                                .build())
                        .addAction(new Notification.Action.Builder(null,
                                getString(R.string.notif_recv_transfer_request_reject),
                                getTransferIntent(ACTION_TRANSFER_REJECT, session.ip))
                                .build())
                        .setDeleteIntent(getTransferIntent(ACTION_TRANSFER_REJECT, session.ip))
                        .build());
    }

    @Override
    public void onAirDropRequestCanceled(ReceivingSession session) {
        Log.d(TAG, "Transfer ask canceled");
        mNotificationManager.cancel(session.ip, NOTIFICATION_TRANSFER);
    }

    private void handleTransferAccept(String ip) {
        final ReceivingSession session = mSessions.get(ip);
        if (session != null) {
            Log.d(TAG, "Transfer accepted");
            session.accept();

            mNotificationManager.notify(ip, NOTIFICATION_TRANSFER,
                    getNotificationBuilder(NOTIFICATION_CHANNEL_TRANSFER, CATEGORY_STATUS)
                            .setContentTitle(getString(R.string.notif_recv_transfer_progress_title,
                                    session.files.size(), session.name))
                            .setContentText(getString(R.string.notif_recv_transfer_progress_desc,
                                    0, session.files.size()))
                            .setProgress(0, 0, true)
                            .setOngoing(true)
                            .setOnlyAlertOnce(true)
                            .build());
        }
    }

    private void handleTransferReject(String ip) {
        final ReceivingSession session = mSessions.remove(ip);
        if (session != null) {
            Log.d(TAG, "Transfer rejected");
            session.reject();
        }
        mNotificationManager.cancel(ip, NOTIFICATION_TRANSFER);
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
            Log.d(TAG, "Received " + fileName);
        } catch (IOException e) {
            Log.e(TAG, "Failed writing file to " + file.getAbsolutePath(), e);
        }
    }

    private void handleTransferCancel(String ip) {
        final ReceivingSession session = mSessions.remove(ip);
        if (session != null) {
            session.cancel();
        }
        mNotificationManager.cancel(ip, NOTIFICATION_TRANSFER);
    }

    @Override
    public void onAirDropTransferProgress(ReceivingSession session, String fileName,
                                          long bytesReceived, long bytesTotal,
                                          int index, int count) {
        mNotificationManager.notify(session.ip, NOTIFICATION_TRANSFER,
                getNotificationBuilder(NOTIFICATION_CHANNEL_TRANSFER, CATEGORY_STATUS)
                        .setContentTitle(getString(R.string.notif_recv_transfer_progress_title,
                                session.files.size(), session.name))
                        .setContentText(getString(R.string.notif_recv_transfer_progress_desc,
                                index + 1, count))
                        .setProgress((int) bytesTotal, (int) bytesReceived, false)
                        .addAction(new Notification.Action.Builder(null,
                                getString(R.string.notif_recv_transfer_progress_cancel),
                                getTransferIntent(ACTION_TRANSFER_CANCEL, session.ip))
                                .build())
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .build());
    }

    @Override
    public void onAirDropTransferDone(ReceivingSession session) {
        Log.d(TAG, "All files received");

        mNotificationManager.cancel(session.ip, NOTIFICATION_TRANSFER);

        mNotificationManager.notify(session.ip, NOTIFICATION_TRANSFER,
                getNotificationBuilder(NOTIFICATION_CHANNEL_TRANSFER, CATEGORY_STATUS)
                        .setContentTitle(getString(R.string.notif_recv_transfer_done_title,
                                session.files.size(), session.name))
                        .setContentText(getString(R.string.notif_recv_transfer_done_desc))
                        .setContentIntent(PendingIntent.getActivity(this, 0,
                                new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                PendingIntent.FLAG_UPDATE_CURRENT))
                        .build());

        mSessions.remove(session.ip);
    }

    @Override
    public void onAirDropTransferFailed(ReceivingSession session) {
        Log.d(TAG, "Receiving aborted");

        mNotificationManager.cancel(session.ip, NOTIFICATION_TRANSFER);

        mNotificationManager.notify(session.ip, NOTIFICATION_TRANSFER,
                getNotificationBuilder(NOTIFICATION_CHANNEL_TRANSFER, CATEGORY_STATUS)
                        .setContentTitle(getString(R.string.notif_recv_transfer_failed_title))
                        .setContentText(getString(R.string.notif_recv_transfer_failed_desc,
                                session.name))
                        .build());

        mSessions.remove(session.ip);
    }

    private PendingIntent getTransferIntent(String action, String ip) {
        return PendingIntent.getForegroundService(this, 0,
                new Intent(action, null, this, getClass())
                        .setData(new Uri.Builder().path(ip).build()),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification.Builder getNotificationBuilder(String channelId, String category) {
        return new Notification.Builder(this, channelId)
                .setCategory(category)
                .setSmallIcon(R.drawable.ic_notification_white_24dp)
                .setColor(getColor(R.color.primary));
    }

}
