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

import android.text.TextUtils;

import org.mokee.warpshare.base.Entity;

class AirDropTypes {

    static String getEntryType(Entity entity) {
        final String mime = entity.type();

        if (!TextUtils.isEmpty(mime)) {
            if (mime.startsWith("image/")) {
                final String name = entity.name().toLowerCase();
                if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                    return "public.jpeg";
                } else if (name.endsWith(".jp2")) {
                    return "public.jpeg-2000";
                } else if (name.endsWith(".gif")) {
                    return "com.compuserve.gif";
                } else if (name.endsWith(".png")) {
                    return "public.png";
                } else {
                    return "public.image";
                }
            } else if (mime.startsWith("audio/")) {
                return "public.audio";
            } else if (mime.startsWith("video/")) {
                return "public.video";
            }
        }

        return "public.content";
    }

    static String getMimeType(String entryType) {
        switch (entryType) {
            case "public.jpeg":
                return "image/jpeg";
            case "public.jpeg-2000":
                return "image/jp2";
            case "com.compuserve.gif":
                return "image/gif";
            case "public.png":
                return "image/png";
            case "public.image":
                return "image/*";
            case "public/audio":
                return "audio/*";
            case "public/video":
                return "video/*";
            default:
                return "*/*";
        }
    }

}
