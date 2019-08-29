package org.mokee.fileshare;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.mokee.fileshare.airdrop.AirDropManager;

import java.util.ArrayList;
import java.util.List;

public class ShareBottomSheetFragment extends BottomSheetDialogFragment
        implements AirDropManager.Callback {

    private static final String TAG = "ShareBottomSheetFragment";

    private ShareActivity mParent;

    private final ArrayMap<String, AirDropManager.Peer> mPeers = new ArrayMap<>();
    private final List<ResolvedUri> mUris = new ArrayList<>();

    private PeersAdapter mAdapter;

    private Button mSendButton;

    private String mPeerPicked = null;
    private int mPeerStatus = 0;

    private AirDropManager mAirDropManager;

    public ShareBottomSheetFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAirDropManager = new AirDropManager(getContext(), this);
        mAdapter = new PeersAdapter(getContext());
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

        final ClipData clipData = mParent.getIntent().getClipData();
        if (clipData == null) {
            Log.w(TAG, "ClipData should not be null");
            handleSendFailed();
            return;
        }

        for (int i = 0; i < clipData.getItemCount(); i++) {
            final ResolvedUri uri = new ResolvedUri(getContext(), clipData.getItemAt(i).getUri());
            if (uri.ok) {
                mUris.add(uri);
            }
        }

        if (mUris.isEmpty()) {
            Log.w(TAG, "No file was selected");
            handleSendFailed();
            return;
        }

        final int count = mUris.size();
        final String titleText = getResources().getQuantityString(R.plurals.send_files_to, count, count);
        final TextView titleView = view.findViewById(R.id.title);
        titleView.setText(titleText);

        mSendButton = view.findViewById(R.id.send);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendFile(mPeers.get(mPeerPicked), mUris);
            }
        });

        view.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        mAirDropManager.startDiscover();
    }

    @Override
    public void onPause() {
        super.onPause();
        mAirDropManager.stopDiscover();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        mParent.finish();
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
        mAdapter.notifyDataSetChanged();
        mSendButton.setEnabled(false);
    }

    private void handleSendRejected() {
        mPeerStatus = R.string.status_rejected;
        mAdapter.notifyDataSetChanged();
        mSendButton.setEnabled(true);
        Toast.makeText(getContext(), R.string.toast_rejected, Toast.LENGTH_SHORT).show();
    }

    private void handleSending() {
        mPeerStatus = R.string.status_sending;
        mAdapter.notifyDataSetChanged();
    }

    private void handleSendSucceed() {
        Toast.makeText(getContext(), R.string.toast_completed, Toast.LENGTH_SHORT).show();
        mParent.setResult(Activity.RESULT_OK);
        dismiss();
    }

    private void handleSendFailed() {
        mPeerPicked = null;
        mPeerStatus = 0;
        mAdapter.notifyDataSetChanged();
        mSendButton.setEnabled(true);
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
        public PeersAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PeersAdapter.ViewHolder(mInflater.inflate(R.layout.item_peer, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull PeersAdapter.ViewHolder holder, int position) {
            final String id = mPeers.keyAt(position);
            final AirDropManager.Peer peer = mPeers.valueAt(position);
            final boolean selected = id.equals(mPeerPicked);
            holder.nameView.setText(peer.name);
            holder.itemView.setSelected(selected);
            if (selected && mPeerStatus != 0) {
                holder.statusView.setVisibility(View.VISIBLE);
                holder.statusView.setText(mPeerStatus);
            } else {
                holder.statusView.setVisibility(View.GONE);
            }
            if (selected && mPeerStatus != 0 && mPeerStatus != R.string.status_rejected) {
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
