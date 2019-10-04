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
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mokee.warpshare.CertificateManager;

import org.mokee.warpshare.GossipyInputStream;
import org.mokee.warpshare.base.DiscoverListener;
import org.mokee.warpshare.base.Discoverer;
import org.mokee.warpshare.base.Entity;
import org.mokee.warpshare.base.SendListener;
import org.mokee.warpshare.base.Sender;
import org.mokee.warpshare.base.SendingSession;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okio.BufferedSink;
import okio.Okio;
import okio.Pipe;

import static org.mokee.warpshare.airdrop.AirDropTypes.getEntryType;
import static org.mokee.warpshare.airdrop.AirDropTypes.getMimeType;

public class AirDropManager implements
        Discoverer,
        Sender<AirDropPeer> {

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

    private final Map<String, AirDropPeer> mPeers = new HashMap<>();

    private final Map<String, ReceivingSession> mReceivingSessions = new HashMap<>();

    private DiscoverListener mDiscoverListener;
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

    private long totalLength(List<Entity> entities) {
        long total = -1;

        for (Entity entity : entities) {
            final long size = entity.size();
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

    @Override
    public void startDiscover(DiscoverListener discoverListener) {
        if (ready() != STATUS_OK) {
            return;
        }

        mDiscoverListener = discoverListener;

        mBleController.triggerDiscoverable();
        mNsdController.startDiscover(mWlanController.getLocalAddress());
    }

    @Override
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

    void onServiceResolved(String id, String url) {
        final NSDictionary req = new NSDictionary();

        mClient.post(url + "/Discover", req, new AirDropClient.AirDropClientCallback() {
            @Override
            public void onFailure(IOException e) {
                Log.w(TAG, "Failed to discover: " + id, e);
            }

            @Override
            public void onResponse(NSDictionary response) {
                final AirDropPeer peer = AirDropPeer.from(response, id, url);
                if (peer == null) {
                    return;
                }

                mPeers.put(id, peer);
                mDiscoverListener.onPeerFound(peer);
            }
        });
    }

    void onServiceLost(String id) {
        final AirDropPeer peer = mPeers.remove(id);
        if (peer != null) {
            mDiscoverListener.onPeerDisappeared(peer);
        }
    }

    @Override
    public SendingSession send(AirDropPeer peer, List<Entity> entities, SendListener listener) {
        Log.d(TAG, "Asking " + peer.id + " to receive " + entities.size() + " files");

        final AtomicReference<Cancelable> ref = new AtomicReference<>();
        final AtomicBoolean thumbnailCanceled = new AtomicBoolean(false);

        final String firstType = entities.get(0).type();
        if (!TextUtils.isEmpty(firstType) && firstType.startsWith("image/")) {
            ref.set(() -> thumbnailCanceled.set(true));

            mArchiveExecutor.execute(() -> {
                final byte[] thumbnail = AirDropThumbnailUtil.generate(entities.get(0));
                mMainThreadHandler.post(() -> {
                    if (!thumbnailCanceled.get()) {
                        ask(ref, peer, thumbnail, entities, listener);
                    }
                });
            });
        } else {
            ask(ref, peer, null, entities, listener);
        }

        return new SendingSession() {
            @Override
            public final void cancel() {
                final Cancelable cancelable = ref.getAndSet(null);
                if (cancelable != null) {
                    cancelable.cancel();
                    Log.d(TAG, "Canceled");
                }
            }
        };
    }

    private void ask(AtomicReference<Cancelable> ref, AirDropPeer peer, byte[] icon,
                     List<Entity> entities, SendListener listener) {
        final NSDictionary req = new NSDictionary();
        req.put("SenderID", mConfigManager.getId());
        req.put("SenderComputerName", mConfigManager.getName());
        req.put("BundleID", "com.apple.finder");
        req.put("ConvertMediaFormats", false);

        final List<NSDictionary> files = new ArrayList<>();
        for (Entity entity : entities) {
            final NSDictionary file = new NSDictionary();
            file.put("FileName", entity.name());
            file.put("FileType", getEntryType(entity));
            file.put("FileBomPath", entity.path());
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
                        listener.onRejected();
                    }

                    @Override
                    public void onResponse(NSDictionary response) {
                        Log.d(TAG, "Accepted");
                        listener.onAccepted();
                        upload(ref, peer, entities, listener);
                    }
                });

        ref.set(call::cancel);
    }

    private void upload(AtomicReference<Cancelable> ref, AirDropPeer peer,
                        List<Entity> entities, SendListener listener) {
        final Pipe archive = new Pipe(1024);

        final long bytesTotal = totalLength(entities);
        final GossipyInputStream.Listener streamReadListener = new GossipyInputStream.Listener() {
            private long bytesSent = 0;

            @Override
            public void onRead(int length) {
                if (bytesTotal == -1) {
                    return;
                }
                bytesSent += length;
                mMainThreadHandler.post(() -> listener.onProgress(bytesSent, bytesTotal));
            }
        };

        final Call call = mClient.post(peer.url + "/Upload",
                Okio.buffer(archive.source()).inputStream(),
                new AirDropClient.AirDropClientCallback() {
                    @Override
                    public void onFailure(IOException e) {
                        Log.e(TAG, "Failed to upload: " + peer.id, e);
                        ref.set(null);
                        listener.onSendFailed();
                    }

                    @Override
                    public void onResponse(NSDictionary response) {
                        Log.d(TAG, "Uploaded");
                        ref.set(null);
                        listener.onSent();
                    }
                });

        ref.set(call::cancel);

        mArchiveExecutor.execute(() -> {
            try (final BufferedSink sink = Okio.buffer(archive.sink())) {
                AirDropArchiveUtil.pack(entities, sink.outputStream(), streamReadListener);
            } catch (IOException e) {
                Log.e(TAG, "Failed to pack upload payload: " + peer.id, e);
                mMainThreadHandler.post(listener::onSendFailed);
            }
        });
    }

    void handleDiscover(@SuppressWarnings("unused") String ip,
                        @SuppressWarnings("unused") NSDictionary request,
                        AirDropServer.ResultCallback callback) {
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

    void handleAsk(String ip, NSDictionary request, AirDropServer.ResultCallback callback) {
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
        final List<String> fileTypes = new ArrayList<>();
        final List<String> filePaths = new ArrayList<>();
        for (NSObject file : files) {
            final NSDictionary fileNode = (NSDictionary) file;
            final NSObject fileTypeNode = fileNode.get("FileType");
            final NSObject filePathNode = fileNode.get("FileBomPath");
            if (fileTypeNode != null && filePathNode != null) {
                fileTypes.add(getMimeType(fileTypeNode.toJavaObject(String.class)));
                filePaths.add(filePathNode.toJavaObject(String.class));
            }
        }

        if (filePaths.isEmpty()) {
            Log.w(TAG, "Invalid ask from " + ip + ": No file asked");
            callback.call(null);
            return;
        }

        final String id = idNode.toJavaObject(String.class);
        final String name = nameNode.toJavaObject(String.class);

        Bitmap icon = null;
        final NSObject iconNode = request.get("FileIcon");
        if (iconNode != null) {
            try {
                final byte[] data = iconNode.toJavaObject(byte[].class);
                icon = AirDropThumbnailUtil.decode(data);
            } catch (Exception e) {
                Log.e(TAG, "Error decoding file icon", e);
            }
        }

        final ReceivingSession session = new ReceivingSession(ip, id, name, fileTypes, filePaths, icon) {
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

    void handleUpload(String ip, InputStream stream, AirDropServer.ResultCallback callback) {
        final ReceivingSession session = mReceivingSessions.get(ip);
        if (session == null) {
            Log.w(TAG, "Upload from " + ip + " not accepted");
            callback.call(null);
            return;
        }

        session.stream = stream;

        final AirDropArchiveUtil.FileFactory fileFactory = new AirDropArchiveUtil.FileFactory() {
            private final int fileCount = session.paths.size();
            private int fileIndex = 0;

            @Override
            public void onFile(final String name, final long size, InputStream input) {
                final GossipyInputStream.Listener streamReadListener = new GossipyInputStream.Listener() {
                    private long bytesReceived = 0;

                    @Override
                    public void onRead(int length) {
                        bytesReceived += length;
                        mMainThreadHandler.post(() -> {
                            if (fileIndex < fileCount && mReceivingSessions.containsKey(ip)) {
                                mReceiverListener.onAirDropTransferProgress(session, name,
                                        bytesReceived, size, fileIndex, fileCount);
                            }
                        });
                    }
                };

                mReceiverListener.onAirDropTransfer(session, name, new GossipyInputStream(input, streamReadListener));
                fileIndex++;
            }
        };

        mArchiveExecutor.execute(() -> {
            try {
                AirDropArchiveUtil.unpack(stream, new HashSet<>(session.paths), fileFactory);
                mMainThreadHandler.post(() -> {
                    mReceiverListener.onAirDropTransferDone(session);
                    mReceivingSessions.remove(ip);
                    callback.call(new NSDictionary());
                });
            } catch (IOException e) {
                Log.e(TAG, "Failed receiving files", e);
                mMainThreadHandler.post(() -> {
                    mReceiverListener.onAirDropTransferFailed(session);
                    mReceivingSessions.remove(ip);
                    callback.call(null);
                });
            }
        });
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

    private interface Cancelable {

        void cancel();

    }

    @SuppressWarnings("WeakerAccess")
    public abstract class ReceivingSession {

        public final String ip;
        public final String id;
        public final String name;
        public final List<String> types;
        public final List<String> paths;

        @Nullable
        public final Bitmap preview;

        private final Map<String, String> targetFileNames = new HashMap<>();

        InputStream stream;

        ReceivingSession(String ip, String id, String name, List<String> types, List<String> paths,
                         @Nullable Bitmap preview) {
            this.ip = ip;
            this.id = id;
            this.name = name;
            this.types = types;
            this.paths = paths;
            this.preview = preview;
            for (String path : paths) {
                targetFileNames.put(path, assignFileName(path));
            }
        }

        public abstract void accept();

        public abstract void reject();

        public abstract void cancel();

        private String assignFileName(String fileName) {
            final String[] segments = fileName.split("/");
            fileName = segments[segments.length - 1];

            return String.format(Locale.US, "%s_%d_%s",
                    id, System.currentTimeMillis(), fileName);
        }

        @NonNull
        @SuppressWarnings("ConstantConditions")
        public String getFileName(String path) {
            return targetFileNames.get(path);
        }

    }

}
