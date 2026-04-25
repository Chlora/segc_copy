package com.sperta.common.keystore;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public class KeyStoreManager {

    private KeyStore keyStore;
    private char[] password;
    private String alias;

    public KeyStoreManager(String path, char[] password, String userId) throws Exception {
        this.password = password;
        this.alias = userId;
        this.keyStore = KeyStore.getInstance("JKS");

        try (FileInputStream fis = new FileInputStream(path)) {
            keyStore.load(fis, password);
        }
    }

    public KeyStoreManager(String path, char[] password) throws Exception {
        this.password = password;
        this.alias = null;
        this.keyStore = KeyStore.getInstance("JKS");

        try (FileInputStream fis = new FileInputStream(path)) {
            keyStore.load(fis, password);
        }
    }

    public Certificate getCertificate() throws Exception {
        Certificate cert = keyStore.getCertificate(alias);
        if (cert == null) {
            throw new Exception("Certificate not found for alias: " + alias);
        }
        return cert;
    }

    public PrivateKey getPrivateKey() throws Exception {
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password);
        if (privateKey == null) {
            throw new Exception("Private key not found for alias: " + alias);
        }
        return privateKey;
    }

    public SSLContext createSSLContext() throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        return sslContext;
    }
}