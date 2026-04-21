package com.sperta.server;

import com.sperta.server.*;

import java.io.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Collection;
import java.util.Map;
import java.util.ArrayList;
import java.util.Base64;

public class CatalogoUsers {

    private Map<String, User> tabela;
    private ArrayList<User> autenticados;

    private static final File f = new File("ficheiros/users.txt");

    public CatalogoUsers(String cipherPassword, byte[] serverSalt) {
        tabela = new HashMap<>();
        autenticados = new ArrayList<User>();
        loadFromFile();
    }

    public synchronized User getWithNome(String nome) {
        return tabela.get(nome);
    }

    public synchronized Collection<User> getAll() {
        return tabela.values();
    }

    public synchronized boolean addUser(String nome, String password) {
        if (tabela.containsKey(nome)) {
            return false;
        }
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        String saltStr = Base64.getEncoder().encodeToString(saltBytes);
        String hashStr = generateHash(password, saltBytes);

        User u = new User(nome, hashStr, saltStr);
        tabela.put(nome, u);
        saveToFile();
        return true;
    }

    public synchronized boolean exists(String nome) {
        return tabela.containsKey(nome);
    }

    public synchronized boolean authenticate(String nome, String password) {
        User u = tabela.get(nome);
        if (u == null) {
            return false;
        }

        byte[] saltBytes = Base64.getDecoder().decode(u.salt);
        String attemptedHash = generateHash(password, saltBytes);
        return u.password.equals(attemptedHash);
    }

    private synchronized void loadFromFile() {
        if (!f.exists()) {
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 3) {
                    tabela.put(parts[0].trim(), new User(parts[0].trim(), parts[1].trim(), parts[2].trim()));
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao dar load aos users: " + e.getMessage());
        }
    }

    private synchronized void saveToFile() {
        f.getParentFile().mkdirs();
        System.out.println("Saving to: " + f.getAbsolutePath());
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f))) {
            for (User u : tabela.values()) {
                bw.write(u.nome + ":" + u.password + ":" + u.salt);
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Erro a gravar users: " + e.getMessage());
        }
    }

    public synchronized boolean isUserAuthenticated(User u) {
        return autenticados.contains(u);
    }

    public synchronized void registerAuth(User u) {
        autenticados.add(u);
    }

    public synchronized void unregisterAuth(User u) {
        autenticados.remove(u);
    }

    private String generateHash(String password, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hash = md.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return null;
        }
    }
}