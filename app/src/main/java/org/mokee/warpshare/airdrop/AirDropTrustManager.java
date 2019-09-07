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
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

class AirDropTrustManager {

    private static final char[] PASSWORD = "password".toCharArray();

    private TrustManager[] mTrustManagers;
    private SSLContext mSSLContext;

    AirDropTrustManager(Context context) {
        try {
            final KeyStore keyStore = loadKeyStore(context, R.raw.keystore);
            loadCertificates(context, R.raw.apple_root_ca, keyStore);
            loadCertificates(context, R.raw.mokee_warp_ca, keyStore);

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

    TrustManager[] getTrustManagers() {
        return mTrustManagers;
    }

    SSLContext getSSLContext() {
        return mSSLContext;
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
