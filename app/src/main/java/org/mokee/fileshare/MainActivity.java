package org.mokee.fileshare;

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

import java.io.FileNotFoundException;

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
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            final Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                sendFile(id, uri);
            }
        } else {
            mPeerPicked = id;
            Intent requestIntent = new Intent(Intent.ACTION_GET_CONTENT);
            requestIntent.addCategory(Intent.CATEGORY_OPENABLE);
            requestIntent.setType("*/*");
            startActivityForResult(Intent.createChooser(requestIntent, "File"), REQUEST_PICK);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_PICK) {
            if (resultCode == RESULT_OK && mPeerPicked != null && data != null) {
                sendFile(mPeerPicked, data.getData());
            } else {
                finish();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void sendFile(final String id, Uri uri) {
        final ResolvedUri resolvedUri = new ResolvedUri(this, uri);

        if (!resolvedUri.ok) {
            return;
        }

        mAirDropManager.ask(id, resolvedUri, new AirDropManager.AskCallback() {
            @Override
            public void onAskResult(boolean accepted) {
                Log.d(TAG, "Accepted: " + accepted);
                if (accepted) {
                    upload(id, resolvedUri);
                }
            }
        });
    }

    private void upload(String id, ResolvedUri resolvedUri) {
        try {
            mAirDropManager.upload(id, resolvedUri);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
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
