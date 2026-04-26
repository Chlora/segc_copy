package com.sperta.server;

import java.io.*;
import java.util.HashMap;
import java.util.Collection;
import java.util.Map;

import com.sperta.common.crypto.HashUtils;

import java.util.ArrayList;
import java.util.Base64;

public class CatalogoUsers {

    private Map<String, User> tabela;
    private ArrayList<User> autenticados;

    private static final File f = new File("ficheiros/users.txt");

    public CatalogoUsers() {
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
        User u = new User(nome, password);
        tabela.put(nome, u);
        saveToFile();
        return true;
    }

    public synchronized boolean exists(String nome) {
        return tabela.containsKey(nome);
    }

    public synchronized boolean authenticate(String nome, String password) {
        User u = tabela.get(nome);
        if (u == null)
            return false;
        String candidateHash = User.hashPassword(password, u.salt);
        return u.password.equals(candidateHash);
    }

    private synchronized void saveToFile() {
        f.getParentFile().mkdirs();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f))) {
            for (User u : tabela.values()) {
                String saltB64 = Base64.getEncoder().encodeToString(u.salt);
                bw.write(u.nome + ":" + u.password + ":" + saltB64);
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Erro a gravar users: " + e.getMessage());
        }

        try {
            HashUtils.saveHash(f);
        } catch (Exception e) {
            System.out.println("Erro ao dar hash ao ficheiro de users");
            e.printStackTrace();
        }
    }

    private synchronized void loadFromFile() {
        if (!f.exists())
            return;

        try {
            HashUtils.verifyIntegrityOrExit(f);
        } catch (Exception e) {
            System.out.println("NOK-INTEGRITY");
            System.exit(1);
        }

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":", 3);
                if (parts.length == 3) {
                    String nome = parts[0].trim();
                    String hash = parts[1].trim();
                    byte[] salt = Base64.getDecoder().decode(parts[2].trim());
                    tabela.put(nome, User.fromStored(nome, hash, salt));
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao dar load aos users: " + e.getMessage());
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
}