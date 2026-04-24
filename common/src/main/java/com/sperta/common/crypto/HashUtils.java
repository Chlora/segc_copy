package common.src.main.java.com.sperta.common.crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

public class HashUtils {

    private static final String ALGORITHM = "SHA-256";
    private static final String HASH_EXTENSION = ".hash";


    public static byte[] hash(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance(ALGORITHM);
        return md.digest(data);
    }


    public static String hashToBase64(byte[] data) throws Exception {
        return Base64.getEncoder().encodeToString(hash(data));
    }


    public static byte[] hashFile(File file) throws Exception {
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
        File hashFile = new File(file.getPath() + HASH_EXTENSION);
        try (FileOutputStream fos = new FileOutputStream(hashFile)) {
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
}
