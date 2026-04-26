package com.sperta.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import com.sperta.common.crypto.AESUtils;

public class User {

    public final String nome;
    public final String password;
    public final byte[] salt;

    public User(String nome, String password) {
        this(nome, password, AESUtils.generateSalt());
    }

    public User(String nome, String password, byte[] salt) {
        this.nome = nome;
        this.salt = salt;
        this.password = hashPassword(password, salt);
    }

    private User(String nome, String hashedPassword, byte[] salt, boolean raw) {
        this.nome = nome;
        this.password = hashedPassword;
        this.salt = salt;
    }

    public static User fromStored(String nome, String hashedPassword, byte[] salt) {
        return new User(nome, hashedPassword, salt, true);
    }

    public static String hashPassword(String password, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
            byte[] combined = new byte[passwordBytes.length + salt.length];
            System.arraycopy(passwordBytes, 0, combined, 0, passwordBytes.length);
            System.arraycopy(salt, 0, combined, passwordBytes.length, salt.length);
            return Base64.getEncoder().encodeToString(md.digest(combined));
        } catch (NoSuchAlgorithmException e) {
            System.out.println("SHA-256 falhou");
            throw new RuntimeException("SHA-256 falhou", e);
        }
    }
}
