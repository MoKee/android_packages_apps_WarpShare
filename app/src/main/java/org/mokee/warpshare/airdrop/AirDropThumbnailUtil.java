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

import android.graphics.Bitmap;

import com.gemalto.jp2.JP2Encoder;

import org.mokee.warpshare.ResolvedUri;

class AirDropThumbnailUtil {

    private static final int SIZE = 540;

    static byte[] generate(ResolvedUri uri) {
        final Bitmap thumbnail = uri.thumbnail(SIZE);
        return new JP2Encoder(thumbnail).encode();
    }

}
