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

package org.mokee.warpshare.nearbysharing;

import com.microsoft.connecteddevices.remotesystems.RemoteSystem;

import org.mokee.warpshare.base.Peer;

@SuppressWarnings("WeakerAccess")
public class NearSharePeer extends Peer {

    final RemoteSystem remoteSystem;

    private NearSharePeer(String id, String name, RemoteSystem remoteSystem) {
        super(id, name);
        this.remoteSystem = remoteSystem;
    }

    static NearSharePeer from(RemoteSystem system) {
        return new NearSharePeer(system.getId(), system.getDisplayName(), system);
    }

}
