package org.mokee.fileshare;

import android.os.Bundle;

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
    protected void handleItemClick(String id) {
        super.handleItemClick(id);
        sendFile(id, getIntent().getClipData());
    }

    @Override
    protected void handleSendSucceed() {
        super.handleSendSucceed();
        finish();
    }

}
