package org.mokee.fileshare;

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

import org.mokee.fileshare.airdrop.AirDropManager;

import java.util.ArrayList;
import java.util.List;

abstract class BasePeersActivity extends AppCompatActivity implements AirDropManager.Callback {

    private static final String TAG = "BasePeersActivity";

    protected ArrayMap<String, String> mPeers = new ArrayMap<>();

    private PeersAdapter mAdapter;

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
    public void onAirDropPeerFound(String id, String name) {
        Log.d(TAG, "Found: " + id + " (" + name + ")");
        mPeers.put(id, name);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    @CallSuper
    public void onAirDropPeerDisappeared(String id) {
        Log.d(TAG, "Disappeared: " + id);
        mPeers.remove(id);
        mAdapter.notifyDataSetChanged();
    }

    protected abstract void handleItemClick(String id);

    protected abstract void handleSendSucceed();

    protected abstract void handleSendFailed();

    protected final void sendFile(String id, Uri rawUri) {
        final ResolvedUri uri = new ResolvedUri(this, rawUri);
        if (!uri.ok) {
            Log.w(TAG, "No file was selected");
            handleSendFailed();
            return;
        }

        final List<ResolvedUri> uris = new ArrayList<>();
        uris.add(uri);

        sendFile(id, uris);
    }

    protected final void sendFile(String id, ClipData clipData) {
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

        sendFile(id, uris);
    }

    private void sendFile(final String id, final List<ResolvedUri> uris) {
        mAirDropManager.ask(id, uris, new AirDropManager.AskCallback() {
            @Override
            public void onAskResult(boolean accepted) {
                if (accepted) {
                    upload(id, uris);
                } else {
                    handleSendFailed();
                }
            }
        });
    }

    private void upload(String id, List<ResolvedUri> uris) {
        mAirDropManager.upload(id, uris, new AirDropManager.UploadCallback() {
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
            final String name = mPeers.valueAt(position);
            holder.nameView.setText(name);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleItemClick(id);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mPeers.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            TextView nameView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameView = itemView.findViewById(R.id.name);
            }

        }

    }

}
