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

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.ArrayMap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.mokee.warpshare.airdrop.AirDropManager;
import org.mokee.warpshare.airdrop.AirDropPeer;
import org.mokee.warpshare.base.DiscoverListener;
import org.mokee.warpshare.base.Entity;
import org.mokee.warpshare.base.Peer;
import org.mokee.warpshare.base.SendListener;
import org.mokee.warpshare.base.SendingSession;
import org.mokee.warpshare.nearbysharing.NearShareManager;
import org.mokee.warpshare.nearbysharing.NearSharePeer;

import java.util.ArrayList;
import java.util.List;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static org.mokee.warpshare.airdrop.AirDropManager.STATUS_OK;

@SuppressWarnings("SwitchStatementWithTooFewBranches")
public class ShareBottomSheetFragment extends BottomSheetDialogFragment
        implements DiscoverListener {

    private static final String TAG = "ShareBottomSheetFragment";

    private static final int REQUEST_SETUP = 1;

    private final ArrayMap<String, Peer> mPeers = new ArrayMap<>();
    private final List<Entity> mEntities = new ArrayList<>();

    private ShareActivity mParent;

    private PeersAdapter mAdapter;

    private Button mSendButton;
    private View mDiscoveringView;

    private String mPeerPicked = null;
    private int mPeerStatus = 0;

    private long mBytesTotal = -1;
    private long mBytesSent = 0;

    private PartialWakeLock mWakeLock;

    private AirDropManager mAirDropManager;
    private NearShareManager mNearShareManager;

    private boolean mIsInSetup = false;

    private final WifiStateMonitor mWifiStateMonitor = new WifiStateMonitor() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setupIfNeeded();
        }
    };

    private final BluetoothStateMonitor mBluetoothStateMonitor = new BluetoothStateMonitor() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setupIfNeeded();
        }
    };

    private boolean mIsDiscovering = false;
    private boolean mShouldKeepDiscovering = false;

    private SendingSession mSending;

    public ShareBottomSheetFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mWakeLock = new PartialWakeLock(getContext(), TAG);
        mAirDropManager = new AirDropManager(getContext(),
                WarpShareApplication.from(getContext()).getCertificateManager());
        mNearShareManager = new NearShareManager(getContext());
        mAdapter = new PeersAdapter(getContext());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAirDropManager.destroy();
        mNearShareManager.destroy();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mParent = (ShareActivity) context;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_share, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final RecyclerView peersView = view.findViewById(R.id.peers);
        peersView.setAdapter(mAdapter);

        mSendButton = view.findViewById(R.id.send);
        mSendButton.setOnClickListener(v -> sendFile(mPeers.get(mPeerPicked), mEntities));

        mDiscoveringView = view.findViewById(R.id.discovering);

        final String type = mParent.getIntent().getType();
        final ClipData clipData = mParent.getIntent().getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                final Entity entity = new Entity(getContext(), clipData.getItemAt(i).getUri(), type);
                if (entity.ok()) {
                    mEntities.add(entity);
                }
            }
        } else {
            final Entity entity = new Entity(getContext(), mParent.getIntent().getData(), type);
            if (entity.ok()) {
                mEntities.add(entity);
            }
        }

        final int count = mEntities.size();
        final String titleText = getResources().getQuantityString(R.plurals.send_files_to, count, count);
        final TextView titleView = view.findViewById(R.id.title);
        titleView.setText(titleText);

        if (mEntities.isEmpty()) {
            Log.w(TAG, "No file was selected");
            Toast.makeText(getContext(), R.string.toast_no_file, Toast.LENGTH_SHORT).show();
            handleSendFailed();
            mParent.finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        mWifiStateMonitor.register(getContext());
        mBluetoothStateMonitor.register(getContext());

        if (setupIfNeeded()) {
            return;
        }

        if (!mIsDiscovering) {
            mAirDropManager.startDiscover(this);
            mNearShareManager.startDiscover(this);
            mIsDiscovering = true;
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mIsDiscovering && !mShouldKeepDiscovering) {
            mAirDropManager.stopDiscover();
            mNearShareManager.stopDiscover();
            mIsDiscovering = false;
        }

        mWifiStateMonitor.unregister(getContext());
        mBluetoothStateMonitor.unregister(getContext());
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (mSending != null) {
            mSending.cancel();
            mSending = null;
        }
        mParent.finish();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case REQUEST_SETUP:
                mIsInSetup = false;
                if (resultCode != Activity.RESULT_OK) {
                    mParent.finish();
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onPeerFound(Peer peer) {
        Log.d(TAG, "Found: " + peer.id + " (" + peer.name + ")");
        mPeers.put(peer.id, peer);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onPeerDisappeared(Peer peer) {
        Log.d(TAG, "Disappeared: " + peer.id + " (" + peer.name + ")");
        if (peer.id.equals(mPeerPicked)) {
            mPeerPicked = null;
        }
        mPeers.remove(peer.id);
        mAdapter.notifyDataSetChanged();
    }

    private boolean setupIfNeeded() {
        if (mIsInSetup) {
            return true;
        }

        final boolean granted = mParent.checkSelfPermission(WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED;
        final boolean ready = mAirDropManager.ready() == STATUS_OK;
        if (!granted || !ready) {
            mIsInSetup = true;
            startActivityForResult(new Intent(mParent, SetupActivity.class), REQUEST_SETUP);
            return true;
        } else {
            return false;
        }
    }

    private void handleItemClick(Peer peer) {
        if (mPeerStatus != 0 && mPeerStatus != R.string.status_rejected) {
            return;
        }
        mPeerStatus = 0;
        if (peer.id.equals(mPeerPicked)) {
            mPeerPicked = null;
            mSendButton.setEnabled(false);
        } else {
            mPeerPicked = peer.id;
            mSendButton.setEnabled(true);
        }
        mAdapter.notifyDataSetChanged();
    }

    private void handleSendConfirming() {
        mPeerStatus = R.string.status_waiting_for_confirm;
        mBytesTotal = -1;
        mBytesSent = 0;
        mAdapter.notifyDataSetChanged();
        mSendButton.setEnabled(false);
        mDiscoveringView.setVisibility(View.GONE);
        mShouldKeepDiscovering = true;
        mWakeLock.acquire();
    }

    private void handleSendRejected() {
        mSending = null;
        mPeerStatus = R.string.status_rejected;
        mAdapter.notifyDataSetChanged();
        mSendButton.setEnabled(true);
        mDiscoveringView.setVisibility(View.VISIBLE);
        mShouldKeepDiscovering = false;
        mWakeLock.release();
        Toast.makeText(getContext(), R.string.toast_rejected, Toast.LENGTH_SHORT).show();
    }

    private void handleSending() {
        mPeerStatus = R.string.status_sending;
        mAdapter.notifyDataSetChanged();
    }

    private void handleSendSucceed() {
        mSending = null;
        mShouldKeepDiscovering = false;
        mWakeLock.release();
        Toast.makeText(getContext(), R.string.toast_completed, Toast.LENGTH_SHORT).show();
        mParent.setResult(Activity.RESULT_OK);
        dismiss();
    }

    private void handleSendFailed() {
        mSending = null;
        mPeerPicked = null;
        mPeerStatus = 0;
        mAdapter.notifyDataSetChanged();
        mSendButton.setEnabled(true);
        mDiscoveringView.setVisibility(View.VISIBLE);
        mShouldKeepDiscovering = false;
        mWakeLock.release();
    }

    private <P extends Peer> void sendFile(P peer, List<Entity> entities) {
        handleSendConfirming();

        final SendListener listener = new SendListener() {
            @Override
            public void onAccepted() {
                handleSending();
            }

            @Override
            public void onRejected() {
                handleSendRejected();
            }

            @Override
            public void onProgress(long bytesSent, long bytesTotal) {
                mBytesSent = bytesSent;
                mBytesTotal = bytesTotal;
                mAdapter.notifyDataSetChanged();
            }

            @Override
            public void onSent() {
                handleSendSucceed();
            }

            @Override
            public void onSendFailed() {
                handleSendFailed();
            }
        };

        if (peer instanceof AirDropPeer) {
            mSending = mAirDropManager.send((AirDropPeer) peer, entities, listener);
        } else if (peer instanceof NearSharePeer) {
            mSending = mNearShareManager.send((NearSharePeer) peer, entities, listener);
        }
    }

    private class PeersAdapter extends RecyclerView.Adapter<PeersAdapter.ViewHolder> {

        private final LayoutInflater mInflater;

        PeersAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public PeersAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PeersAdapter.ViewHolder(mInflater.inflate(R.layout.item_peer, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull PeersAdapter.ViewHolder holder, int position) {
            final String id = mPeers.keyAt(position);
            final Peer peer = mPeers.valueAt(position);
            final boolean selected = id.equals(mPeerPicked);
            holder.nameView.setText(peer.name);
            holder.itemView.setSelected(selected);
            if (selected && mPeerStatus != 0) {
                holder.statusView.setVisibility(View.VISIBLE);
                if (mPeerStatus == R.string.status_sending && mBytesTotal != -1) {
                    holder.statusView.setText(getString(R.string.status_sending_progress,
                            Formatter.formatFileSize(mParent, mBytesSent),
                            Formatter.formatFileSize(mParent, mBytesTotal)));
                } else {
                    holder.statusView.setText(mPeerStatus);
                }
            } else {
                holder.statusView.setVisibility(View.GONE);
            }
            if (selected && mPeerStatus != 0 && mPeerStatus != R.string.status_rejected) {
                holder.progressBar.setVisibility(View.VISIBLE);
                if (mBytesTotal == -1 || mPeerStatus != R.string.status_sending) {
                    holder.progressBar.setIndeterminate(true);
                } else {
                    holder.progressBar.setIndeterminate(false);
                    holder.progressBar.setMax((int) mBytesTotal);
                    holder.progressBar.setProgress((int) mBytesSent, true);
                }
            } else {
                holder.progressBar.setVisibility(View.GONE);
            }
            if (peer instanceof AirDropPeer) {
                final boolean isMokee = ((AirDropPeer) peer).getMokeeApiVersion() > 0;
                if (isMokee) {
                    holder.iconView.setImageResource(R.drawable.ic_mokee_24dp);
                } else {
                    holder.iconView.setImageResource(R.drawable.ic_apple_24dp);
                }
            } else if (peer instanceof NearSharePeer) {
                holder.iconView.setImageResource(R.drawable.ic_windows_24dp);
            } else {
                holder.iconView.setImageDrawable(null);
            }
            holder.itemView.setOnClickListener(v -> handleItemClick(peer));
        }

        @Override
        public long getItemId(int position) {
            return mPeers.keyAt(position).hashCode();
        }

        @Override
        public int getItemCount() {
            return mPeers.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            ImageView iconView;
            TextView nameView;
            TextView statusView;
            ProgressBar progressBar;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                iconView = itemView.findViewById(R.id.icon);
                nameView = itemView.findViewById(R.id.name);
                statusView = itemView.findViewById(R.id.status);
                progressBar = itemView.findViewById(R.id.progress);
            }

        }

    }

}
