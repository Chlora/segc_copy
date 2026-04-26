package com.sperta.common.crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.security.Key;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class HashUtils {

    private static final String ALGORITHM = "SHA-256";
    public static final String HASH_EXTENSION = ".hash";

    public static byte[] hashBytes(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance(ALGORITHM);
        md.update(data);
        return md.digest();
    }

    public static String hashToBase64(byte[] data) throws Exception {
        return Base64.getEncoder().encodeToString(hashBytes(data));
    }

    private static byte[] hashFile(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance(ALGORITHM);
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }
        return md.digest();
    }

    public static byte[] hashFileWithNonce(File file, long nonce) throws Exception {
        MessageDigest md = MessageDigest.getInstance(ALGORITHM);
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }

        byte[] nonceBytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            nonceBytes[i] = (byte) (nonce & 0xFF);
            nonce >>= 8;
        }
        md.update(nonceBytes);
        return md.digest();
    }

    public static void saveHash(File file) throws Exception {
        byte[] hash = hashFile(file);
        File hashedFile = new File(file.getPath() + HASH_EXTENSION);
        try (FileOutputStream fos = new FileOutputStream(hashedFile)) {
            fos.write(hash);
        }
    }

    public static boolean verifyIntegrity(File file) throws Exception {
        File hashFile = new File(file.getPath() + HASH_EXTENSION);
        if (!hashFile.exists()) {
            return false;
        }
        byte[] storedHash = Files.readAllBytes(hashFile.toPath());
        byte[] computedHash = hashFile(file);
        return Arrays.equals(storedHash, computedHash);
    }

    public static void verifyIntegrityOrExit(File file) throws Exception {
        if (!verifyIntegrity(file)) {
            System.out.println("NOK-INTEGRITY");
            System.exit(1);
        }
    }

    public static SecretKey derivePBEKey(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                65536,
                128);

        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = skf.generateSecret(spec).getEncoded();
        spec.clearPassword(); // seguranca

        return new SecretKeySpec(keyBytes, "AES");
    }

    public static byte[] decryptAndVerify(File encryptedFile, Key pbeKey) {
        try {
            byte[] fileBytes = Files.readAllBytes(encryptedFile.toPath());
            byte[] iv = Arrays.copyOfRange(fileBytes, 0, 16);
            byte[] ciphertext = Arrays.copyOfRange(fileBytes, 16, fileBytes.length);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, pbeKey, new IvParameterSpec(iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            // Step — hash plaintext in memory, compare to <encryptedFile>.hash
            File hashFile = new File(encryptedFile.getPath() + HASH_EXTENSION);
            if (!hashFile.exists()) {
                System.out.println("NOK-INTEGRITY");
                System.exit(1);
            }

            byte[] storedHash = Files.readAllBytes(hashFile.toPath());
            byte[] computedHash = hashBytes(plaintext);

            if (!Arrays.equals(storedHash, computedHash)) {
                System.out.println("NOK-INTEGRITY");
                System.exit(1);
            }

            return plaintext;

        } catch (Exception e) {
            System.out.println("NOK-INTEGRITY");
            System.exit(1);
            return null;
        }
    }

    public static void saveEncryptedWithHash(File targetFile, byte[] plaintext, Key pbeKey) {
        try {
            // hash plaintext bytes directly, save to .hash
            byte[] hash = HashUtils.hashBytes(plaintext);
            File hashFile = new File(targetFile.getPath() + HashUtils.HASH_EXTENSION);
            try (FileOutputStream fos = new FileOutputStream(hashFile)) {
                fos.write(hash);
            }

            // encrypt and write [ IV | ciphertext ] to target
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, pbeKey);
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(plaintext);

            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                fos.write(iv);
                fos.write(ciphertext);
            }

        } catch (Exception e) {
            System.err.println("Erro ao cifrar/guardar " + targetFile.getName() + ": " + e.getMessage());
        }
    }
}
