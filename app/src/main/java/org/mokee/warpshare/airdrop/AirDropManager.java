package org.mokee.warpshare.airdrop;

import android.app.Service;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;

import org.mokee.warpshare.GossipyInputStream;
import org.mokee.warpshare.ResolvedUri;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okio.BufferedSink;
import okio.Okio;
import okio.Pipe;

public class AirDropManager {

    public static final int STATUS_OK = 0;
    public static final int STATUS_NO_BLUETOOTH = 1;
    public static final int STATUS_NO_WIFI = 2;

    private static final String TAG = "AirDropManager";

    private final AirDropConfigManager mConfigManager;

    private final AirDropBleController mBleController;
    private final AirDropNsdController mNsdController;
    private final AirDropWlanController mWlanController;

    private final AirDropClient mClient;
    private final AirDropServer mServer;

    private final Map<String, Peer> mPeers = new HashMap<>();

    private final Map<String, ReceivingSession> mReceivingSessions = new HashMap<>();

    private final Map<String, Call> mOngoingUploads = new HashMap<>();

    private DiscoveryListener mDiscoveryListener;
    private ReceiverListener mReceiverListener;

    private ExecutorService mArchiveExecutor;

    private Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    public AirDropManager(Context context) {
        mConfigManager = new AirDropConfigManager(context);

        mBleController = new AirDropBleController(context);
        mNsdController = new AirDropNsdController(context, mConfigManager, this);
        mWlanController = new AirDropWlanController();

        final AirDropTrustManager trustManager = new AirDropTrustManager(context);

        mClient = new AirDropClient(trustManager);
        mServer = new AirDropServer(trustManager, this);

        mArchiveExecutor = Executors.newFixedThreadPool(10);
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

        if (!mWlanController.ready()) {
            return STATUS_NO_WIFI;
        }

        mClient.setNetworkInterface(mWlanController.getInterface());

        return STATUS_OK;
    }

    public void startDiscover(DiscoveryListener discoveryListener) {
        if (ready() != STATUS_OK) {
            return;
        }

        mDiscoveryListener = discoveryListener;

        mBleController.triggerDiscoverable();
        mNsdController.startDiscover(mWlanController.getLocalAddress());
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

        final int port = mServer.start(mWlanController.getLocalAddress().getHostAddress());

        mNsdController.publish(mWlanController.getLocalAddress(), port);
    }

    public void stopDiscoverable() {
        mNsdController.unpublish();
        mServer.stop();
    }

    public void destroy() {
        mNsdController.destroy();
        mArchiveExecutor.shutdownNow();
    }

    public void registerTrigger(Class<? extends Service> receiverService, String action) {
        mBleController.registerTrigger(receiverService, action);
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

        mArchiveExecutor.execute(new Runnable() {
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

        final ReceivingSession session = new ReceivingSession(ip, name, fileNames, filePaths) {
            @Override
            public void accept() {
                final NSDictionary response = new NSDictionary();
                response.put("ReceiverModelName", "Android");
                response.put("ReceiverComputerName", mConfigManager.getName());

                callback.call(response);
            }

            @Override
            public void reject() {
                callback.call(null);
                mReceivingSessions.remove(ip);
            }

            @Override
            public void cancel() {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException ignored) {
                    }
                    stream = null;
                }
                mReceivingSessions.remove(ip);
            }
        };

        mReceivingSessions.put(ip, session);

        mReceiverListener.onAirDropRequest(session);
    }

    void handleAskCanceled(String ip) {
        final ReceivingSession session = mReceivingSessions.remove(ip);
        mReceiverListener.onAirDropRequestCanceled(session);
    }

    void handleUpload(final String ip, final InputStream stream, final AirDropServer.ResultCallback callback) {
        final ReceivingSession session = mReceivingSessions.get(ip);
        if (session == null) {
            Log.w(TAG, "Upload from " + ip + " not accepted");
            callback.call(null);
            return;
        }

        session.stream = stream;

        final Runnable onDecompressFailed = new Runnable() {
            @Override
            public void run() {
                mReceiverListener.onAirDropTransferFailed(session);
                mReceivingSessions.remove(ip);
                callback.call(null);
            }
        };

        final Runnable onDecompressDone = new Runnable() {
            @Override
            public void run() {
                mReceiverListener.onAirDropTransferDone(session);
                mReceivingSessions.remove(ip);
                callback.call(new NSDictionary());
            }
        };

        final AirDropArchiveUtil.FileFactory fileFactory = new AirDropArchiveUtil.FileFactory() {
            private final int fileCount = session.files.size();
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
                                if (fileIndex < fileCount && mReceivingSessions.containsKey(ip)) {
                                    mReceiverListener.onAirDropTransferProgress(session, name,
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

        mArchiveExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    AirDropArchiveUtil.unpack(stream, new HashSet<>(session.paths), fileFactory);
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

        void onAirDropRequest(ReceivingSession session);

        void onAirDropRequestCanceled(ReceivingSession session);

        void onAirDropTransfer(String fileName, InputStream input);

        void onAirDropTransferProgress(ReceivingSession session, String fileName,
                                       long bytesReceived, long bytesTotal,
                                       int index, int count);

        void onAirDropTransferDone(ReceivingSession session);

        void onAirDropTransferFailed(ReceivingSession session);

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

    public abstract class ReceivingSession {

        public final String ip;
        public final String name;
        public final List<String> files;
        public final List<String> paths;

        InputStream stream;

        ReceivingSession(String ip, String name, List<String> files, List<String> paths) {
            this.ip = ip;
            this.name = name;
            this.files = files;
            this.paths = paths;
        }

        public abstract void accept();

        public abstract void reject();

        public abstract void cancel();

    }

}
