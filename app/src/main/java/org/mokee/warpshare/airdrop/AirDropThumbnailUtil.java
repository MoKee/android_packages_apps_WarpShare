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
