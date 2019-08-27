package org.mokee.fileshare;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.mokee.fileshare.airdrop.AirDropManager;

public class MainActivity extends AppCompatActivity implements AirDropManager.Callback {

    private static final String TAG = "MainActivity";

    private AirDropManager mAirDropManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAirDropManager = new AirDropManager(this, this);
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
    }

    @Override
    public void onAirDropPeerDisappeared(String id) {
        Log.d(TAG, "Disappeared: " + id);
    }

}
