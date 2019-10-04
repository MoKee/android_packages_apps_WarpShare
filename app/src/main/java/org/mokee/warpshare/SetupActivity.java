/*
 * Copyright (C) 2019 The MoKee Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mokee.warpshare;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.mokee.warpshare.airdrop.AirDropManager;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;
import static android.provider.Settings.ACTION_WIFI_SETTINGS;
import static org.mokee.warpshare.airdrop.AirDropManager.STATUS_NO_BLUETOOTH;
import static org.mokee.warpshare.airdrop.AirDropManager.STATUS_NO_WIFI;

@SuppressWarnings("SwitchStatementWithTooFewBranches")
public class SetupActivity extends AppCompatActivity {

    private static final String TAG = "SetupActivity";

    private static final int REQUEST_PERM = 1;

    private AirDropManager mAirDropManager;

    private ViewGroup mGroupPerm;
    private ViewGroup mGroupWifi;
    private ViewGroup mGroupBt;

    private final WifiStateMonitor mWifiStateMonitor = new WifiStateMonitor() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateState();
        }
    };

    private final BluetoothStateMonitor mBluetoothStateMonitor = new BluetoothStateMonitor() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateState();
        }
    };

    private long mLastRequestForPermission = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        mGroupPerm = findViewById(R.id.group_perm);
        findViewById(R.id.perm).setOnClickListener(v -> requestPermission());

        mGroupWifi = findViewById(R.id.group_wifi);
        findViewById(R.id.wifi).setOnClickListener(v -> setupWifi());

        mGroupBt = findViewById(R.id.group_bt);
        findViewById(R.id.bt).setOnClickListener(v -> turnOnBluetooth());

        mAirDropManager = new AirDropManager(this,
                WarpShareApplication.from(this).getCertificateManager());

        mWifiStateMonitor.register(this);
        mBluetoothStateMonitor.register(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mWifiStateMonitor.unregister(this);
        mBluetoothStateMonitor.unregister(this);

        mAirDropManager.destroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateState();
    }

    private void updateState() {
        final int ready = mAirDropManager.ready();
        if (checkSelfPermission(WRITE_EXTERNAL_STORAGE) != PERMISSION_GRANTED) {
            mGroupPerm.setVisibility(View.VISIBLE);
            mGroupWifi.setVisibility(View.GONE);
            mGroupBt.setVisibility(View.GONE);
        } else if (ready == STATUS_NO_WIFI) {
            mGroupPerm.setVisibility(View.GONE);
            mGroupWifi.setVisibility(View.VISIBLE);
            mGroupBt.setVisibility(View.GONE);
        } else if (ready == STATUS_NO_BLUETOOTH) {
            mGroupPerm.setVisibility(View.GONE);
            mGroupWifi.setVisibility(View.GONE);
            mGroupBt.setVisibility(View.VISIBLE);
        } else {
            setResult(RESULT_OK);
            finish();
        }
    }

    private void requestPermission() {
        mLastRequestForPermission = SystemClock.elapsedRealtime();
        requestPermissions(new String[]{WRITE_EXTERNAL_STORAGE}, REQUEST_PERM);
    }

    private void setupWifi() {
        startActivity(new Intent(ACTION_WIFI_SETTINGS));
    }

    private void turnOnBluetooth() {
        startActivity(new Intent(ACTION_REQUEST_ENABLE));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERM:
                if (grantResults[0] != PERMISSION_GRANTED) {
                    // If "Don't ask again" was selected, permissions request will be rejected
                    // immediately after they was requested.
                    if (SystemClock.elapsedRealtime() - mLastRequestForPermission < 200) {
                        final Intent intent = new Intent(ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.fromParts("package", getPackageName(), null));
                        startActivity(intent);
                    }
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

}
