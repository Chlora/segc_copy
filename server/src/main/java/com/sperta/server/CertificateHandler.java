package com.sperta.server;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import com.sperta.common.crypto.HashUtils;

public class CertificateHandler {
    private static final String CERT_DIR = "./ficheiros/certs/";


    public static void saveCertificate(String userId, byte[] certBytes) throws Exception {
        File certFile = new File(CERT_DIR + userId + ".cert");
        certFile.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(certFile)) {
            fos.write(certBytes);
        }
        HashUtils.saveHash(certFile);
    }

    public static boolean hasCertificate(String userId) {
        return Files.exists(Paths.get(CERT_DIR + userId + ".cert"));
    }

    public static PublicKey getPublicKey(String userId) throws Exception {
        byte[] certBytes = Files.readAllBytes(Paths.get(CERT_DIR + userId + ".cert"));
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate cert = cf.generateCertificate(new ByteArrayInputStream(certBytes));
        return cert.getPublicKey();
    }

    public static byte[] getCertificate(String userId) throws Exception {
        return Files.readAllBytes(Paths.get(CERT_DIR + userId + ".cert"));
    }
}
