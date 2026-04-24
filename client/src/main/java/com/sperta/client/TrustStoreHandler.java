package com.sperta.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class TrustStoreHandler {

    private KeyStore trustStore;
    private String trustStorePath;
    private final char[] password;
    

    public TrustStoreHandler(String path, char[] password) throws Exception {
        this.trustStorePath = path;
        this.password = password;
        this.trustStore = KeyStore.getInstance("JKS");

        File f = new File(path);
        if (f.exists()) {
            try (FileInputStream fis = new FileInputStream(f)) {
                trustStore.load(fis, password);
            }
        } else {
            trustStore.load(null, password);
        }
    }


    public TrustManager[] getTrustManagers() throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf.getTrustManagers();
    }


    public boolean hasCertificate(String userId) throws Exception {
        return trustStore.containsAlias(userId);
    } // TODO Cada vez que um Owner adicionar um utilizador a uma secção (4.4b)

    public void storeCertificate(String userId, Certificate cert) throws Exception {
        trustStore.setCertificateEntry(userId, cert);
        save();
    }


    public Certificate getCertificate(String userId) throws Exception {
        Certificate c = trustStore.getCertificate(userId);
        if (c == null) {
            throw new Exception("Certificate not found for user: " + userId);
        }
        return c;
    }


    public PublicKey getPublicKey(String userId) throws Exception {
        return getCertificate(userId).getPublicKey();
    }


    private void save() throws Exception {
        try (FileOutputStream fos = new FileOutputStream(trustStorePath)) {
            trustStore.store(fos, password);
        }
    }
}
