package org.mokee.fileshare.utils;

import android.bluetooth.le.AdvertiseData;

public class AirDropUtils {

    public static final String SD_SERVICE_TYPE = "_airdrop._tcp";

    private static final int MANUFACTURER_ID = 0x004C;
    private static final byte[] MANUFACTURER_DATA = {
            0x05, 0x12, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00};

    public static AdvertiseData triggerDiscoverable() {
        return new AdvertiseData.Builder()
                .addManufacturerData(MANUFACTURER_ID, MANUFACTURER_DATA)
                .build();
    }

}
