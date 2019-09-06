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

import android.app.PendingIntent;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mokee.warpshare.CertificateManager;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    private DiscoveryListener mDiscoveryListener;
    private ReceiverListener mReceiverListener;

    private ExecutorService mArchiveExecutor;

    private Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    public AirDropManager(Context context, CertificateManager certificateManager) {
        mConfigManager = new AirDropConfigManager(context);

        mBleController = new AirDropBleController(context);
        mNsdController = new AirDropNsdController(context, mConfigManager, this);
        mWlanController = new AirDropWlanController();

        mClient = new AirDropClient(certificateManager);
        mServer = new AirDropServer(certificateManager, this);

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

    public void registerTrigger(PendingIntent pendingIntent) {
        mBleController.registerTrigger(pendingIntent);
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
                final Peer peer = Peer.from(response, id, url);
                if (peer == null) {
                    return;
                }

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

    private String getEntryType(ResolvedUri uri) {
        final String mime = uri.type();

        if (!TextUtils.isEmpty(mime)) {
            if (mime.startsWith("image/")) {
                final String name = uri.name().toLowerCase();
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

    public Cancelable send(final Peer peer, final List<ResolvedUri> uris, final SenderListener listener) {
        Log.d(TAG, "Asking " + peer.id + " to receive " + uris.size() + " files");

        final AtomicReference<Cancelable> ref = new AtomicReference<>();
        final AtomicBoolean thumbnailCanceled = new AtomicBoolean(false);

        final String firstType = uris.get(0).type();
        if (!TextUtils.isEmpty(firstType) && firstType.startsWith("image/")) {
            ref.set(new Cancelable() {
                @Override
                public void cancel() {
                    thumbnailCanceled.set(true);
                }
            });

            mArchiveExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final byte[] thumbnail = AirDropThumbnailUtil.generate(uris.get(0));
                    mMainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!thumbnailCanceled.get()) {
                                ask(ref, peer, thumbnail, uris, listener);
                            }
                        }
                    });
                }
            });
        } else {
            ask(ref, peer, null, uris, listener);
        }

        return new Cancelable() {
            @Override
            public void cancel() {
                final Cancelable cancelable = ref.getAndSet(null);
                if (cancelable != null) {
                    cancelable.cancel();
                    Log.d(TAG, "Canceled");
                }
            }
        };
    }

    private void ask(final AtomicReference<Cancelable> ref, final Peer peer, final byte[] icon,
                     final List<ResolvedUri> uris, final SenderListener listener) {
        final NSDictionary req = new NSDictionary();
        req.put("SenderID", mConfigManager.getId());
        req.put("SenderComputerName", mConfigManager.getName());
        req.put("BundleID", "com.apple.finder");
        req.put("ConvertMediaFormats", false);

        final List<NSDictionary> files = new ArrayList<>();
        for (ResolvedUri uri : uris) {
            final NSDictionary file = new NSDictionary();
            file.put("FileName", uri.name());
            file.put("FileType", getEntryType(uri));
            file.put("FileBomPath", uri.path());
            file.put("FileIsDirectory", false);
            file.put("ConvertMediaFormats", 0);
            files.add(file);
        }

        req.put("Files", files);

        if (icon != null) {
            req.put("FileIcon", icon);
        }

        final Call call = mClient.post(peer.url + "/Ask", req,
                new AirDropClient.AirDropClientCallback() {
                    @Override
                    public void onFailure(IOException e) {
                        Log.w(TAG, "Failed to ask: " + peer.id, e);
                        ref.set(null);
                        listener.onAirDropRejected();
                    }

                    @Override
                    public void onResponse(NSDictionary response) {
                        Log.d(TAG, "Accepted");
                        listener.onAirDropAccepted();
                        upload(ref, peer, uris, listener);
                    }
                });

        ref.set(new Cancelable() {
            @Override
            public void cancel() {
                call.cancel();
            }
        });
    }

    private void upload(final AtomicReference<Cancelable> ref, final Peer peer,
                        final List<ResolvedUri> uris, final SenderListener listener) {
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

        final Call call = mClient.post(peer.url + "/Upload",
                Okio.buffer(archive.source()).inputStream(),
                new AirDropClient.AirDropClientCallback() {
                    @Override
                    public void onFailure(IOException e) {
                        Log.e(TAG, "Failed to upload: " + peer.id, e);
                        ref.set(null);
                        listener.onAirDropSendFailed();
                    }

                    @Override
                    public void onResponse(NSDictionary response) {
                        Log.d(TAG, "Uploaded");
                        ref.set(null);
                        listener.onAirDropSent();
                    }
                });

        ref.set(new Cancelable() {
            @Override
            public void cancel() {
                call.cancel();
            }
        });

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
        final JsonObject mokee = new JsonObject();
        mokee.addProperty("APIVersion", 1);

        final JsonObject vendor = new JsonObject();
        vendor.add("org.mokee", mokee);

        final JsonObject capabilities = new JsonObject();
        capabilities.addProperty("Version", 1);
        capabilities.add("Vendor", vendor);

        final NSDictionary response = new NSDictionary();
        response.put("ReceiverComputerName", mConfigManager.getName());
        response.put("ReceiverMediaCapabilities", new Gson().toJson(capabilities).getBytes());

        callback.call(response);
    }

    void handleAsk(final String ip, NSDictionary request, final AirDropServer.ResultCallback callback) {
        final NSObject idNode = request.get("SenderID");
        if (idNode == null) {
            Log.w(TAG, "Invalid ask from " + ip + ": Missing SenderID");
            callback.call(null);
            return;
        }

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

        final String id = idNode.toJavaObject(String.class);
        final String name = nameNode.toJavaObject(String.class);

        final ReceivingSession session = new ReceivingSession(ip, id, name, fileNames, filePaths) {
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

                mReceiverListener.onAirDropTransfer(session, name, new GossipyInputStream(input, streamReadListener));
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

        void onAirDropTransfer(ReceivingSession session, String fileName, InputStream input);

        void onAirDropTransferProgress(ReceivingSession session, String fileName,
                                       long bytesReceived, long bytesTotal,
                                       int index, int count);

        void onAirDropTransferDone(ReceivingSession session);

        void onAirDropTransferFailed(ReceivingSession session);

    }

    public interface Cancelable {

        void cancel();

    }

    @SuppressWarnings("WeakerAccess")
    public static class Peer {

        public final String id;
        public final String name;

        public final JsonObject capabilities;

        final String url;

        private Peer(String id, String name, String url, JsonObject capabilities) {
            this.id = id;
            this.name = name;
            this.url = url;
            this.capabilities = capabilities;
        }

        static Peer from(NSDictionary dict, String id, String url) {
            final NSObject nameNode = dict.get("ReceiverComputerName");
            if (nameNode == null) {
                Log.w(TAG, "Name is null: " + id);
                return null;
            }

            final String name = nameNode.toJavaObject(String.class);

            JsonObject capabilities = null;

            final NSObject capabilitiesNode = dict.get("ReceiverMediaCapabilities");
            if (capabilitiesNode != null) {
                final byte[] caps = capabilitiesNode.toJavaObject(byte[].class);
                try {
                    capabilities = (JsonObject) new JsonParser().parse(new String(caps));
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing ReceiverMediaCapabilities", e);
                }
            }

            return new Peer(id, name, url, capabilities);
        }

        public int getMokeeApiVersion() {
            if (capabilities == null) {
                return 0;
            }

            final JsonObject vendor = capabilities.getAsJsonObject("Vendor");
            if (vendor == null) {
                return 0;
            }

            final JsonObject mokee = vendor.getAsJsonObject("org.mokee");
            if (mokee == null) {
                return 0;
            }

            final JsonElement api = mokee.get("APIVersion");
            if (api == null) {
                return 0;
            }

            return api.getAsInt();
        }

    }

    @SuppressWarnings("WeakerAccess")
    public abstract class ReceivingSession {

        public final String ip;
        public final String id;
        public final String name;
        public final List<String> files;
        public final List<String> paths;

        InputStream stream;

        ReceivingSession(String ip, String id, String name, List<String> files, List<String> paths) {
            this.ip = ip;
            this.id = id;
            this.name = name;
            this.files = files;
            this.paths = paths;
        }

        public abstract void accept();

        public abstract void reject();

        public abstract void cancel();

    }

}
