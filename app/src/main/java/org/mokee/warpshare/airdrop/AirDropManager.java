package org.mokee.warpshare.airdrop;

import android.app.Service;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;

import org.mokee.warpshare.GossipyInputStream;
import org.mokee.warpshare.ResolvedUri;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import okhttp3.Call;
import okio.BufferedSink;
import okio.Okio;
import okio.Pipe;

public class AirDropManager {

    public static final int STATUS_OK = 0;
    public static final int STATUS_NO_BLUETOOTH = 1;
    public static final int STATUS_NO_WIFI = 2;

    private static final String TAG = "AirDropManager";

    private static final String INTERFACE_NAME = "wlan0";

    private final AirDropConfigManager mConfigManager;

    private final AirDropBleController mBleController;
    private final AirDropNsdController mNsdController;

    private final AirDropClient mClient;
    private final AirDropServer mServer;

    private final HashMap<String, Peer> mPeers = new HashMap<>();

    private DiscoveryListener mDiscoveryListener;
    private ReceiverListener mReceiverListener;

    private InetAddress mLocalAddress;

    private String mReceivingIp = null;
    private List<String> mReceivingFiles = null;

    private HashMap<String, Call> mOngoingUploads = new HashMap<>();

    private HandlerThread mArchiveThread;
    private Handler mArchiveHandler;

    private Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    public AirDropManager(Context context) {
        mBleController = new AirDropBleController(context);
        mConfigManager = new AirDropConfigManager(context, mBleController);
        mNsdController = new AirDropNsdController(context, mConfigManager, this);

        final AirDropTrustManager trustManager = new AirDropTrustManager(context);

        mClient = new AirDropClient(trustManager);
        mServer = new AirDropServer(trustManager, this);

        mArchiveThread = new HandlerThread("archive");
        mArchiveThread.start();

        mArchiveHandler = new Handler(mArchiveThread.getLooper());
    }

    private long totalLength(List<ResolvedUri> uris) {
        long total = -1;

        for (ResolvedUri uri : uris) {
            final long size = uri.size();
            if (size >= 0) {
                if (total == -1) {
                    total = size;
                } else {
                    total += size;
                }
            }
        }

        return total;
    }

    public int ready() {
        if (!mBleController.ready()) {
            return STATUS_NO_BLUETOOTH;
        }

        if (!checkNetwork()) {
            return STATUS_NO_WIFI;
        }

        return STATUS_OK;
    }

    public void startDiscover(DiscoveryListener discoveryListener) {
        if (ready() != STATUS_OK) {
            return;
        }

        mDiscoveryListener = discoveryListener;

        mBleController.triggerDiscoverable();
        mNsdController.startDiscover();
    }

    public void stopDiscover() {
        mBleController.stop();
        mNsdController.stopDiscover();
    }

    public void startDiscoverable(ReceiverListener receiverListener) {
        if (ready() != STATUS_OK) {
            return;
        }

        mReceiverListener = receiverListener;

        final int port = mServer.start(mLocalAddress.getHostAddress());

        mNsdController.publish(mLocalAddress, port);
    }

    public void stopDiscoverable() {
        mNsdController.unpublish();
        mServer.stop();
    }

    public void destroy() {
        mArchiveHandler.removeCallbacksAndMessages(null);
        mArchiveThread.quit();
    }

    public void registerTrigger(Class<? extends Service> receiverService, String action) {
        mBleController.registerTrigger(receiverService, action);
    }

    private boolean checkNetwork() {
        NetworkInterface iface = null;
        try {
            iface = NetworkInterface.getByName(INTERFACE_NAME);
        } catch (SocketException e) {
            Log.e(TAG, "Failed getting " + INTERFACE_NAME, e);
        }

        mClient.setNetworkInterface(iface);

        if (iface == null) {
            Log.e(TAG, "Cannot get " + INTERFACE_NAME);
            return false;
        }

        final Enumeration<InetAddress> addresses = iface.getInetAddresses();
        Inet6Address address6 = null;
        Inet4Address address4 = null;
        while (addresses.hasMoreElements()) {
            final InetAddress address = addresses.nextElement();
            if (address6 == null && address instanceof Inet6Address) {
                try {
                    // Recreate a non-scoped address since we are going to advertise it out
                    address6 = (Inet6Address) Inet6Address.getByAddress(null, address.getAddress());
                } catch (UnknownHostException ignored) {
                }
            } else if (address4 == null && address instanceof Inet4Address) {
                address4 = (Inet4Address) address;
            }
        }

        mLocalAddress = address4 != null ? address4 : address6;

        if (mLocalAddress == null) {
            Log.e(TAG, "No address on interface " + INTERFACE_NAME);
            return false;
        }

        return true;
    }

    void onServiceResolved(final String id, final String url) {
        final NSDictionary req = new NSDictionary();

        mClient.post(url + "/Discover", req, new AirDropClient.AirDropClientCallback() {
            @Override
            public void onFailure(IOException e) {
                Log.w(TAG, "Failed to discover: " + id, e);
            }

            @Override
            public void onResponse(NSDictionary response) {
                NSObject nameNode = response.get("ReceiverComputerName");
                if (nameNode == null) {
                    Log.w(TAG, "Name is null: " + id);
                    return;
                }

                final Peer peer = new Peer(id, nameNode.toJavaObject(String.class), url);
                mPeers.put(id, peer);

                mDiscoveryListener.onAirDropPeerFound(peer);
            }
        });
    }

    void onServiceLost(String id) {
        final Peer peer = mPeers.remove(id);
        if (peer != null) {
            mDiscoveryListener.onAirDropPeerDisappeared(peer);
        }
    }

    public Cancelable send(final Peer peer, final List<ResolvedUri> uris, final SenderListener listener) {
        final NSDictionary req = new NSDictionary();
        req.put("SenderID", mConfigManager.getId());
        req.put("SenderComputerName", mConfigManager.getName());
        req.put("BundleID", "com.apple.finder");
        req.put("ConvertMediaFormats", false);

        final List<NSDictionary> files = new ArrayList<>();
        for (ResolvedUri uri : uris) {
            final NSDictionary file = new NSDictionary();
            file.put("FileName", uri.name());
            file.put("FileType", "public.content");
            file.put("FileBomPath", uri.path());
            file.put("FileIsDirectory", false);
            file.put("ConvertMediaFormats", 0);
            files.add(file);
        }

        req.put("Files", files);

        Log.d(TAG, "Asking " + peer.id + " to receive " + uris.size() + " files");

        mOngoingUploads.put(peer.id, mClient.post(peer.url + "/Ask", req,
                new AirDropClient.AirDropClientCallback() {
                    @Override
                    public void onFailure(IOException e) {
                        Log.w(TAG, "Failed to ask: " + peer.id, e);
                        mOngoingUploads.remove(peer.id);
                        listener.onAirDropRejected();
                    }

                    @Override
                    public void onResponse(NSDictionary response) {
                        Log.d(TAG, "Accepted");
                        listener.onAirDropAccepted();
                        upload(peer, uris, listener);
                    }
                }));

        return new Cancelable() {
            @Override
            public void cancel() {
                final Call call = mOngoingUploads.remove(peer.id);
                if (call != null) {
                    call.cancel();
                    Log.d(TAG, "Canceled");
                }
            }
        };
    }

    private void upload(final Peer peer, final List<ResolvedUri> uris, final SenderListener listener) {
        final Pipe archive = new Pipe(1024);

        final Runnable onCompressFailed = new Runnable() {
            @Override
            public void run() {
                listener.onAirDropSendFailed();
            }
        };

        final long bytesTotal = totalLength(uris);
        final GossipyInputStream.Listener streamReadListener = new GossipyInputStream.Listener() {
            private long bytesSent = 0;

            @Override
            public void onRead(int length) {
                if (bytesTotal == -1) {
                    return;
                }
                bytesSent += length;
                mMainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onAirDropProgress(bytesSent, bytesTotal);
                    }
                });
            }
        };

        mOngoingUploads.put(peer.id, mClient.post(peer.url + "/Upload",
                Okio.buffer(archive.source()).inputStream(),
                new AirDropClient.AirDropClientCallback() {
                    @Override
                    public void onFailure(IOException e) {
                        Log.e(TAG, "Failed to upload: " + peer.id, e);
                        mOngoingUploads.remove(peer.id);
                        listener.onAirDropSendFailed();
                    }

                    @Override
                    public void onResponse(NSDictionary response) {
                        Log.d(TAG, "Uploaded");
                        mOngoingUploads.remove(peer.id);
                        listener.onAirDropSent();
                    }
                }));

        mArchiveHandler.post(new Runnable() {
            @Override
            public void run() {
                try (final BufferedSink sink = Okio.buffer(archive.sink())) {
                    AirDropArchiveUtil.pack(uris, sink.outputStream(), streamReadListener);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to pack upload payload: " + peer.id, e);
                    mMainThreadHandler.post(onCompressFailed);
                }
            }
        });
    }

    void handleDiscover(String ip, NSDictionary request, AirDropServer.ResultCallback callback) {
        final NSDictionary response = new NSDictionary();
        response.put("ReceiverComputerName", mConfigManager.getName());
        callback.call(response);
    }

    void handleAsk(final String ip, NSDictionary request, final AirDropServer.ResultCallback callback) {
        final NSObject nameNode = request.get("SenderComputerName");
        if (nameNode == null) {
            Log.w(TAG, "Invalid ask from " + ip + ": Missing SenderComputerName");
            callback.call(null);
            return;
        }

        final NSObject filesNode = request.get("Files");
        if (filesNode == null) {
            Log.w(TAG, "Invalid ask from " + ip + ": Missing Files");
            callback.call(null);
            return;
        } else if (!(filesNode instanceof NSArray)) {
            Log.w(TAG, "Invalid ask from " + ip + ": Files is not a array");
            callback.call(null);
            return;
        }

        final NSObject[] files = ((NSArray) filesNode).getArray();
        final List<String> fileNames = new ArrayList<>();
        final List<String> filePaths = new ArrayList<>();
        for (NSObject file : files) {
            final NSDictionary fileNode = (NSDictionary) file;
            final NSObject fileNameNode = fileNode.get("FileName");
            final NSObject filePathNode = fileNode.get("FileBomPath");
            if (fileNameNode != null && filePathNode != null) {
                fileNames.add(fileNameNode.toJavaObject(String.class));
                filePaths.add(filePathNode.toJavaObject(String.class));
            }
        }

        if (fileNames.isEmpty()) {
            Log.w(TAG, "Invalid ask from " + ip + ": No file asked");
            callback.call(null);
            return;
        }

        final String name = nameNode.toJavaObject(String.class);

        mReceiverListener.onAirDropRequest(name, fileNames, new ReceiverCallback() {
            @Override
            public void accept() {
                final NSDictionary response = new NSDictionary();
                response.put("ReceiverModelName", "Android");
                response.put("ReceiverComputerName", mConfigManager.getName());

                mReceivingIp = ip;
                mReceivingFiles = filePaths;

                callback.call(response);
            }

            @Override
            public void reject() {
                callback.call(null);
            }
        });
    }

    void handleUpload(String ip, final InputStream stream, final AirDropServer.ResultCallback callback) {
        if (mReceivingIp == null || mReceivingFiles == null) {
            Log.w(TAG, "Not in transferring state");
            callback.call(null);
            return;
        }

        if (!mReceivingIp.equals(ip)) {
            Log.w(TAG, "Not the accepted IP");
            callback.call(null);
            return;
        }

        final Runnable onDecompressFailed = new Runnable() {
            @Override
            public void run() {
                mReceiverListener.onAirDropTransferFailed();
                mReceivingIp = null;
                mReceivingFiles = null;
                callback.call(null);
            }
        };

        final Runnable onDecompressDone = new Runnable() {
            @Override
            public void run() {
                mReceiverListener.onAirDropTransferDone();
                mReceivingIp = null;
                mReceivingFiles = null;
                callback.call(new NSDictionary());
            }
        };

        final AirDropArchiveUtil.FileFactory fileFactory = new AirDropArchiveUtil.FileFactory() {
            private final int fileCount = mReceivingFiles.size();
            private int fileIndex = 0;

            @Override
            public void onFile(final String name, final long size, InputStream input) {
                final GossipyInputStream.Listener streamReadListener = new GossipyInputStream.Listener() {
                    private long bytesReceived = 0;

                    @Override
                    public void onRead(int length) {
                        bytesReceived += length;
                        mMainThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (fileIndex < fileCount) {
                                    mReceiverListener.onAirDropTransferProgress(name,
                                            bytesReceived, size, fileIndex, fileCount);
                                }
                            }
                        });
                    }
                };

                mReceiverListener.onAirDropTransfer(name, new GossipyInputStream(input, streamReadListener));
                fileIndex++;
            }
        };

        mArchiveHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    AirDropArchiveUtil.unpack(stream, new HashSet<>(mReceivingFiles), fileFactory);
                    mMainThreadHandler.post(onDecompressDone);
                } catch (IOException e) {
                    Log.e(TAG, "Failed receiving files", e);
                    mMainThreadHandler.post(onDecompressFailed);
                }
            }
        });
    }

    public interface DiscoveryListener {

        void onAirDropPeerFound(Peer peer);

        void onAirDropPeerDisappeared(Peer peer);

    }

    public interface SenderListener {

        void onAirDropAccepted();

        void onAirDropRejected();

        void onAirDropProgress(long bytesSent, long bytesTotal);

        void onAirDropSent();

        void onAirDropSendFailed();

    }

    public interface ReceiverListener {

        void onAirDropRequest(String name, List<String> fileNames, ReceiverCallback callback);

        void onAirDropTransfer(String fileName, InputStream input);

        void onAirDropTransferProgress(String fileName,
                                       long bytesReceived, long bytesTotal,
                                       int index, int count);

        void onAirDropTransferDone();

        void onAirDropTransferFailed();

    }

    public interface ReceiverCallback {

        void accept();

        void reject();

    }

    public interface Cancelable {

        void cancel();

    }

    public class Peer {

        public final String id;
        public final String name;

        final String url;

        Peer(String id, String name, String url) {
            this.id = id;
            this.name = name;
            this.url = url;
        }

    }

}
