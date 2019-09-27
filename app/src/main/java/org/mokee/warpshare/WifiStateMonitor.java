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
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;

import static android.content.Context.CONNECTIVITY_SERVICE;

class WifiStateMonitor extends ConnectivityManager.NetworkCallback {

    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    private ConnectivityManager getConnectivityManager(Context context) {
        return (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
    }

    void register(Context context) {
        getConnectivityManager(context).registerDefaultNetworkCallback(this, mMainThreadHandler);
    }

    void unregister(Context context) {
        getConnectivityManager(context).unregisterNetworkCallback(this);
        mMainThreadHandler.removeCallbacksAndMessages(null);
    }

}
