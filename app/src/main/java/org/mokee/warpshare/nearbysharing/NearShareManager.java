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

package org.mokee.warpshare.nearbysharing;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.microsoft.connecteddevices.AsyncOperationWithProgress;
import com.microsoft.connecteddevices.ConnectedDevicesAccount;
import com.microsoft.connecteddevices.ConnectedDevicesAccountManager;
import com.microsoft.connecteddevices.ConnectedDevicesNotificationRegistrationManager;
import com.microsoft.connecteddevices.ConnectedDevicesPlatform;
import com.microsoft.connecteddevices.remotesystems.RemoteSystemAuthorizationKind;
import com.microsoft.connecteddevices.remotesystems.RemoteSystemAuthorizationKindFilter;
import com.microsoft.connecteddevices.remotesystems.RemoteSystemDiscoveryType;
import com.microsoft.connecteddevices.remotesystems.RemoteSystemDiscoveryTypeFilter;
import com.microsoft.connecteddevices.remotesystems.RemoteSystemFilter;
import com.microsoft.connecteddevices.remotesystems.RemoteSystemStatusType;
import com.microsoft.connecteddevices.remotesystems.RemoteSystemStatusTypeFilter;
import com.microsoft.connecteddevices.remotesystems.RemoteSystemWatcher;
import com.microsoft.connecteddevices.remotesystems.commanding.RemoteSystemConnectionRequest;
import com.microsoft.connecteddevices.remotesystems.commanding.nearshare.NearShareFileProvider;
import com.microsoft.connecteddevices.remotesystems.commanding.nearshare.NearShareHelper;
import com.microsoft.connecteddevices.remotesystems.commanding.nearshare.NearShareProgress;
import com.microsoft.connecteddevices.remotesystems.commanding.nearshare.NearShareSender;
import com.microsoft.connecteddevices.remotesystems.commanding.nearshare.NearShareStatus;

import org.mokee.warpshare.base.DiscoverListener;
import org.mokee.warpshare.base.Discoverer;
import org.mokee.warpshare.base.Entity;
import org.mokee.warpshare.base.SendListener;
import org.mokee.warpshare.base.Sender;
import org.mokee.warpshare.base.SendingSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class NearShareManager implements
        Discoverer,
        Sender<NearSharePeer> {

    private static final String TAG = "NearShareManager";

    private final Context mContext;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final Map<String, NearSharePeer> mPeers = new HashMap<>();

    private ConnectedDevicesPlatform mPlatform;
    private RemoteSystemWatcher mRemoteSystemWatcher;
    private NearShareSender mNearShareSender;

    private DiscoverListener mDiscoverListener;

    public NearShareManager(Context context) {
        mContext = context.getApplicationContext();
        setupPlatform(context);
        setupAnonymousAccount();
        setupWatcher();
        mNearShareSender = new NearShareSender();
    }

    public void destroy() {
        mPlatform.shutdownAsync();
        mHandler.removeCallbacksAndMessages(null);
    }

    private void setupPlatform(Context context) {
        mPlatform = new ConnectedDevicesPlatform(context.getApplicationContext());

        final ConnectedDevicesAccountManager accountManager = mPlatform.getAccountManager();

        accountManager.accessTokenRequested().subscribe((manager, args) -> {
        });

        accountManager.accessTokenInvalidated().subscribe((manager, args) -> {
        });

        final ConnectedDevicesNotificationRegistrationManager registrationManager
                = mPlatform.getNotificationRegistrationManager();

        registrationManager.notificationRegistrationStateChanged().subscribe((manager, args) -> {
        });

        mPlatform.start();
    }

    private void setupAnonymousAccount() {
        final ConnectedDevicesAccount account = ConnectedDevicesAccount.getAnonymousAccount();
        mPlatform.getAccountManager().addAccountAsync(account).whenComplete((result, tr) -> {
            if (tr != null) {
                Log.e(TAG, "Failed creating anonymous account", tr);
            }
        });
    }

    private void setupWatcher() {
        final List<RemoteSystemFilter> filters = new ArrayList<>();
        filters.add(new RemoteSystemDiscoveryTypeFilter(RemoteSystemDiscoveryType.PROXIMAL));
        filters.add(new RemoteSystemStatusTypeFilter(RemoteSystemStatusType.ANY));
        filters.add(new RemoteSystemAuthorizationKindFilter(RemoteSystemAuthorizationKind.ANONYMOUS));

        mRemoteSystemWatcher = new RemoteSystemWatcher(filters);

        mRemoteSystemWatcher.remoteSystemAdded().subscribe((watcher, args) -> {
            final NearSharePeer peer = NearSharePeer.from(args.getRemoteSystem());
            final RemoteSystemConnectionRequest connectionRequest = new RemoteSystemConnectionRequest(peer.remoteSystem);
            if (mNearShareSender.isNearShareSupported(connectionRequest)) {
                mPeers.put(peer.id, peer);
                mHandler.post(() -> mDiscoverListener.onPeerFound(peer));
            }
        });

        mRemoteSystemWatcher.remoteSystemRemoved().subscribe((watcher, args) -> {
            final NearSharePeer peer = mPeers.remove(args.getRemoteSystem().getId());
            if (peer != null) {
                mHandler.post(() -> mDiscoverListener.onPeerDisappeared(peer));
            }
        });
    }

    @Override
    public void startDiscover(DiscoverListener discoverListener) {
        mDiscoverListener = discoverListener;
        mRemoteSystemWatcher.start();
    }

    @Override
    public void stopDiscover() {
        mRemoteSystemWatcher.stop();
    }

    @Override
    public SendingSession send(NearSharePeer peer, List<Entity> entities, SendListener listener) {
        final RemoteSystemConnectionRequest connectionRequest = new RemoteSystemConnectionRequest(peer.remoteSystem);

        final AsyncOperationWithProgress<NearShareStatus, NearShareProgress> operation;
        if (entities.size() == 1) {
            final NearShareFileProvider fileProvider = NearShareHelper.createNearShareFileFromContentUri(
                    entities.get(0).uri, mContext);

            operation = mNearShareSender.sendFileAsync(connectionRequest, fileProvider);
        } else {
            final NearShareFileProvider[] fileProviders = new NearShareFileProvider[entities.size()];

            for (int i = 0; i < entities.size(); i++) {
                fileProviders[i] = NearShareHelper.createNearShareFileFromContentUri(
                        entities.get(i).uri, mContext);
            }

            operation = mNearShareSender.sendFilesAsync(connectionRequest, fileProviders);
        }

        final AtomicBoolean accepted = new AtomicBoolean(false);

        operation.progress().subscribe((op, progress) -> {
            if (progress.filesSent != 0 || progress.totalFilesToSend != 0) {
                if (accepted.compareAndSet(false, true)) {
                    mHandler.post(listener::onAccepted);
                }
                mHandler.post(() -> listener.onProgress(progress.bytesSent, progress.totalBytesToSend));
            }
        });

        operation.whenComplete((status, tr) -> {
            if (tr != null) {
                Log.e(TAG, "Failed sending files to " + peer.name, tr);
                mHandler.post(listener::onSendFailed);
            } else if (status != NearShareStatus.COMPLETED) {
                Log.e(TAG, "Failed sending files to " + peer.name + ": " + status);
                mHandler.post(listener::onSendFailed);
            } else {
                mHandler.post(listener::onSent);
            }
        });

        return new SendingSession() {
            @Override
            public void cancel() {
                operation.cancel(true);
            }
        };
    }

}
