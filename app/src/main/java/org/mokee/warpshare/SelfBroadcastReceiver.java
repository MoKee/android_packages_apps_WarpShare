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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

abstract class SelfBroadcastReceiver extends BroadcastReceiver {

    private final Object mLock = new Object();

    private final IntentFilter mIntentFilter = new IntentFilter();

    private boolean mRegistered = false;

    SelfBroadcastReceiver(String... actions) {
        for (String action : actions) {
            mIntentFilter.addAction(action);
        }
    }

    void register(Context context) {
        synchronized (mLock) {
            if (!mRegistered) {
                context.registerReceiver(this, mIntentFilter);
                mRegistered = true;
            }
        }
    }

    void unregister(Context context) {
        synchronized (mLock) {
            if (mRegistered) {
                context.unregisterReceiver(this);
                mRegistered = false;
            }
        }
    }

}
