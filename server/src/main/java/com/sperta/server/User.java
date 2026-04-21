package com.sperta.server;

public class User {
    
    public final String nome;
    public final String password;
    public final String salt;

    public User(String nome, String password, String salt) {
        this.nome = nome;
        this.password = password;
        this.salt = salt;
    }
}
