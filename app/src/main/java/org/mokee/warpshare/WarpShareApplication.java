package org.mokee.warpshare;

import android.app.Application;
import android.content.Context;

import com.mokee.warpshare.CertificateManager;

public class WarpShareApplication extends Application {

    private CertificateManager mCertificateManager;

    static WarpShareApplication from(Context context) {
        return (WarpShareApplication) context.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mCertificateManager = new CertificateManager(this, R.raw.keystore,
                R.raw.apple_root_ca,
                R.raw.mokee_warp_ca);
    }

    CertificateManager getCertificateManager() {
        return mCertificateManager;
    }

}
