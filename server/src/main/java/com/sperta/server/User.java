package com.sperta.server;

import com.sperta.common.crypto.AESUtils;


public class User {
    
    public final String nome;
    public final String password;
    public final byte[] salt;

    public User(String nome, String password) {
        this.nome = nome;
        this.password = password;
        salt = AESUtils.generateSalt();
    }
}
