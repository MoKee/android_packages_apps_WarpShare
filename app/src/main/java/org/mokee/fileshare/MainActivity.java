package org.mokee.fileshare;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.mokee.fileshare.utils.AirDropUtils;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private BluetoothLeAdvertiser advertiser;
    private NsdManager nsdManager;

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
        }
    };

    private final NsdManager.DiscoveryListener discoveryListener = new NsdManager.DiscoveryListener() {
        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
        }

        @Override
        public void onDiscoveryStarted(String serviceType) {
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
        }

        @Override
        public void onServiceFound(NsdServiceInfo serviceInfo) {
            handleServiceFound(serviceInfo);
        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            handleServiceLost(serviceInfo);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        final BluetoothAdapter bluetoothAdapter = Objects.requireNonNull(bluetoothManager).getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            final Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
            return;
        }

        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();

        nsdManager = (NsdManager) getSystemService(NSD_SERVICE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        advertiser.startAdvertising(
                new AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                        .setConnectable(true)
                        .build(),
                AirDropUtils.triggerDiscoverable(),
                advertiseCallback);

        nsdManager.discoverServices(AirDropUtils.SD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        advertiser.stopAdvertising(advertiseCallback);
        nsdManager.stopServiceDiscovery(discoveryListener);
    }

    private void handleServiceFound(NsdServiceInfo serviceInfo) {
        nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                handleServiceResolved(serviceInfo);
            }
        });
    }

    private void handleServiceLost(NsdServiceInfo serviceInfo) {
        Log.d(TAG, "Lost: " + serviceInfo.toString());
    }

    private void handleServiceResolved(NsdServiceInfo serviceInfo) {
        Log.d(TAG, "Resolved: " + serviceInfo.toString());
    }

}
