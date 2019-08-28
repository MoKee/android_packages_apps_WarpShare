package org.mokee.fileshare;

public class ShareActivity extends BasePeersActivity {

    private static final String TAG = "ShareActivity";

    @Override
    protected int provideContentViewId() {
        return R.layout.activity_main;
    }

    @Override
    protected void handleItemClick(String id) {
        sendFile(id, getIntent().getClipData());
    }

    @Override
    protected void handleSendSucceed() {
        finish();
    }

    @Override
    protected void handleSendFailed() {
        finish();
    }

}
