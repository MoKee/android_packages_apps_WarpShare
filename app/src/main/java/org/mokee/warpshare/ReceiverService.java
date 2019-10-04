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

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.le.ScanResult;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
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
import java.util.ArrayList;
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
import static androidx.core.content.FileProvider.getUriForFile;
import static org.mokee.warpshare.airdrop.AirDropManager.STATUS_OK;

public class ReceiverService extends Service implements AirDropManager.ReceiverListener {

    private static final String TAG = "ReceiverService";

    private static final String ACTION_SCAN_RESULT = "org.mokee.warpshare.SCAN_RESULT";

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

    private PartialWakeLock mWakeLock;

    private NotificationManager mNotificationManager;
    private AirDropManager mAirDropManager;

    private final WifiStateMonitor mWifiStateMonitor = new WifiStateMonitor() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopIfNotReady();
        }
    };

    private final BluetoothStateMonitor mBluetoothStateMonitor = new BluetoothStateMonitor() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopIfNotReady();
        }
    };

    static PendingIntent getTriggerIntent(Context context) {
        return PendingIntent.getForegroundService(context, 0,
                new Intent(ACTION_SCAN_RESULT, null, context, ReceiverService.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void updateDiscoverability(Context context) {
        final ConfigManager configManager = new ConfigManager(context);
        final PackageManager packageManager = context.getPackageManager();

        final int state = configManager.isDiscoverable()
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        packageManager.setComponentEnabledSetting(
                new ComponentName(context, ReceiverService.class),
                state, PackageManager.DONT_KILL_APP);

        if (!configManager.isDiscoverable()) {
            context.stopService(new Intent(context, ReceiverService.class));
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        mWakeLock = new PartialWakeLock(this, TAG);

        mAirDropManager = new AirDropManager(this,
                WarpShareApplication.from(this).getCertificateManager());

        mWifiStateMonitor.register(this);
        mBluetoothStateMonitor.register(this);

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
                .addAction(new Notification.Action.Builder(null,
                        getString(R.string.settings),
                        PendingIntent.getActivity(this, 0,
                                new Intent(this, SettingsActivity.class),
                                PendingIntent.FLAG_UPDATE_CURRENT))
                        .build())
                .setOngoing(true)
                .build());

        stopIfNotReady();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        mAirDropManager.destroy();

        mWifiStateMonitor.unregister(this);
        mBluetoothStateMonitor.unregister(this);

        stopForeground(true);
    }

    private void stopIfNotReady() {
        if (mAirDropManager.ready() != STATUS_OK) {
            Log.w(TAG, "Hardware not ready, quit");
            stopSelf();
        }
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

        final Notification.Builder builder = getNotificationBuilder(NOTIFICATION_CHANNEL_TRANSFER, CATEGORY_STATUS)
                .setContentTitle(getString(R.string.notif_recv_transfer_request_title))
                .setContentText(getResources().getQuantityString(
                        R.plurals.notif_recv_transfer_request_desc, session.paths.size(),
                        session.name, session.paths.size()))
                .addAction(new Notification.Action.Builder(null,
                        getString(R.string.notif_recv_transfer_request_accept),
                        getTransferIntent(ACTION_TRANSFER_ACCEPT, session.ip))
                        .build())
                .addAction(new Notification.Action.Builder(null,
                        getString(R.string.notif_recv_transfer_request_reject),
                        getTransferIntent(ACTION_TRANSFER_REJECT, session.ip))
                        .build())
                .setDeleteIntent(getTransferIntent(ACTION_TRANSFER_REJECT, session.ip));

        if (session.preview != null) {
            builder.setLargeIcon(session.preview);
            builder.setStyle(new Notification.BigPictureStyle()
                    .bigLargeIcon((Icon) null)
                    .bigPicture(session.preview));
        }

        mNotificationManager.notify(session.ip, NOTIFICATION_TRANSFER, builder.build());
        mWakeLock.acquire();
    }

    @Override
    public void onAirDropRequestCanceled(ReceivingSession session) {
        Log.d(TAG, "Transfer ask canceled");
        mNotificationManager.cancel(session.ip, NOTIFICATION_TRANSFER);
        mWakeLock.release();
    }

    private void handleTransferAccept(String ip) {
        final ReceivingSession session = mSessions.get(ip);
        if (session != null) {
            Log.d(TAG, "Transfer accepted");
            session.accept();

            mNotificationManager.notify(ip, NOTIFICATION_TRANSFER,
                    getNotificationBuilder(NOTIFICATION_CHANNEL_TRANSFER, CATEGORY_STATUS)
                            .setContentTitle(getResources().getQuantityString(
                                    R.plurals.notif_recv_transfer_progress_title, session.paths.size(),
                                    session.paths.size(), session.name))
                            .setContentText(getString(R.string.notif_recv_transfer_progress_desc,
                                    0, session.paths.size()))
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
        mWakeLock.release();
    }

    @Override
    public void onAirDropTransfer(ReceivingSession session, String fileName, InputStream input) {
        Log.d(TAG, "Transferring " + fileName + " from " + session.name);
        final String targetFileName = session.getFileName(fileName);
        final File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        final File file = new File(downloadDir, targetFileName);
        try {
            final Source source = Okio.source(input);
            final BufferedSink sink = Okio.buffer(Okio.sink(file));
            sink.writeAll(source);
            sink.flush();
            sink.close();
            Log.d(TAG, "Received " + fileName + " as " + targetFileName);
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
        mWakeLock.release();
    }

    @Override
    public void onAirDropTransferProgress(ReceivingSession session, String fileName,
                                          long bytesReceived, long bytesTotal,
                                          int index, int count) {
        mNotificationManager.notify(session.ip, NOTIFICATION_TRANSFER,
                getNotificationBuilder(NOTIFICATION_CHANNEL_TRANSFER, CATEGORY_STATUS)
                        .setContentTitle(getResources().getQuantityString(
                                R.plurals.notif_recv_transfer_progress_title, session.paths.size(),
                                session.paths.size(), session.name))
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

        final Intent shareIntent;
        if (session.paths.size() > 1) {
            final ArrayList<Uri> uris = new ArrayList<>();
            for (String path : session.paths) {
                uris.add(getUriForReceivedFile(session.getFileName(path)));
            }
            shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uris);
        } else {
            final Uri uri = getUriForReceivedFile(session.getFileName(session.paths.get(0)));
            shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        }

        shareIntent.setType(getGeneralMimeType(session.types));
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        final Notification.Builder builder = getNotificationBuilder(NOTIFICATION_CHANNEL_TRANSFER, CATEGORY_STATUS)
                .setContentTitle(getResources().getQuantityString(
                        R.plurals.notif_recv_transfer_done_title, session.paths.size(),
                        session.paths.size(), session.name))
                .setContentText(getString(R.string.notif_recv_transfer_done_desc))
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_UPDATE_CURRENT))
                .addAction(new Notification.Action.Builder(null,
                        getString(R.string.notif_recv_transfer_done_share),
                        PendingIntent.getActivity(this, 0,
                                Intent.createChooser(shareIntent, null)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                PendingIntent.FLAG_UPDATE_CURRENT))
                        .build());

        if (session.preview != null) {
            builder.setLargeIcon(session.preview);
            builder.setStyle(new Notification.BigPictureStyle()
                    .bigLargeIcon((Icon) null)
                    .bigPicture(session.preview));
        }

        mNotificationManager.notify(session.ip, NOTIFICATION_TRANSFER, builder.build());

        mSessions.remove(session.ip);

        mWakeLock.release();
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

        mWakeLock.release();
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

    private Uri getUriForReceivedFile(String fileName) {
        final File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        final File file = new File(downloadDir, fileName);
        return getUriForFile(this, "org.mokee.warpshare.files", file);
    }

    private String getGeneralMimeType(List<String> mimeTypes) {
        String generalType = null;
        String generalSubtype = null;

        for (String mimeType : mimeTypes) {
            final String[] segments = mimeType.split("/");
            if (segments.length != 2) continue;
            final String type = segments[0];
            final String subtype = segments[1];
            if (type.equals("*") && !subtype.equals("*")) continue;

            if (generalType == null) {
                generalType = type;
                generalSubtype = subtype;
                continue;
            }

            if (!generalType.equals(type)) {
                generalType = "*";
                generalSubtype = "*";
                break;
            }

            if (!generalSubtype.equals(subtype)) {
                generalSubtype = "*";
            }
        }

        if (generalType == null) {
            generalType = "*";
        }
        if (generalSubtype == null) {
            generalSubtype = "*";
        }

        return generalType + "/" + generalSubtype;
    }

}
