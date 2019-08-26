package org.mokee.warpshare;

import android.content.ClipData;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import org.mokee.warpshare.airdrop.AirDropManager;

import java.util.ArrayList;
import java.util.List;

abstract class BasePeersActivity extends AppCompatActivity implements AirDropManager.Callback {

    private static final String TAG = "BasePeersActivity";

    protected ArrayMap<String, AirDropManager.Peer> mPeers = new ArrayMap<>();

    private PeersAdapter mAdapter;

    protected String mPeerPicked = null;
    private int mPeerStatus = 0;

    private AirDropManager mAirDropManager;

    @LayoutRes
    protected abstract int provideContentViewId();

    @Override
    @CallSuper
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(provideContentViewId());

        mAirDropManager = new AirDropManager(this, this);

        mAdapter = new PeersAdapter(this);

        final RecyclerView peersView = findViewById(R.id.peers);
        peersView.setAdapter(mAdapter);
    }

    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        mAirDropManager.startDiscover();
    }

    @Override
    @CallSuper
    protected void onPause() {
        super.onPause();
        mAirDropManager.stopDiscover();
    }

    @Override
    @CallSuper
    public void onAirDropPeerFound(AirDropManager.Peer peer) {
        Log.d(TAG, "Found: " + peer.id + " (" + peer.name + ")");
        mPeers.put(peer.id, peer);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    @CallSuper
    public void onAirDropPeerDisappeared(AirDropManager.Peer peer) {
        Log.d(TAG, "Disappeared: " + peer.id + " (" + peer.name + ")");
        if (peer.id.equals(mPeerPicked)) {
            mPeerPicked = null;
        }
        mPeers.remove(peer.id);
        mAdapter.notifyDataSetChanged();
    }

    @CallSuper
    protected void handleItemClick(AirDropManager.Peer peer) {
        mPeerPicked = peer.id;
    }

    @CallSuper
    protected void handleSendConfirming() {
        mPeerStatus = R.string.status_waiting_for_confirm;
        mAdapter.notifyDataSetChanged();
    }

    @CallSuper
    protected void handleSendRejected() {
        mPeerStatus = R.string.status_rejected;
        mAdapter.notifyDataSetChanged();
    }

    @CallSuper
    protected void handleSending() {
        mPeerStatus = R.string.status_sending;
        mAdapter.notifyDataSetChanged();
    }

    @CallSuper
    protected void handleSendSucceed() {
        mPeerPicked = null;
        mPeerStatus = 0;
        mAdapter.notifyDataSetChanged();
    }

    @CallSuper
    protected void handleSendFailed() {
        mPeerPicked = null;
        mPeerStatus = 0;
        mAdapter.notifyDataSetChanged();
    }

    protected final void sendFile(AirDropManager.Peer peer, Uri rawUri) {
        final ResolvedUri uri = new ResolvedUri(this, rawUri);
        if (!uri.ok) {
            Log.w(TAG, "No file was selected");
            handleSendFailed();
            return;
        }

        final List<ResolvedUri> uris = new ArrayList<>();
        uris.add(uri);

        sendFile(peer, uris);
    }

    protected final void sendFile(AirDropManager.Peer peer, ClipData clipData) {
        if (clipData == null) {
            Log.w(TAG, "ClipData should not be null");
            handleSendFailed();
            return;
        }

        final List<ResolvedUri> uris = new ArrayList<>();
        for (int i = 0; i < clipData.getItemCount(); i++) {
            final ResolvedUri uri = new ResolvedUri(this, clipData.getItemAt(i).getUri());
            if (uri.ok) {
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
        mAirDropManager.ask(peer, uris, new AirDropManager.AskCallback() {
            @Override
            public void onAskResult(boolean accepted) {
                if (accepted) {
                    upload(peer, uris);
                } else {
                    handleSendRejected();
                }
            }
        });
    }

    private void upload(AirDropManager.Peer peer, List<ResolvedUri> uris) {
        handleSending();
        mAirDropManager.upload(peer, uris, new AirDropManager.UploadCallback() {
            @Override
            public void onUploadResult(boolean done) {
                if (done) {
                    handleSendSucceed();
                } else {
                    handleSendFailed();
                }
            }
        });
    }

    private class PeersAdapter extends RecyclerView.Adapter<PeersAdapter.ViewHolder> {

        private final LayoutInflater mInflater;

        PeersAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(mInflater.inflate(R.layout.item_peer, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final String id = mPeers.keyAt(position);
            final AirDropManager.Peer peer = mPeers.valueAt(position);
            holder.nameView.setText(peer.name);
            if (id.equals(mPeerPicked) && mPeerStatus != 0) {
                holder.statusView.setVisibility(View.VISIBLE);
                holder.statusView.setText(mPeerStatus);
            } else {
                holder.statusView.setVisibility(View.GONE);
            }
            if (id.equals(mPeerPicked) && mPeerStatus != 0 && mPeerStatus != R.string.status_rejected) {
                holder.itemView.setEnabled(false);
            } else {
                holder.itemView.setEnabled(true);
            }
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleItemClick(peer);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mPeers.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            TextView nameView;
            TextView statusView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameView = itemView.findViewById(R.id.name);
                statusView = itemView.findViewById(R.id.status);
            }

        }

    }

}
