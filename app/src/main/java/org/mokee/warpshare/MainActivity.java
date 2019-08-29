package org.mokee.warpshare;

import android.content.Intent;

import androidx.annotation.Nullable;

import org.mokee.warpshare.airdrop.AirDropManager;

public class MainActivity extends BasePeersActivity {

    private static final String TAG = "MainActivity";

    private static final int REQUEST_PICK = 1;

    @Override
    protected int provideContentViewId() {
        return R.layout.activity_main;
    }

    @Override
    protected void handleItemClick(AirDropManager.Peer peer) {
        super.handleItemClick(peer);
        Intent requestIntent = new Intent(Intent.ACTION_GET_CONTENT);
        requestIntent.addCategory(Intent.CATEGORY_OPENABLE);
        requestIntent.setType("*/*");
        requestIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(requestIntent, "File"), REQUEST_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_PICK) {
            if (resultCode == RESULT_OK && mPeerPicked != null && data != null) {
                if (data.getClipData() == null) {
                    sendFile(mPeers.get(mPeerPicked), data.getData());
                } else {
                    sendFile(mPeers.get(mPeerPicked), data.getClipData());
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAirDropManager.startDiscoverable();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAirDropManager.stopDiscoverable();
    }

}
