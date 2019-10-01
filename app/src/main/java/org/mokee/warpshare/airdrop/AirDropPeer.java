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

package org.mokee.warpshare.airdrop;

import android.util.Log;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.mokee.warpshare.base.Peer;

@SuppressWarnings("WeakerAccess")
public class AirDropPeer extends Peer {

    private static final String TAG = "AirDropPeer";

    public final JsonObject capabilities;

    final String url;

    private AirDropPeer(String id, String name, String url, JsonObject capabilities) {
        super(id, name);
        this.url = url;
        this.capabilities = capabilities;
    }

    static AirDropPeer from(NSDictionary dict, String id, String url) {
        final NSObject nameNode = dict.get("ReceiverComputerName");
        if (nameNode == null) {
            Log.w(TAG, "Name is null: " + id);
            return null;
        }

        final String name = nameNode.toJavaObject(String.class);

        JsonObject capabilities = null;

        final NSObject capabilitiesNode = dict.get("ReceiverMediaCapabilities");
        if (capabilitiesNode != null) {
            final byte[] caps = capabilitiesNode.toJavaObject(byte[].class);
            try {
                capabilities = (JsonObject) new JsonParser().parse(new String(caps));
            } catch (Exception e) {
                Log.e(TAG, "Error parsing ReceiverMediaCapabilities", e);
            }
        }

        return new AirDropPeer(id, name, url, capabilities);
    }

    public int getMokeeApiVersion() {
        if (capabilities == null) {
            return 0;
        }

        final JsonObject vendor = capabilities.getAsJsonObject("Vendor");
        if (vendor == null) {
            return 0;
        }

        final JsonObject mokee = vendor.getAsJsonObject("org.mokee");
        if (mokee == null) {
            return 0;
        }

        final JsonElement api = mokee.get("APIVersion");
        if (api == null) {
            return 0;
        }

        return api.getAsInt();
    }

}
