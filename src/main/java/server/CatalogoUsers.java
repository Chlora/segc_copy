package server;

import java.io.*;
import java.util.HashMap;
import java.util.Collection;
import java.util.Map;
import java.util.ArrayList;

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
        return u.password.equals(password);
    }

    private synchronized void loadFromFile() {
        if (!f.exists()) {
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    String nome = parts[0].trim();
                    String password = parts[1].trim();
                    tabela.put(nome, new User(nome, password));
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
                bw.write(u.nome + "," + u.password);
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
}