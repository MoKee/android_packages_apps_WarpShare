package org.mokee.fileshare;

import android.annotation.SuppressLint;
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

import com.dd.plist.NSDictionary;
import com.dd.plist.PropertyListParser;

import org.jetbrains.annotations.NotNull;
import org.mokee.fileshare.utils.AirDropUtils;

import java.io.IOException;
import java.net.Inet6Address;
import java.util.Locale;
import java.util.Objects;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private BluetoothLeAdvertiser advertiser;
    private NsdManager nsdManager;

    private OkHttpClient httpClient;

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

        final AppleTrustManager trustManager = new AppleTrustManager(this);
        httpClient = new OkHttpClient.Builder()
                .sslSocketFactory(trustManager.getSslSocketFactory(), trustManager.getTrustManager())
                .hostnameVerifier(new HostnameVerifier() {
                    @SuppressLint("BadHostnameVerifier")
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                })
                .build();
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

        if (serviceInfo.getHost() instanceof Inet6Address) {
            Log.w(TAG, "IPv6 is not supported yet");
            return;
        }

        final Buffer buffer = new Buffer();

        final NSDictionary body = new NSDictionary();
        try {
            PropertyListParser.saveAsBinary(body, buffer.outputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }

        final String url = String.format(Locale.US, "https://%s:%d/Discover",
                serviceInfo.getHost().getHostName(), serviceInfo.getPort());

        final Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(buffer.readByteString(), MediaType.get("application/octet-stream")))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.e(TAG, "failed", e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                try {
                    NSDictionary rootDict = (NSDictionary) PropertyListParser.parse(Objects.requireNonNull(response.body()).byteStream());
                    Log.d(TAG, rootDict.get("ReceiverComputerName").toJavaObject(String.class));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

}
