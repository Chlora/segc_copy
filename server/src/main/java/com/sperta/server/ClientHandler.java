package com.sperta.server;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.SocketException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLSocket;

import com.sperta.common.Enums.Section;
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

            // System.out.println("getcert");
            getCertificate();
            if (this.publicKey == null) {
                return;
            }

            catalogoUsers.registerAuth(loggedUser);
            System.out.println("Utilizador " + loggedUser.nome + " conectou-se com sucesso\n");

            // command loop
            String command;
            while ((command = (String) in.readObject()) != null) {
                // System.out.println(command);
                proccessCommand(command, loggedUser);
                out.flush();
            }

        } catch (EOFException e) {
            System.out.println(
                    "Cliente " + (loggedUser != null ? loggedUser.nome : "desconhecido") + " desconectou-se.");
        } catch (SocketException e) {
            System.out
                    .println("Cliente " + (loggedUser != null ? loggedUser.nome : "desconhecido") + " desconectou-se.");
        } catch (Exception e) {
            System.err.println("Erro com cliente " + (loggedUser != null ? loggedUser.nome : "desconhecido") + ": "
                    + e.getMessage());
            e.printStackTrace();
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

    private void sendCertToClient(String userId) {
        // System.out.println("sendcerttoclient");
        if (!catalogoUsers.exists(userId)) {
            try {
                out.writeObject("NO-USER");
                return;
            } catch (IOException e) {
                System.out.println("Erro ao enviar falta de certificado para o cliente");
                e.printStackTrace();
                return;
            }
        }

        try {
            byte[] cert = CertificateHandler.getCertificate(userId);
            out.writeObject(cert);
        } catch (Exception e) {
            System.out.println("Utilizador " + loggedUser.nome + " tentou pedir certificado do utilizador " + userId);
            try {
                out.writeObject("NO-USER");
            } catch (IOException e1) {
                System.out.println("Erro ao enviar falta de certificado para o cliente");
                e1.printStackTrace();
            }
            e.printStackTrace();
        }
    }

    private void sendSectionKey(String hm, String s) {
        Section sec;
        try {
            sec = Section.valueOf(s);
        } catch (Exception e) {
            try {
                out.writeObject("NOK");
                System.out.println("Seccao " + s + " nao suportada");
            } catch (IOException e1) {
                System.out.println("Erro de IO");
                e1.printStackTrace();
                return;
            }
            return;
        }

        Casa c = catalogoCasas.getWithId(hm);

        try {
            if (c == null) {
                out.writeObject("NOHM");
                return;
            }
            if (!c.getOwner().equals(loggedUser.nome)) {
                out.writeObject("NOPERM");
                return;
            }
        } catch (Exception e) {
            System.out.println("Erro ao responder ao pedido de sectionkey do user " + loggedUser.nome);
            return;
        }

        try {
            out.writeObject("OK");
            byte[] skey = SectionKeyUtils.getKeyFile(hm, sec, loggedUser.nome);
            out.writeObject(skey);
            out.flush();
        } catch (Exception e) {
            System.out.println("Erro ao obter sectionkey da home " + hm);
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
                out.writeObject("SEND-CERT");
                System.out.println("A pedir certificado ao user " + loggedUser.nome);
                byte[] certBytes = (byte[]) in.readObject();
                CertificateHandler.saveCertificate(loggedUser.nome, certBytes);
                System.out.println("Novo certificado adquirido para o user " + loggedUser.nome);
            } catch (Exception e) {
                System.out.println("Erro ao receber certificado");
                e.printStackTrace();
                return;
            }
        }

        try {
            this.publicKey = CertificateHandler.getPublicKey(loggedUser.nome);
        } catch (Exception e) {
            System.out.println("Erro ao aceder a publickey do user " + loggedUser.nome);
            e.printStackTrace();
        }

        try {
            out.writeObject("CERT-GOOD");
        } catch (IOException e) {
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
    private void proccessCommand(String comando, User u) throws IOException {
        String[] tokens = comando.trim().split("\\s+");

        switch (tokens[0].toUpperCase()) {
            case "CREATE":
                if (tokens.length != 2) {
                    out.writeObject("NOK");
                    return;
                }
                create(u, tokens[1]);
                break;
            case "ADD":
                if (tokens.length != 4) {
                    out.writeObject("NOK");
                    return;
                }
                add(u, tokens[1], tokens[2], tokens[3]);
                break;
            case "RD":
                if (tokens.length != 3) {
                    out.writeObject("NOK");
                    return;
                }
                rd(u, tokens[1], tokens[2]);
                break;
            case "EC":
                if (tokens.length != 3) {
                    out.writeObject("NOK");
                    return;
                }
                try {
                    ec(u, tokens[1], tokens[2]);
                } catch (NumberFormatException e) {
                    out.writeObject("NOK");
                }
                break;
            case "RT":
                if (tokens.length != 2) {
                    out.writeObject("NOK");
                    return;
                }
                rt(u, tokens[1]);
                break;
            case "RH":
                if (tokens.length != 3) {
                    out.writeObject("NOK");
                    return;
                }
                rh(u, tokens[1], tokens[2]);
                break;
            case "GET-CERT":
                if (tokens.length != 2) {
                    out.writeObject("NOK");
                    return;
                }
                sendCertToClient(tokens[1]);
                break;
            case "GET-SKEY":
                if (tokens.length != 3) {
                    out.writeObject("NOK");
                    return;
                }
                sendSectionKey(tokens[1], tokens[2]);
                break;
            default:
                out.writeObject("NOK");
                break;
        }
    }

    // cria casa
    private void create(User u, String hm) throws IOException {
        if (catalogoCasas.addCasa(hm, u)) {
            out.writeObject("OK");

            // receber section keys
            for (Section s : Section.values()) {
                try {
                    byte[] wrappedKey = (byte[]) in.readObject();
                    SectionKeyUtils.saveKeyFile(wrappedKey, hm, s, u.nome);
                } catch (Exception e) {
                    System.out.println("Erro a receber section keys");
                    e.printStackTrace();
                    out.writeObject("NOK");
                    return;
                }
            }

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
            byte[] rewrapped = (byte[]) in.readObject();
            if (c.UserTemPermParaSeccao(catalogoUsers.getWithNome(username), Permissao.valueOf(s))) {
                System.out.println("User " + u.nome + " tentou adicionar " + username + " mas já tinha permissões");
                out.writeObject("NOK");
                return;
            }
            SectionKeyUtils.saveKeyFile(rewrapped, hm, Section.valueOf(s), username);
        } catch (Exception e) {
            System.out.println("Erro ao tentar salvar keyfile a pedido de " + loggedUser.nome);
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
    private void ec(User u, String hm, String d) throws IOException {
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

            out.writeObject("OK");
            out.flush();

            byte[] secKey = SectionKeyUtils.getKeyFile(hm, Section.valueOf(String.valueOf(d.charAt(0))), u.nome);
            out.writeObject(secKey);
            out.flush();

            byte[] cypheredInt;
            try {
                cypheredInt = (byte[]) in.readObject();
                // System.out.println(HexFormat.of().formatHex(cypheredInt));
            } catch (Exception e) {
                System.out.println("Erro a receber int cifrado no EC");
                e.printStackTrace();
                return;
            }

            if (!c.changeEstado(d, cypheredInt)) {
                out.writeObject("NOK");
                return;
            }

            catalogoCasas.saveCasa(c);
            System.out.println(
                    "Utilizador " + u.nome + " mudou o estado do dispositivo " + d + " para "
                            + HexFormat.of().formatHex(cypheredInt) + " com sucesso\n");
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

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream payload = new ObjectOutputStream(baos);

        Map<Permissao, Seccao> seccoes = catalogoCasas.getWithId(hm).getSeccoes();

        Map<Permissao, List<Aparelho>> eligibleMap = new LinkedHashMap<>();
        for (Map.Entry<Permissao, Seccao> entry : seccoes.entrySet()) {
            Permissao p = entry.getKey();
            Seccao s = entry.getValue();

            if (c.UserTemPermParaSeccao(u, p)) {
                List<Aparelho> valid = new ArrayList<>();
                for (Aparelho a : s.getAparelhos()) {
                    if (a.getEstado() != null && a.getEstado().length > 4)
                        valid.add(a);
                }
                if (!valid.isEmpty())
                    eligibleMap.put(p, valid);
            }
        }

        if (eligibleMap.isEmpty()) {
            out.writeObject("NODATA");
            return;
        }

        out.writeObject("OK");

        payload.writeObject(eligibleMap.size());

        for (Map.Entry<Permissao, List<Aparelho>> entry : eligibleMap.entrySet()) {
            Permissao p = entry.getKey();
            List<Aparelho> valid = entry.getValue();

            payload.writeObject(valid.size());
            byte[] sectionKey = SectionKeyUtils.getKeyFile(hm, Section.valueOf(p.name()), u.nome);
            payload.writeObject(sectionKey);
            payload.flush();

            for (Aparelho a : valid) {
                payload.writeObject(a.nome);
                payload.writeObject(a.getEstado());
                payload.flush();
            }
        }

        byte[] data = baos.toByteArray();
        out.writeLong(data.length);
        out.write(data);
        out.flush();
    }

    // da ao cliente o log do dispositivo d da casa hm
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

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream payload = new ObjectOutputStream(baos);

        byte[] sectionKey = SectionKeyUtils.getKeyFile(hm, Section.valueOf(p.name()), u.nome);
        payload.writeObject(sectionKey);
        payload.flush();
        
        byte[] data = new byte[(int) logFile.length()];
        data = Files.readAllBytes(logFile.toPath());

        payload.writeObject(data);
        payload.flush();
        
        out.writeObject("OK");
        byte[] data2 = baos.toByteArray();
        out.writeLong(data2.length);
        out.write(data2);
        out.flush();
    }

}
