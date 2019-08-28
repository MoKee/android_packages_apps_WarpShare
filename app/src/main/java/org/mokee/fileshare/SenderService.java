package org.mokee.fileshare;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;

import org.mokee.fileshare.airdrop.AirDropManager;

import java.util.ArrayList;
import java.util.List;

public class SenderService extends IntentService {

    private static final String TAG = "SenderService";

    static final String EXTRA_PEER = "peer";
    static final String EXTRA_URIS = "uris";

    private AirDropManager mAirDropManager;

    public SenderService() {
        super("sender");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mAirDropManager = new AirDropManager(this, null);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }

        final AirDropManager.Peer peer = intent.getParcelableExtra(EXTRA_PEER);
        final List<Uri> rawUris = intent.getParcelableArrayListExtra(EXTRA_URIS);

        final List<ResolvedUri> uris = new ArrayList<>();
        for (Uri rawUri : rawUris) {
            uris.add(new ResolvedUri(this, rawUri));
        }

        sendFile(peer, uris);
    }

    private void sendFile(final AirDropManager.Peer peer, final List<ResolvedUri> uris) {
        handleSendConfirming();
        mAirDropManager.ask(peer, uris, new AirDropManager.AskCallback() {
            @Override
            public void onAskResult(boolean accepted) {
                if (accepted) {
                    upload(peer, uris);
                } else {
                    handleSendRejected();
                }
            }
        });
    }

    private void upload(AirDropManager.Peer peer, List<ResolvedUri> uris) {
        handleSending();
        mAirDropManager.upload(peer, uris, new AirDropManager.UploadCallback() {
            @Override
            public void onUploadResult(boolean done) {
                if (done) {
                    handleSendSucceed();
                } else {
                    handleSendFailed();
                }
            }
        });
    }

    private void handleSendConfirming() {
    }

    private void handleSendRejected() {
    }

    private void handleSending() {
    }

    private void handleSendSucceed() {
    }

    private void handleSendFailed() {
    }

}
