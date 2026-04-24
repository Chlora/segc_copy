package common.src.main.java.com.sperta.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.SecureRandom;

public class AESUtils {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int KEY_SIZE = 128;
    private static final int IV_SIZE = 16;

    // Generate a new random AES section key (Owner calls this on CREATE)
    public static SecretKey generateSectionKey() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance(ALGORITHM);
        kg.init(KEY_SIZE);
        return kg.generateKey();
    }

    // Encrypt data with section key (EC command, Section 4.4 step 4)
    public static byte[] encrypt(byte[] data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        byte[] iv = new byte[IV_SIZE];
        new SecureRandom().nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(data);

        // prepend IV to ciphertext so receiver can decrypt
        byte[] result = new byte[IV_SIZE + encrypted.length];
        System.arraycopy(iv, 0, result, 0, IV_SIZE);
        System.arraycopy(encrypted, 0, result, IV_SIZE, encrypted.length);
        return result;
    }

    // Decrypt data with section key (RT/RH commands, Section 4.4 step 3)
    public static byte[] decrypt(byte[] data, SecretKey key) throws Exception {
        // extract IV from first 16 bytes
        byte[] iv = new byte[IV_SIZE];
        byte[] ciphertext = new byte[data.length - IV_SIZE];
        System.arraycopy(data, 0, iv, 0, IV_SIZE);
        System.arraycopy(data, IV_SIZE, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        return cipher.doFinal(ciphertext);
    }

    // Wrap (encrypt) section key with a user's public key (ADD command, Section 4.4 step 2)
    public static byte[] wrapKey(SecretKey sectionKey, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.WRAP_MODE, publicKey);
        return cipher.wrap(sectionKey);
    }

    // Unwrap (decrypt) section key with user's own private key (Section 4.4 steps 2a, 3, 4b)
    public static SecretKey unwrapKey(byte[] wrappedKey, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.UNWRAP_MODE, privateKey);
        return (SecretKey) cipher.unwrap(wrappedKey, ALGORITHM, Cipher.SECRET_KEY);
    }

    // Reconstruct SecretKey from raw bytes (e.g. after reading from file)
    public static SecretKey keyFromBytes(byte[] keyBytes) {
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
}
