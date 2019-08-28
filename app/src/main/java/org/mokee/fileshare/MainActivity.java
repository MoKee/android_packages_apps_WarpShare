package org.mokee.fileshare;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import org.mokee.fileshare.airdrop.AirDropManager;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements AirDropManager.Callback {

    private static final String TAG = "MainActivity";

    private static final int REQUEST_PICK = 1;

    private ArrayMap<String, String> mPeers = new ArrayMap<>();
    private PeersAdapter mAdapter;

    private AirDropManager mAirDropManager;

    private String mPeerPicked = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAirDropManager = new AirDropManager(this, this);

        mAdapter = new PeersAdapter(this);

        final RecyclerView peersView = findViewById(R.id.peers);
        peersView.setAdapter(mAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAirDropManager.startDiscover();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAirDropManager.stopDiscover();
    }

    @Override
    public void onAirDropPeerFound(String id, String name) {
        Log.d(TAG, "Found: " + id + " (" + name + ")");
        mPeers.put(id, name);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onAirDropPeerDisappeared(String id) {
        Log.d(TAG, "Disappeared: " + id);

        if (id.equals(mPeerPicked)) {
            mPeerPicked = null;
        }

        mPeers.remove(id);
        mAdapter.notifyDataSetChanged();
    }

    private void handleItemClick(String id) {
        final Intent intent = getIntent();
        final String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            sendFile(id, intent.getClipData());
        } else {
            mPeerPicked = id;
            Intent requestIntent = new Intent(Intent.ACTION_GET_CONTENT);
            requestIntent.addCategory(Intent.CATEGORY_OPENABLE);
            requestIntent.setType("*/*");
            requestIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(Intent.createChooser(requestIntent, "File"), REQUEST_PICK);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_PICK) {
            if (resultCode == RESULT_OK && mPeerPicked != null && data != null) {
                if (data.getClipData() == null) {
                    sendFile(mPeerPicked, data.getData());
                } else {
                    sendFile(mPeerPicked, data.getClipData());
                }
            } else {
                finish();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void sendFile(String id, Uri rawUri) {
        final ResolvedUri uri = new ResolvedUri(this, rawUri);
        if (!uri.ok) {
            Log.w(TAG, "No file was selected");
            return;
        }

        final List<ResolvedUri> uris = new ArrayList<>();
        uris.add(uri);

        sendFile(id, uris);
    }

    private void sendFile(String id, ClipData clipData) {
        if (clipData == null) {
            Log.w(TAG, "ClipData should not be null");
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
            return;
        }

        sendFile(id, uris);
    }

    private void sendFile(final String id, final List<ResolvedUri> uris) {
        mAirDropManager.ask(id, uris, new AirDropManager.AskCallback() {
            @Override
            public void onAskResult(boolean accepted) {
                Log.d(TAG, "Accepted: " + accepted);
                if (accepted) {
                    mAirDropManager.upload(id, uris);
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
