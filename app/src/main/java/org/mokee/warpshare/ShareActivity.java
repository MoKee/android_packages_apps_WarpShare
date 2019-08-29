package org.mokee.warpshare;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class ShareActivity extends AppCompatActivity {

    private static final String TAG = "ShareActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ShareBottomSheetFragment shareFragment = new ShareBottomSheetFragment();
        shareFragment.show(getSupportFragmentManager(), shareFragment.getTag());
    }

}
