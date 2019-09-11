package org.mokee.warpshare;

import java.io.IOException;
import java.io.InputStream;

public class GossipyInputStream extends InputStream {

    private final InputStream mSource;
    private final Listener mListener;

    public GossipyInputStream(InputStream source, Listener listener) {
        mSource = source;
        mListener = listener;
    }

    @Override
    public int read() throws IOException {
        final byte[] buf = new byte[1];
        final int ret = read(buf);
        return ret == -1 ? -1 : buf[0];
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        final int ret = mSource.read(b, off, len);
        if (ret == -1) {
            return -1;
        } else {
            mListener.onRead(ret);
            return ret;
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
        mSource.close();
    }

    public interface Listener {

        void onRead(int length);

    }

}
