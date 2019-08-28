package org.mokee.fileshare;

import android.content.Intent;

import androidx.annotation.Nullable;

public class MainActivity extends BasePeersActivity {

    private static final String TAG = "MainActivity";

    private static final int REQUEST_PICK = 1;

    private String mPeerPicked = null;

    @Override
    protected int provideContentViewId() {
        return R.layout.activity_main;
    }

    @Override
    public void onAirDropPeerDisappeared(String id) {
        super.onAirDropPeerDisappeared(id);
        if (id.equals(mPeerPicked)) {
            mPeerPicked = null;
        }
    }

    @Override
    protected void handleItemClick(String id) {
        mPeerPicked = id;
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
                    sendFile(mPeerPicked, data.getData());
                } else {
                    sendFile(mPeerPicked, data.getClipData());
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void handleSendSucceed() {
    }

    @Override
    protected void handleSendFailed() {
    }

}
