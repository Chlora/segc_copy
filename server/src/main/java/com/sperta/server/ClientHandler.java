package com.sperta.server;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Map;

import javax.net.ssl.SSLSocket;

import com.sperta.common.crypto.HashUtils;

public class ClientHandler extends Thread {
    private SSLSocket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private static CatalogoUsers catalogoUsers;
    private static CatalogoCasas catalogoCasas;

    private static String ATTEST_PATH;
    private static String password;

    private User loggedUser;
    private PublicKey publicKey;

    private static final int max_attempts = 3;

    public static void setCatalogos(CatalogoUsers catalogoUsers, CatalogoCasas catalogoCasas) {
        ClientHandler.catalogoUsers = catalogoUsers;
        ClientHandler.catalogoCasas = catalogoCasas;
    }

    public static void setAttestParams(String path, String pass) {
        ClientHandler.ATTEST_PATH = path;
        ClientHandler.password = pass;
    }

    public ClientHandler(SSLSocket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        // TODO
        System.out.println("Novo clienthandler inicializado");

        try {
            this.out = new ObjectOutputStream(socket.getOutputStream());
            this.in = new ObjectInputStream(socket.getInputStream());

            // atestacao
            if (attest()) {
                out.writeObject("ATTESTATION_OK");
            } else {
                out.writeObject("ATTESTATION_FAILED");
                socket.close();
                return;
            }

            // auth loop
            for (int i = 0; i < max_attempts; i++) {
                String auth = (String) in.readObject();
                User result = authenticate(auth, i);

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

            getCertificate();
            if (this.publicKey == null) {
                return;
            }

            catalogoUsers.registerAuth(loggedUser);
            System.out.println("Utilizador " + loggedUser.nome + " conectou-se com sucesso\n");

            // command loop
            String command;
            while ((command = (String) in.readObject()) != null) {
                proccessCommand(command, loggedUser);
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

    private boolean attest() {
        SecureRandom sr = new SecureRandom();
        long nonce = sr.nextLong();

        try {
            this.out.writeLong(nonce);
            this.out.flush();
        } catch (Exception e) {
            System.out.println("Erro ao enviar nonce");
            return false;
        }

        byte[] response = new byte[32];
        try {
            response = (byte[]) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Erro ao receber hash nonce");
            // e.printStackTrace();
            return false;
        }

        // calcular hash
        String pathToClient = FileUtils.readAttestFile(ATTEST_PATH, password);

        byte[] hashed;
        try {
            hashed = HashUtils.hashFileWithNonce(new File(pathToClient), nonce);
        } catch (Exception e) {
            System.out.println("Erro ao fazer hash com o nonce");
            e.printStackTrace();
            return false;
        }

        // System.out.println(HexFormat.of().formatHex(response));
        // System.out.println(HexFormat.of().formatHex(hashed));

        return MessageDigest.isEqual(response, hashed);
    }

    private void getCertificate() {
        if (!CertificateHandler.hasCertificate(loggedUser.nome)) {
            try {
                out.writeObject("SEND_CERT");
                byte[] certBytes = (byte[]) in.readObject();
                CertificateHandler.saveCertificate(loggedUser.nome, certBytes);
                System.out.println("Novo certificado adquirido para o user " + loggedUser.nome);
            } catch (Exception e) {
                System.out.println("Erro ao receber certificado");
            }
        }

        try {
            this.publicKey = CertificateHandler.getPublicKey(loggedUser.nome);
        } catch (Exception e) {
            System.out.println("Erro ao aceder a publickey do user " + loggedUser.nome);
            e.printStackTrace();
        }
    }

    // processa a string
    // ve se o utilizador existe. se sim, ve se a pass tem match
    // senao cria novo registo
    // retorna user e escreve no out
    private synchronized User authenticate(String composta, int attempt)
            throws IOException {
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
        out.writeObject("OK-NEW-USER");
        return catalogoUsers.getWithNome(tokens[0]);
    }

    // processa a string de comando, faz validacoes, e chama um dos metodos acima
    // TODO
    private void proccessCommand(String comando, User u) throws IOException {
        String[] tokens = comando.trim().split("\\s+");

        switch (tokens[0].toUpperCase()) {
            case "CREATE":
                if (tokens.length < 2) {
                    out.writeObject("NOK");
                    return;
                }
                create(u, tokens[1]);
                break;
            case "ADD":
                if (tokens.length < 4) {
                    out.writeObject("NOK");
                    return;
                }
                add(u, tokens[1], tokens[2], tokens[3]);
                break;
            case "RD":
                if (tokens.length < 3) {
                    out.writeObject("NOK");
                    return;
                }
                rd(u, tokens[1], tokens[2]);
                break;
            case "EC":
                if (tokens.length < 4) {
                    out.writeObject("NOK");
                    return;
                }
                try {
                    ec(u, tokens[1], tokens[2], Integer.parseInt(tokens[3]));
                } catch (NumberFormatException e) {
                    out.writeObject("NOK");
                }
                break;
            case "RT":
                if (tokens.length < 2) {
                    out.writeObject("NOK");
                    return;
                }
                rt(u, tokens[1]);
                break;
            case "RH":
                if (tokens.length < 3) {
                    out.writeObject("NOK");
                    return;
                }
                rh(u, tokens[1], tokens[2]);
                break;
            default:
                out.writeObject("NOK");
                break;
        }
    }

    // cria casa
    private void create(User u, String hm) throws IOException {
        if (catalogoCasas.addCasa(hm, u)) {
            System.out.println("Utilizador " + u.nome + " registou casa " + hm + " com sucesso\n");
            out.writeObject("OK");
            return;
        }
        out.writeObject("NOK");
    }

    // da permissao s ao username na casa hm
    private void add(User u, String username, String hm, String s) throws IOException {
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
            c.givePerms(uToAdd, p);
            catalogoCasas.saveCasa(c);
            System.out.println("Utilizador " + u.nome + " deu permissao a " + s + " na casa " + hm + " com sucesso\n");
            out.writeObject("OK");
        } catch (IllegalArgumentException e) {
            out.writeObject("NOK");
        }
    }

    // regista aparelho na casa hm seccao s
    private void rd(User u, String hm, String s) throws IOException {
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

            c.addAparelho(p);
            catalogoCasas.saveCasa(c);
            System.out.println(
                    "Utilizador " + u.nome + " registou aparelho na seccao " + s + " na casa " + hm + " com sucesso\n");
            out.writeObject("OK");
        } catch (IllegalArgumentException e) {
            out.writeObject("NOK");
        }
    }

    // mete o dispositivo d com estado v na casa hm
    private void ec(User u, String hm, String d, int v) throws IOException {
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

            if (!c.changeEstado(d, v)) {
                out.writeObject("NOK");
                return;
            }

            catalogoCasas.saveCasa(c);
            System.out.println(
                    "Utilizador " + u.nome + " mudou o estado do dispositivo " + d + " para " + v + " com sucesso\n");
            out.writeObject("OK");
        } catch (IllegalArgumentException e) {
            out.writeObject("NOK");
        }
    }

    private void rt(User u, String hm) throws IOException {
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
        for (Map.Entry<Permissao, Seccao> entry : c.getSeccoes().entrySet()) {
            if (!c.UserTemPermParaSeccao(u, entry.getKey()))
                continue;
            Seccao s = entry.getValue();
            for (int i = 1; i <= s.getAparelhoCount(); i++) {
                sb.append(entry.getKey().name()).append(i).append(":").append(s.GetUltimoEstado(i)).append("\n");
            }
        }

        if (sb.length() == 0) {
            out.writeObject("NODATA");
            return;
        }

        byte[] data = sb.toString().getBytes();
        out.writeObject("OK");
        out.writeLong(data.length);
        out.writeObject(data);
    }

    // da ao cliente o log do dispositivo d da casa hm
    // TODO
    private void rh(User u, String hm, String d) throws IOException {
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

        File logFile = new File("ficheiros/casas/" + hm + "/" + d.charAt(0) + "/" + d + ".csv");

        if (!logFile.exists() || logFile.length() == 0) {
            out.writeObject("NODATA");
            return;
        }

        byte[] data = new byte[(int) logFile.length()];
        try (FileInputStream fis = new FileInputStream(logFile)) {
            fis.read(data);
        }

        out.writeObject("OK");
        out.writeLong(data.length);
        out.writeObject(data);
    }

}
