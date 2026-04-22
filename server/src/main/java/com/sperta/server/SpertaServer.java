package com.sperta.server;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.AlgorithmParameters;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

public class SpertaServer {
    private static final int DEFAULT_PORT = 22345;
    // private static final long EXPECTED_CLIENT_SIZE = 2734L;

    private static CatalogoUsers catalogoUsers;
    private static CatalogoCasas catalogoCasas;

    private static String cipherPassword;
    private static byte[] serverSalt;

    public static void main(String[] args) {

        if (args.length < 4) {
            System.err.println("Uso: SpertaServer <port> <password-cifra> <keystore> <password-keystore>");
            return;
        }

        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Porto invalido, a usar " + DEFAULT_PORT);
            }
        }

        cipherPassword = args[1];
        String keystorePath = args[2];
        String keystorePassword = args[3];

        if (cipherPassword == null || cipherPassword.isEmpty()) {
            System.err.println("Password de cifra invalida");
            return;
        }

        if (keystorePath == null || keystorePath.isEmpty()) {
            System.err.println("Keystore path invalido");
            return;
        }

        if (keystorePassword == null || keystorePassword.isEmpty()) {
            System.err.println("Keystore password invalido");
            return;
        }

        File ks = new File(keystorePath);
        if (!ks.exists()) {
            System.err.println("Keystore nao encontrado: " + keystorePath);
            return;
        }

        System.setProperty("javax.net.ssl.keyStore", keystorePath);
        System.setProperty("javax.net.ssl.keyStorePassword", keystorePassword);

        try {

            initializeSalt();

            try {
                catalogoUsers = new CatalogoUsers(cipherPassword, serverSalt);
                catalogoCasas = new CatalogoCasas(catalogoUsers, cipherPassword, serverSalt);
            } catch (Exception e) {
                System.out.println("NOK-INTEGRITY");
                System.exit(1);
            }

            System.out.println("SpertaServer a iniciar no porto " + port + "...");

            SSLServerSocketFactory ssf = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();

            try (SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(port)) {

                serverSocket.setNeedClientAuth(false);

                while (true) {
                    try {
                        SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                        System.out.println("Novo cliente conectado.");
                        new ClientHandler(clientSocket).start();
                    } catch (IOException e) {
                        System.err.println("Erro ao aceitar conexao: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        }
    }

    // cria casa
    private static void create(User u, String hm, ObjectOutputStream out) throws IOException {
        if (catalogoCasas.addCasa(hm, u)) {
            System.out.println("Utilizador " + u.nome + " registou casa " + hm + " com sucesso\n");
            out.writeObject("OK");
            return;
        }
        out.writeObject("NOK");
    }

    // da permissao s ao username na casa hm
    private static void add(User u, String username, String hm, String s, ObjectOutputStream out, ObjectInputStream in)
            throws Exception {
        Casa c = catalogoCasas.getWithId(hm);

        if (c == null) {
            out.writeObject("NOHM");
            return;
        }

        User uToAdd = catalogoUsers.getWithNome(username);

        if (uToAdd == null) {
            out.writeObject("NOUSER");
            return;
        }

        if (!c.getOwner().equals(u.nome)) {
            out.writeObject("NOPERM");
            return;
        }

        try {
            Permissao p = Permissao.valueOf(s);
            byte[] ownerWrappedKey = catalogoCasas.getWrappedKey(hm, p, u.nome, cipherPassword);
            if (ownerWrappedKey == null) {
                out.writeObject("NOKEY");
                return;
            }

            out.writeObject("OK-ADD-HANDSHAKE");
            out.writeObject(ownerWrappedKey);
            out.flush();

            Object response = in.readObject();
            if (response instanceof byte[]) {
                byte[] newWrappedKey = (byte[]) response;

                catalogoCasas.saveWrappedKey(hm, p, username, newWrappedKey, cipherPassword);

                c.givePerms(uToAdd, p);
                catalogoCasas.saveCasa(c, cipherPassword);
                out.writeObject("OK");
            } else {
                out.writeObject("NOK");
            }
        } catch (IllegalArgumentException e) {
            out.writeObject("NOK");
        }
    }

    // regista aparelho na casa hm seccao s
    private static void rd(User u, String hm, String s, ObjectOutputStream out, ObjectInputStream in)
            throws Exception {
        Casa c = catalogoCasas.getWithId(hm);

        if (c == null) {
            out.writeObject("NOHM");
            return;
        }

        if (!c.getOwner().equals(u.nome)) {
            out.writeObject("NOPERM");
            return;
        }

        try {
            Permissao p = Permissao.valueOf(s);
            boolean isNewSeccao = !c.ExisteSeccao(p);
            c.addAparelho(p);
            catalogoCasas.saveCasa(c, cipherPassword);

            if (isNewSeccao) {
                out.writeObject("SEND-KEY");
                out.flush();
                byte[] newWrappedKey = (byte[]) in.readObject();
                catalogoCasas.saveWrappedKey(hm, p, u.nome, newWrappedKey, cipherPassword);
                System.out.println("Utilizador " + u.nome + " registou aparelho na seccao " + s + " na casa " + hm
                        + " com sucesso\n");
            } else {
                out.writeObject("OK");
            }
        } catch (IllegalArgumentException e) {
            out.writeObject("NOK");
        }
    }

    // mete o dispositivo d com estado v na casa hm
    private static void ec(User u, String hm, String d, ObjectOutputStream out, ObjectInputStream in)
            throws Exception {
        Casa c = catalogoCasas.getWithId(hm);

        if (c == null) {
            out.writeObject("NOHM");
            return;
        }

        try {
            Permissao p = Permissao.valueOf(String.valueOf(d.charAt(0)));

            if (!c.UserTemPermParaSeccao(u, p)) {
                out.writeObject("NOPERM");
                return;
            }

            if (!c.ExisteAparelho(d)) {
                out.writeObject("NOD");
                return;
            }

            byte[] wrappedKey = catalogoCasas.getWrappedKey(hm, p, u.nome, cipherPassword);
            if (wrappedKey == null) {
                out.writeObject("NOKEY");
                return;
            }

            out.writeObject("OK-KEY");
            out.writeObject(wrappedKey);
            out.flush();

            Object response = in.readObject();
            if (response instanceof String) {
                String estadoCifrado = (String) response;

                if (c.changeEstado(d, estadoCifrado)) {
                    catalogoCasas.saveCasa(c, cipherPassword);
                    System.out
                            .println("Utilizador " + u.nome + " mudou o estado do dispositivo " + d + " com sucesso\n");
                    out.writeObject("OK");
                } else {
                    out.writeObject("NOK");
                }
            } else {
                out.writeObject("NOK");
            }

        } catch (IllegalArgumentException e) {
            out.writeObject("NOK");
        }
    }

    private static void rt(User u, String hm, ObjectOutputStream out) throws IOException {
        Casa c = catalogoCasas.getWithId(hm);

        if (c == null) {
            out.writeObject("NOHM");
            return;
        }

        if (!c.getPermissoes().containsKey(u)) {
            out.writeObject("NOPERM");
            return;
        }

        StringBuilder sb = new StringBuilder();
        out.writeObject("OK-DATA");

        for (Map.Entry<Permissao, Seccao> entry : c.getSeccoes().entrySet()) {
            if (!c.UserTemPermParaSeccao(u, entry.getKey()))
                continue;

            Seccao s = entry.getValue();
            byte[] wrappedKey = catalogoCasas.getWrappedKey(hm, entry.getKey(), u.nome, cipherPassword);

            for (int i = 1; i <= s.getAparelhoCount(); i++) {
                sb.append(entry.getKey().name()).append(i).append(":").append(s.GetUltimoEstado(i)).append("\n");
            }

            out.writeObject(entry.getKey().name());
            out.writeObject(wrappedKey);
        }

        out.writeObject("END-KEYS");
        if (sb.length() == 0) {
            out.writeObject("NODATA");
        } else {
            out.writeObject(sb.toString());
        }
    }

    // da ao cliente o log do dispositivo d da casa hm
    // TODO
    private static void rh(User u, String hm, String d, ObjectOutputStream out) throws IOException {
        Casa c = catalogoCasas.getWithId(hm);

        if (c == null) {
            out.writeObject("NOHM");
            return;
        }

        if (!c.ExisteAparelho(d)) {
            out.writeObject("NOD");
            return;
        }

        Permissao p = Permissao.valueOf(String.valueOf(d.charAt(0)));
        if (!c.UserTemPermParaSeccao(u, p)) {
            out.writeObject("NOPERM");
            return;
        }

        File logFile = new File("ficheiros/casas/" + hm + "/" + d.charAt(0) + "/" + d + ".csv.enc");

        if (!logFile.exists() || logFile.length() == 0) {
            out.writeObject("NODATA");
            return;
        }

        byte[] wrappedKey = catalogoCasas.getWrappedKey(hm, p, u.nome, cipherPassword);

        byte[] logData;

        try {
            logData = verifyAndDecrypt(logFile, cipherPassword, serverSalt);
        } catch (Exception e) {
            System.err.println("Erro de integridade ou decifra: " + e.getMessage());
            out.writeObject("NODATA");
            return;
        }

        out.writeObject("OK");
        out.writeObject(wrappedKey);
        out.writeLong(logData.length);
        out.writeObject(logData);
    }

    // processa a string de comando, faz validacoes, e chama um dos metodos acima
    // TODO
    private static void proccessCommand(String comando, User u, ObjectOutputStream out, ObjectInputStream in)
            throws Exception {
        String[] tokens = comando.trim().split("\\s+");

        switch (tokens[0].toUpperCase()) {
            case "CREATE":
                if (tokens.length < 2) {
                    out.writeObject("NOK");
                    return;
                }
                create(u, tokens[1], out);
                break;
            case "ADD":
                if (tokens.length < 4) {
                    out.writeObject("NOK");
                    return;
                }
                add(u, tokens[1], tokens[2], tokens[3], out, in);
                break;
            case "RD":
                if (tokens.length < 3) {
                    out.writeObject("NOK");
                    return;
                }
                rd(u, tokens[1], tokens[2], out, in);
                break;
            case "EC":
                if (tokens.length < 3) {
                    out.writeObject("NOK");
                    return;
                }
                try {
                    ec(u, tokens[1], tokens[2], out, in);
                } catch (NumberFormatException e) {
                    out.writeObject("NOK");
                }
                break;
            case "RT":
                if (tokens.length < 2) {
                    out.writeObject("NOK");
                    return;
                }
                rt(u, tokens[1], out);
                break;
            case "RH":
                if (tokens.length < 3) {
                    out.writeObject("NOK");
                    return;
                }
                rh(u, tokens[1], tokens[2], out);
                break;
            case "GETCERT":
                if (tokens.length < 2) {
                    out.writeObject("NOK");
                    return;
                }
                File certFile = new File("ficheiros/certs/" + tokens[1] + ".cert");
                if (certFile.exists()) {
                    out.writeObject("OK");
                    out.writeObject(Files.readAllBytes(certFile.toPath()));
                } else {
                    out.writeObject("NOCERT");
                }
                break;
            default:
                out.writeObject("NOK");
                break;
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private User loggedUser;

        private static final int max_attempts = 3;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        // processa a string
        // ve se o utilizador existe. se sim, ve se a pass tem match
        // senao cria novo registo
        // retorna user e escreve no out
        private static synchronized User authenticate(String composta, ObjectOutputStream out, ObjectInputStream in,
                int attempt)
                throws Exception {
            String[] tokens = composta.trim().split("\\s+");

            if (tokens.length < 2) {
                return null;
            }

            if (catalogoUsers.exists(tokens[0])) {

                User u = catalogoUsers.getWithNome(tokens[0]);

                if (catalogoUsers.isUserAuthenticated(u)) {
                    out.writeObject("ALREADY-LOGGED");
                    System.out.println("Utilizador " + u.nome + " tentou conectar-se num 2o cliente. A rejeitar...\n");
                    return null;
                }

                if (catalogoUsers.authenticate(tokens[0], tokens[1])) {
                    out.writeObject("OK-USER");
                    return u;
                }

                if (attempt + 1 == max_attempts) {
                    out.writeObject("TOO-MANY-ATTEMPTS");
                } else {
                    out.writeObject("WRONG-PWD");
                }
                return null;
            }

            catalogoUsers.addUser(tokens[0], tokens[1]);
            out.writeObject("SEND-CERT");
            out.flush();

            byte[] certBytes = (byte[]) in.readObject();
            File certFile = new File("ficheiros/certs/" + tokens[0] + ".cert");
            certFile.getParentFile().mkdirs();
            Files.write(certFile.toPath(), certBytes);

            out.writeObject("OK-NEW-USER");
            return catalogoUsers.getWithNome(tokens[0]);
        }

        @Override
        public void run() {
            // TODO

            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                // atestacao
                // Fase 2: Atestação Remota (Anti-Replay)
                long nonce = new SecureRandom().nextLong();
                out.writeLong(nonce);
                out.flush();

                byte[] clientHash = (byte[]) in.readObject();
                File refJar = new File("ficheiros/SpertaClient.jar");
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(ByteBuffer.allocate(8).putLong(nonce).array());
                md.update(Files.readAllBytes(refJar.toPath()));
                byte[] serverHash = md.digest();

                if (!MessageDigest.isEqual(clientHash, serverHash)) {
                    out.writeObject("NOK-ATTEST");
                    socket.close();
                    return;
                }
                out.writeObject("OK-ATTEST");

                // auth loop
                for (int i = 0; i < max_attempts; i++) {
                    String auth = (String) in.readObject();
                    User result = authenticate(auth, out, in, i);

                    if (result != null) {
                        loggedUser = result;
                        break;
                    }
                }

                // disconnect se excedeu max attempts
                if (loggedUser == null) {
                    System.out.println("Um utilizador tentou autenticar demasiadas vezes. A fechar a ligacao...\n");
                    socket.close();
                    return;
                }

                catalogoUsers.registerAuth(loggedUser);

                System.out.println("Utilizador " + loggedUser.nome + " conectou-se com sucesso\n");

                // command loop
                String command;
                while ((command = (String) in.readObject()) != null) {
                    proccessCommand(command, loggedUser, out, in);
                    out.flush();
                }

            } catch (EOFException e) {
                System.out.println(
                        "Cliente " + (loggedUser != null ? loggedUser.nome : "desconhecido") + " desconectou-se.");
            } catch (Exception e) {
                System.err.println("Erro com cliente " + (loggedUser != null ? loggedUser.nome : "desconhecido") + ": "
                        + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Erro ao fechar socket: " + e.getMessage());
                }

                if (loggedUser != null) {
                    catalogoUsers.unregisterAuth(loggedUser);
                }
            }
        }
    }

    private static void initializeSalt() throws IOException {
        File saltFile = new File("ficheiros/server.salt");
        if (saltFile.exists()) {
            serverSalt = Files.readAllBytes(saltFile.toPath());
        } else {
            serverSalt = new byte[16];
            new SecureRandom().nextBytes(serverSalt);
            saltFile.getParentFile().mkdirs();
            Files.write(saltFile.toPath(), serverSalt);
        }
    }

    public static void encryptAndSign(File targetFile, byte[] cleanData, String password, byte[] salt)
            throws Exception {

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(cleanData);

        File hashFile = new File(targetFile.getAbsolutePath() + ".hash");
        hashFile.getParentFile().mkdirs();
        Files.write(hashFile.toPath(), hash);

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 128);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKeySpec secret = new SecretKeySpec(tmp.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secret);

        byte[] iv = cipher.getIV();
        byte[] encryptedData = cipher.doFinal(cleanData);

        ByteBuffer buffer = ByteBuffer.allocate(iv.length + encryptedData.length);
        buffer.put(iv);
        buffer.put(encryptedData);

        Files.write(targetFile.toPath(), buffer.array());
    }

    public static byte[] verifyAndDecrypt(File encryptedFile, String password, byte[] salt) throws Exception {

        byte[] cipherData = Files.readAllBytes(encryptedFile.toPath());
        File hashFile = new File(encryptedFile.getPath() + ".hash");
        if (!hashFile.exists())
            throw new Exception("Hash missing");
        byte[] expectedHash = Files.readAllBytes(hashFile.toPath());

        byte[] decryptedData = decryptPBE(cipherData, password, salt);

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] actualHash = md.digest(decryptedData);

        if (!MessageDigest.isEqual(actualHash, expectedHash)) {
            throw new Exception("Integrity failed");
        }

        return decryptedData;
    }

    private static byte[] decryptPBE(byte[] data, String password, byte[] salt) throws Exception {

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 128);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKeySpec secret = new SecretKeySpec(tmp.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        byte[] iv = Arrays.copyOfRange(data, 0, 16);
        byte[] encrypted = Arrays.copyOfRange(data, 16, data.length);

        cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
        return cipher.doFinal(encrypted);
    }

    public static String getCipherPassword() { 
        return cipherPassword; 
    }

    public static byte[] getServerSalt() { 
        return serverSalt;
    }
}