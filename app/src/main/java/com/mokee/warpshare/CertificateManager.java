package com.mokee.warpshare;

import android.content.Context;

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
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class CertificateManager {

    private static final char[] PASSWORD = "kdha!%^bna9$-1kNJ19j".toCharArray();

    private TrustManager[] mTrustManagers;
    private SSLContext mSSLContext;

    public CertificateManager(Context context, int keyStoreRes, int... caRes) {
        try {
            final KeyStore keyStore = loadKeyStore(context, keyStoreRes);
            for (int ca : caRes) {
                loadCertificates(context, ca, keyStore);
            }

            final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, PASSWORD);

            final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            mTrustManagers = trustManagerFactory.getTrustManagers();

            mSSLContext = SSLContext.getInstance("TLS");
            mSSLContext.init(keyManagerFactory.getKeyManagers(), mTrustManagers, null);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public TrustManager[] getTrustManagers() {
        return mTrustManagers;
    }

    public SSLContext getSSLContext() {
        return mSSLContext;
    }

    private KeyStore loadKeyStore(Context context, int id) throws GeneralSecurityException, IOException {
        final InputStream jks = context.getResources().openRawResource(id);
        final KeyStore keyStore = KeyStore.getInstance("pkcs12");
        keyStore.load(jks, PASSWORD);
        return keyStore;
    }

    private void loadCertificates(Context context, int id, KeyStore keyStore) throws GeneralSecurityException, IOException {
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
