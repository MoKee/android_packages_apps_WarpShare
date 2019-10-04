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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

class PartialWakeLock {

    private static final String TAG = "PartialWakeLock";

    private final PowerManager.WakeLock mWakeLock;

    PartialWakeLock(Context context, String tag) {
        final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null) {
            mWakeLock = null;
            return;
        }

        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag);
        mWakeLock.setReferenceCounted(false);
    }

    @SuppressLint("WakelockTimeout")
    void acquire() {
        Log.d(TAG, "acquire");
        if (mWakeLock != null) {
            mWakeLock.acquire();
        }
    }

    void release() {
        Log.d(TAG, "release");
        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }

}
