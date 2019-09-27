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

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Network;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.ArrayMap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;

import org.mokee.warpshare.airdrop.AirDropManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static org.mokee.warpshare.airdrop.AirDropManager.STATUS_OK;

@SuppressWarnings("SwitchStatementWithTooFewBranches")
public class MainActivity extends AppCompatActivity implements AirDropManager.DiscoveryListener {

    private static final String TAG = "MainActivity";

    private static final int REQUEST_PICK = 1;
    private static final int REQUEST_SETUP = 2;

    private final ArrayMap<String, AirDropManager.Peer> mPeers = new ArrayMap<>();

    private final Map<String, PeerState> mPeerStates = new HashMap<>();

    private PeersAdapter mAdapter;

    private String mPeerPicked = null;

    private AirDropManager mAirDropManager;

    private boolean mIsInSetup = false;

    private final WifiStateMonitor mWifiStateMonitor = new WifiStateMonitor() {
        @Override
        public void onAvailable(Network network) {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAirDropManager = new AirDropManager(this,
                WarpShareApplication.from(this).getCertificateManager());
        mAirDropManager.registerTrigger(TriggerReceiver.getTriggerIntent(this));

        mAdapter = new PeersAdapter(this);

        final RecyclerView peersView = findViewById(R.id.peers);
        peersView.setAdapter(mAdapter);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAirDropManager.destroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        mWifiStateMonitor.register(this);
        mBluetoothStateMonitor.register(this);

        if (setupIfNeeded()) {
            return;
        }

        if (!mIsDiscovering) {
            mAirDropManager.startDiscover(this);
            mIsDiscovering = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mIsDiscovering && !mShouldKeepDiscovering) {
            mAirDropManager.stopDiscover();
            mIsDiscovering = false;
        }

        mWifiStateMonitor.unregister(this);
        mBluetoothStateMonitor.unregister(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case REQUEST_PICK:
                mShouldKeepDiscovering = false;
                if (resultCode == RESULT_OK && mPeerPicked != null && data != null) {
                    final AirDropManager.Peer peer = mPeers.get(mPeerPicked);
                    if (peer != null) {
                        if (data.getClipData() == null) {
                            sendFile(peer, data.getData(), data.getType());
                        } else {
                            sendFile(peer, data.getClipData(), data.getType());
                        }
                    }
                }
                break;
            case REQUEST_SETUP:
                mIsInSetup = false;
                if (resultCode != RESULT_OK) {
                    finish();
                } else {
                    mAirDropManager.registerTrigger(TriggerReceiver.getTriggerIntent(this));
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    @Override
    public void onAirDropPeerFound(AirDropManager.Peer peer) {
        Log.d(TAG, "Found: " + peer.id + " (" + peer.name + ")");
        mPeers.put(peer.id, peer);
        mPeerStates.put(peer.id, new PeerState());
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onAirDropPeerDisappeared(AirDropManager.Peer peer) {
        Log.d(TAG, "Disappeared: " + peer.id + " (" + peer.name + ")");
        mPeers.remove(peer.id);
        mPeerStates.remove(peer.id);
        mAdapter.notifyDataSetChanged();
    }

    private boolean setupIfNeeded() {
        if (mIsInSetup) {
            return true;
        }

        final boolean granted = checkSelfPermission(WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED;
        final boolean ready = mAirDropManager.ready() == STATUS_OK;
        if (!granted || !ready) {
            mIsInSetup = true;
            startActivityForResult(new Intent(this, SetupActivity.class), REQUEST_SETUP);
            return true;
        } else {
            return false;
        }
    }

    private void handleItemClick(AirDropManager.Peer peer) {
        mPeerPicked = peer.id;
        mShouldKeepDiscovering = true;
        Intent requestIntent = new Intent(Intent.ACTION_GET_CONTENT);
        requestIntent.addCategory(Intent.CATEGORY_OPENABLE);
        requestIntent.setType("*/*");
        requestIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(requestIntent, "File"), REQUEST_PICK);
    }

    private void handleItemCancelClick(AirDropManager.Peer peer, PeerState state) {
        if (state.sending != null) {
            state.sending.cancel();
        }
        handleSendFailed(peer);
    }

    private void handleSendConfirming(AirDropManager.Peer peer, PeerState state) {
        state.status = R.string.status_waiting_for_confirm;
        state.bytesTotal = -1;
        state.bytesSent = 0;
        mAdapter.notifyDataSetChanged();
    }

    private void handleSendRejected(AirDropManager.Peer peer, PeerState state) {
        state.status = R.string.status_rejected;
        mAdapter.notifyDataSetChanged();
    }

    private void handleSending(AirDropManager.Peer peer, PeerState state) {
        state.status = R.string.status_sending;
        mAdapter.notifyDataSetChanged();
    }

    private void handleSendSucceed(AirDropManager.Peer peer, PeerState state) {
        state.status = 0;
        mAdapter.notifyDataSetChanged();
    }

    private void handleSendFailed(AirDropManager.Peer peer) {
        final PeerState state = mPeerStates.get(peer.id);
        if (state != null) {
            state.status = 0;
        }
        mAdapter.notifyDataSetChanged();
    }

    private void sendFile(AirDropManager.Peer peer, Uri rawUri, String type) {
        final ResolvedUri uri = new ResolvedUri(this, rawUri, type);
        if (!uri.ok()) {
            Log.w(TAG, "No file was selected");
            handleSendFailed(peer);
            return;
        }

        final List<ResolvedUri> uris = new ArrayList<>();
        uris.add(uri);

        sendFile(peer, uris);
    }

    private void sendFile(AirDropManager.Peer peer, ClipData clipData, String type) {
        if (clipData == null) {
            Log.w(TAG, "ClipData should not be null");
            handleSendFailed(peer);
            return;
        }

        final List<ResolvedUri> uris = new ArrayList<>();
        for (int i = 0; i < clipData.getItemCount(); i++) {
            final ResolvedUri uri = new ResolvedUri(this, clipData.getItemAt(i).getUri(), type);
            if (uri.ok()) {
                uris.add(uri);
            }
        }

        if (uris.isEmpty()) {
            Log.w(TAG, "No file was selected");
            handleSendFailed(peer);
            return;
        }

        sendFile(peer, uris);
    }

    private void sendFile(final AirDropManager.Peer peer, final List<ResolvedUri> uris) {
        final PeerState state = mPeerStates.get(peer.id);
        if (state == null) {
            Log.w(TAG, "state should not be null");
            handleSendFailed(peer);
            return;
        }

        handleSendConfirming(peer, state);

        state.sending = mAirDropManager.send(peer, uris, new AirDropManager.SenderListener() {
            @Override
            public void onAirDropAccepted() {
                handleSending(peer, state);
            }

            @Override
            public void onAirDropRejected() {
                handleSendRejected(peer, state);
            }

            @Override
            public void onAirDropProgress(long bytesSent, long bytesTotal) {
                state.bytesSent = bytesSent;
                state.bytesTotal = bytesTotal;
                mAdapter.notifyDataSetChanged();
            }

            @Override
            public void onAirDropSent() {
                handleSendSucceed(peer, state);
            }

            @Override
            public void onAirDropSendFailed() {
                handleSendFailed(peer);
            }
        });
    }

    private class PeersAdapter extends RecyclerView.Adapter<PeersAdapter.ViewHolder> {

        private final LayoutInflater mInflater;

        PeersAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(mInflater.inflate(R.layout.item_peer_main, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final String id = mPeers.keyAt(position);
            final AirDropManager.Peer peer = mPeers.valueAt(position);
            final PeerState state = mPeerStates.get(id);

            assert state != null;

            holder.nameView.setText(peer.name);
            if (state.status != 0) {
                holder.itemView.setSelected(true);
                holder.statusView.setVisibility(View.VISIBLE);
                if (state.status == R.string.status_sending && state.bytesTotal != -1) {
                    holder.statusView.setText(getString(R.string.status_sending_progress,
                            Formatter.formatFileSize(MainActivity.this, state.bytesSent),
                            Formatter.formatFileSize(MainActivity.this, state.bytesTotal)));
                } else {
                    holder.statusView.setText(state.status);
                }
            } else {
                holder.itemView.setSelected(false);
                holder.statusView.setVisibility(View.GONE);
            }
            if (state.status != 0 && state.status != R.string.status_rejected) {
                holder.itemView.setEnabled(false);
                holder.progressBar.setVisibility(View.VISIBLE);
                holder.cancelButton.setVisibility(View.VISIBLE);
                if (state.bytesTotal == -1 || state.status != R.string.status_sending) {
                    holder.progressBar.setIndeterminate(true);
                } else {
                    holder.progressBar.setIndeterminate(false);
                    holder.progressBar.setMax((int) state.bytesTotal);
                    holder.progressBar.setProgress((int) state.bytesSent, true);
                }
            } else {
                holder.itemView.setEnabled(true);
                holder.progressBar.setVisibility(View.GONE);
                holder.cancelButton.setVisibility(View.GONE);
            }
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleItemClick(peer);
                }
            });
            holder.cancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleItemCancelClick(peer, state);
                }
            });
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

            TextView nameView;
            TextView statusView;
            ProgressBar progressBar;
            View cancelButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameView = itemView.findViewById(R.id.name);
                statusView = itemView.findViewById(R.id.status);
                progressBar = itemView.findViewById(R.id.progress);
                cancelButton = itemView.findViewById(R.id.cancel);
            }

        }

    }

    private class PeerState {

        @StringRes
        int status = 0;

        long bytesTotal = -1;
        long bytesSent = 0;

        AirDropManager.Cancelable sending = null;

    }

}
