package org.mokee.fileshare;

import android.os.Bundle;

import org.mokee.fileshare.airdrop.AirDropManager;

public class ShareActivity extends BasePeersActivity {

    private static final String TAG = "ShareActivity";

    @Override
    protected int provideContentViewId() {
        return R.layout.activity_share;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.select_to_share);
    }

    @Override
    protected void handleItemClick(AirDropManager.Peer peer) {
        super.handleItemClick(peer);
        sendFile(peer, getIntent().getClipData());
//        finish();
    }

}
