package com.sperta.server;

import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.util.Date;

import javax.security.auth.x500.X500Principal;
import sun.security.*;;

public class Security {

    public class KeystoreGenerator {

        public static void main(String[] args) throws Exception {
            String userId = "alice";
            String keystorePath = "keystore.p12";
            char[] password = "changeit".toCharArray();

            // 1. Generate key pair
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            // 2. Generate self-signed certificate
            Certificate cert = generateCertificate("CN=" + userId, keyPair, 365);

            // 3. Create/load keystore
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, password); // new keystore

            // 4. Store entry (alias = userId)
            ks.setKeyEntry(
                    userId,
                    keyPair.getPrivate(),
                    password,
                    new Certificate[] { cert });

            // 5. Save to file
            try (FileOutputStream fos = new FileOutputStream(keystorePath)) {
                ks.store(fos, password);
            }

            System.out.println("Keystore created with user: " + userId);
        }

        private static Certificate generateCertificate(String dn, KeyPair pair, int days) throws Exception {
            PrivateKey privkey = pair.getPrivate();

        X509CertInfo info = new X509CertInfo();
        Date from = new Date();
        Date to = new Date(from.getTime() + days * 86400000L);
        CertificateValidity interval = new CertificateValidity(from, to);

        BigInteger sn = new BigInteger(64, new SecureRandom());
        X500Name owner = new X500Name(dn);

        info.set(X509CertInfo.VALIDITY, interval);
        info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(sn));
        info.set(X509CertInfo.SUBJECT, owner);
        info.set(X509CertInfo.ISSUER, owner);
        info.set(X509CertInfo.KEY, new CertificateX509Key(pair.getPublic()));
        info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
        info.set(X509CertInfo.ALGORITHM_ID,
                new CertificateAlgorithmId(
                        AlgorithmId.get("SHA256withRSA")));

        // Sign the cert
        X509CertImpl cert = new X509CertImpl(info);
        cert.sign(privkey, "SHA256withRSA");

        return cert;
        }
    }
}
