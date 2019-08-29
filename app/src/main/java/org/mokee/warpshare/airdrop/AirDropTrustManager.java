package org.mokee.warpshare.airdrop;

import android.content.Context;

import androidx.annotation.RawRes;

import org.mokee.warpshare.R;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;
import java.util.Locale;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

class AirDropTrustManager {

    private static final char[] PASSWORD = "password".toCharArray();

    private X509TrustManager mTrustManager;
    private SSLSocketFactory mSslSocketFactory;
    private SSLServerSocketFactory mSslServerSocketFactory;

    AirDropTrustManager(Context context) {
        try {
            final KeyStore keyStore = loadKeyStore(context, R.raw.keystore);
            loadCertificates(context, R.raw.apple_root_ca, keyStore);
            loadCertificates(context, R.raw.mokee_warp_ca, keyStore);

            final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, PASSWORD);

            final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            final TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            mTrustManager = (X509TrustManager) trustManagers[0];

            final SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(keyManagerFactory.getKeyManagers(), trustManagers, null);

            mSslSocketFactory = ctx.getSocketFactory();
            mSslServerSocketFactory = ctx.getServerSocketFactory();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    X509TrustManager getTrustManager() {
        return mTrustManager;
    }

    SSLSocketFactory getSslSocketFactory() {
        return mSslSocketFactory;
    }

    SSLServerSocketFactory getSslServerSocketFactory() {
        return mSslServerSocketFactory;
    }

    private KeyStore loadKeyStore(Context context, @RawRes int id) throws GeneralSecurityException, IOException {
        final InputStream jks = context.getResources().openRawResource(id);
        final KeyStore keyStore = KeyStore.getInstance("pkcs12");
        keyStore.load(jks, PASSWORD);
        return keyStore;
    }

    private void loadCertificates(Context context, @RawRes int id, KeyStore keyStore) throws GeneralSecurityException, IOException {
        try (final InputStream ca = context.getResources().openRawResource(id)) {
            final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            final Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(ca);

            int index = 0;
            for (Certificate certificate : certificates) {
                String certificateAlias = String.format(Locale.US, "%d-%d", id, index++);
                keyStore.setCertificateEntry(certificateAlias, certificate);
            }
        }
    }

}
