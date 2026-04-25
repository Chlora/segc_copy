package com.sperta.server;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.crypto.SecretKey;

import com.sperta.common.crypto.AESUtils;

public class FileUtils {

    //qnd n ha ficheiro (debug/dev)
    private static final String DEFAULT_CLIENT_PATH = "./server/target/sperta-client.jar";

    public static byte[] salt;

    public static void setSalt(byte[] salt) {
        FileUtils.salt = salt;
    }

    public static String readAttestFile(String path, String password) {
        File f = new File(path);

        if (f.exists()) {
            try {
                SecretKey s = AESUtils.deriveKeyFromPassword(password.toCharArray(), salt);
                byte[] decrypted = AESUtils.decrypt(Files.readAllBytes(f.toPath()), s);
                return new String(decrypted, StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.out.println("Erro ao ler ficheiro attest");
                return null;
            }
        } else {
            try {
                createAttestFile(path, password);
                return readAttestFile(path, password);
            } catch (Exception e) {
                System.out.println("Erro ao criar ficheiro attest");
                e.printStackTrace();
                return null;
            }
        }
    }

    public static byte[] loadOrCreateSalt(String path) {
        File saltFile = new File(path);
        if (saltFile.exists()) {
            try {
                return Files.readAllBytes(saltFile.toPath());
            } catch (IOException e) {
                System.out.println("Erro ao ler serversalt");
                e.printStackTrace();
            }
        } else {
            salt = AESUtils.generateSalt();
            try {
                Files.write(saltFile.toPath(), salt);
                return salt;
            } catch (IOException e) {
                System.out.println("Erro ao criar serversalt");
                e.printStackTrace();
                return null;
            }
        }

        return null;
    }

    private static String createAttestFile(String path, String password) throws Exception {
        SecretKey s = AESUtils.deriveKeyFromPassword(password.toCharArray(), salt);
        byte[] result = AESUtils.encrypt(DEFAULT_CLIENT_PATH.getBytes(StandardCharsets.UTF_8), s);
        File f = new File(path);
        Files.write(f.toPath(), result);

        return path;
    }
}
