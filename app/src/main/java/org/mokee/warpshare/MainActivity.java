package org.mokee.warpshare;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;

import org.mokee.warpshare.airdrop.AirDropManager;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("SwitchStatementWithTooFewBranches")
public class MainActivity extends AppCompatActivity implements AirDropManager.DiscoveryListener {

    private static final String TAG = "MainActivity";

    private static final int REQUEST_PICK = 1;

    private ArrayMap<String, AirDropManager.Peer> mPeers = new ArrayMap<>();

    private PeersAdapter mAdapter;

    private String mPeerPicked = null;
    private int mPeerStatus = 0;

    private long mBytesTotal = -1;
    private long mBytesSent = 0;

    private AirDropManager mAirDropManager;

    private AirDropManager.Cancelable mSending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAirDropManager = new AirDropManager(this);
        mAirDropManager.startDiscover(this);

        mAirDropManager.registerTrigger(ReceiverService.class, ReceiverService.ACTION_SCAN_RESULT);

        mAdapter = new PeersAdapter(this);

        final RecyclerView peersView = findViewById(R.id.peers);
        peersView.setAdapter(mAdapter);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAirDropManager.stopDiscover();
        mAirDropManager.destroy();
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
                if (resultCode == RESULT_OK && mPeerPicked != null && data != null) {
                    if (data.getClipData() == null) {
                        sendFile(mPeers.get(mPeerPicked), data.getData());
                    } else {
                        sendFile(mPeers.get(mPeerPicked), data.getClipData());
                    }
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
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onAirDropPeerDisappeared(AirDropManager.Peer peer) {
        Log.d(TAG, "Disappeared: " + peer.id + " (" + peer.name + ")");
        if (peer.id.equals(mPeerPicked)) {
            mPeerPicked = null;
        }
        mPeers.remove(peer.id);
        mAdapter.notifyDataSetChanged();
    }

    private void handleItemClick(AirDropManager.Peer peer) {
        mPeerPicked = peer.id;
        Intent requestIntent = new Intent(Intent.ACTION_GET_CONTENT);
        requestIntent.addCategory(Intent.CATEGORY_OPENABLE);
        requestIntent.setType("*/*");
        requestIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(requestIntent, "File"), REQUEST_PICK);
    }

    private void handleItemCancelClick(AirDropManager.Peer peer) {
        if (mSending != null) {
            mSending.cancel();
            mSending = null;
        }
        handleSendFailed();
    }

    private void handleSendConfirming() {
        mPeerStatus = R.string.status_waiting_for_confirm;
        mBytesTotal = -1;
        mBytesSent = 0;
        mAdapter.notifyDataSetChanged();
    }

    private void handleSendRejected() {
        mSending = null;
        mPeerStatus = R.string.status_rejected;
        mAdapter.notifyDataSetChanged();
    }

    private void handleSending() {
        mPeerStatus = R.string.status_sending;
        mAdapter.notifyDataSetChanged();
    }

    private void handleSendSucceed() {
        mSending = null;
        mPeerPicked = null;
        mPeerStatus = 0;
        mAdapter.notifyDataSetChanged();
    }

    private void handleSendFailed() {
        mSending = null;
        mPeerPicked = null;
        mPeerStatus = 0;
        mAdapter.notifyDataSetChanged();
    }

    private void sendFile(AirDropManager.Peer peer, Uri rawUri) {
        final ResolvedUri uri = new ResolvedUri(this, rawUri);
        if (!uri.ok()) {
            Log.w(TAG, "No file was selected");
            handleSendFailed();
            return;
        }

        final List<ResolvedUri> uris = new ArrayList<>();
        uris.add(uri);

        sendFile(peer, uris);
    }

    private void sendFile(AirDropManager.Peer peer, ClipData clipData) {
        if (clipData == null) {
            Log.w(TAG, "ClipData should not be null");
            handleSendFailed();
            return;
        }

        final List<ResolvedUri> uris = new ArrayList<>();
        for (int i = 0; i < clipData.getItemCount(); i++) {
            final ResolvedUri uri = new ResolvedUri(this, clipData.getItemAt(i).getUri());
            if (uri.ok()) {
                uris.add(uri);
            }
        }

        if (uris.isEmpty()) {
            Log.w(TAG, "No file was selected");
            handleSendFailed();
            return;
        }

        sendFile(peer, uris);
    }

    private void sendFile(final AirDropManager.Peer peer, final List<ResolvedUri> uris) {
        handleSendConfirming();
        mSending = mAirDropManager.send(peer, uris, new AirDropManager.SenderListener() {
            @Override
            public void onAirDropAccepted() {
                handleSending();
            }

            @Override
            public void onAirDropRejected() {
                handleSendRejected();
            }

            @Override
            public void onAirDropProgress(long bytesSent, long bytesTotal) {
                mBytesSent = bytesSent;
                mBytesTotal = bytesTotal;
                mAdapter.notifyDataSetChanged();
            }

            @Override
            public void onAirDropSent() {
                handleSendSucceed();
            }

            @Override
            public void onAirDropSendFailed() {
                handleSendFailed();
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
            final boolean selected = id.equals(mPeerPicked);
            holder.nameView.setText(peer.name);
            holder.itemView.setSelected(selected);
            if (selected && mPeerStatus != 0) {
                holder.statusView.setVisibility(View.VISIBLE);
                if (mPeerStatus == R.string.status_sending && mBytesTotal != -1) {
                    holder.statusView.setText(getString(R.string.status_sending_progress,
                            Formatter.formatFileSize(MainActivity.this, mBytesSent),
                            Formatter.formatFileSize(MainActivity.this, mBytesTotal)));
                } else {
                    holder.statusView.setText(mPeerStatus);
                }
            } else {
                holder.statusView.setVisibility(View.GONE);
            }
            if (selected && mPeerStatus != 0 && mPeerStatus != R.string.status_rejected) {
                holder.itemView.setEnabled(false);
                holder.progressBar.setVisibility(View.VISIBLE);
                holder.cancelButton.setVisibility(View.VISIBLE);
                if (mBytesTotal == -1 || mPeerStatus != R.string.status_sending) {
                    holder.progressBar.setIndeterminate(true);
                } else {
                    holder.progressBar.setIndeterminate(false);
                    holder.progressBar.setMax((int) mBytesTotal);
                    holder.progressBar.setProgress((int) mBytesSent, true);
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
                    handleItemCancelClick(peer);
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

}
