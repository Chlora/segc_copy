package com.sperta.client;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.format.DateTimeFormatter;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.time.LocalDateTime;

import javax.crypto.SecretKey;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.sperta.common.keystore.KeyStoreManager;
import com.sperta.common.crypto.*;

public class SpertaClient {

    private static final File ROOT_DIR = new File("ficheirosRecebidos/");
    private static final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static ObjectOutputStream out;
    private static ObjectInputStream in;
    private static Scanner scanner;

    private static TrustStoreHandler tsh;
    private static KeyStoreManager ksm;

    private static SSLSocket socket;

    public static void main(String[] args) {

        if (args.length < 7) {
            System.out.println(
                    "Uso: SpertaClient <serverAddress> <truststore> <password-truststore> <keystore> <password-keystore> <user-id> <password>");
            return;
        }

        String truststorePath = args[1];
        char[] truststorePwd = args[2].toCharArray();
        String keystorePath = args[3];
        char[] keystorePwd = args[4].toCharArray();
        String userId = args[5];
        String password = args[6];

        if (!ROOT_DIR.exists()) {
            ROOT_DIR.mkdirs();
        }

        // criar truststorehandler
        try {
            tsh = new TrustStoreHandler(truststorePath, truststorePwd);
        } catch (Exception e) {
            System.out.println("Erro ao criar TrustStoreHandler");
            return;
        }

        // criar keystoremanager
        try {
            ksm = new KeyStoreManager(keystorePath, keystorePwd, userId);
        } catch (Exception e) {
            System.out.println("Erro ao criar KeyStoreManager");
            return;
        }

        String[] serverAddressParts = args[0].split(":");
        String host = serverAddressParts[0];
        int port = serverAddressParts.length > 1 ? Integer.parseInt(serverAddressParts[1]) : 22345;

        // criar SSLfactory
        SSLSocketFactory ssf;
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(
                    null, // unilateral auth
                    tsh.getTrustManagers(),
                    new SecureRandom());

            ssf = sslContext.getSocketFactory();
            socket = (SSLSocket) ssf.createSocket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            scanner = new Scanner(System.in);
        } catch (Exception e) {
            System.out.println("Erro ao criar SSLSocket");
            return;
        }

        // ler jar
        File clientFile;
        try {
            clientFile = new File(
                    SpertaClient.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        } catch (Exception e) {
            System.out.println("Erro ao obter clientfile");
            onClose();
            return;
        }

        // attestacao
        if (!attestate(clientFile)) {
            onClose();
            return;
        }

        // autenticacao
        if (!authenticate(userId, password)) {
            onClose();
            return;
        }

        // loop
        mainLoop();

        onClose();
    }

    // previne resource leaks
    private static void onClose() {
        try {
            in.close();
            out.close();
        } catch (IOException e) {
            System.out.println("Erro a fechar streams");
        }

        try {
            socket.close();
        } catch (IOException e) {
            System.out.println("Erro a fechar socket");
        }

        scanner.close();

        System.exit(0); // previne um erro no too many attempts
    }

    private static void mainLoop() {
        try {
            printMenu();

            while (true) {
                System.out.print("Comando: ");
                String command = scanner.nextLine().trim();

                if (command.isEmpty()) {
                    continue;
                }

                String cmdUpper = command.toUpperCase();
                String[] cmdSplit = command.split("\\s+");

                if (cmdUpper.startsWith("CREATE")) {
                    handleCreate(cmdSplit);
                } else if (cmdUpper.startsWith("ADD")) {
                    handleAdd(cmdSplit);
                } else if (cmdUpper.startsWith("EC")) {
                    handleEC(cmdSplit);
                } else if (cmdUpper.startsWith("RT")) {
                    handleRT(cmdSplit);
                } else if (cmdUpper.startsWith("RH")) {
                    handleRH(cmdSplit);
                } else {
                    out.writeObject(command);
                    System.out.println((String) in.readObject());
                }
            }

        } catch (NoSuchElementException e) {
            System.out.println("A terminar...");
        } catch (Exception e) {
            System.err.println("Erro na conexao: " + e.getMessage());
        }
    }

    private static void handleRH(String[] parts) throws Exception {
        String hm = parts[1];
        String d = parts[2];

        out.writeObject("RH " + hm + " " + d);
        out.flush();

        String response = (String) in.readObject();
        if (!"OK".equals(response)) {
            System.out.println(response);
            return;
        }

        // one section key for RH (device belongs to exactly one section)
        byte[] wrappedKey = (byte[]) in.readObject();
        SecretKey sectionKey = AESUtils.unwrapKey(wrappedKey, ksm.getPrivateKey());

        byte[] encryptedData = (byte[]) in.readObject();
        byte[] fileData = AESUtils.decrypt(encryptedData, sectionKey);

        String filename = hm + "_" + d.toUpperCase() + "_" + LocalDateTime.now().format(fmt) + ".csv";
        saveLocalFile(filename, fileData);
        System.out.println("OK");
    }

    private static void handleRT(String[] parts) throws Exception {
        String hm = parts[1];

        out.writeObject("RT " + hm);
        out.flush();

        String response = (String) in.readObject();
        if (!"OK".equals(response)) {
            System.out.println(response);
            return;
        }

        // RT may span multiple sections — server sends count, then one (wrappedKey,
        // encryptedData) per section
        int sectionCount = (int) in.readObject();
        ByteArrayOutputStream result = new ByteArrayOutputStream();

        for (int i = 0; i < sectionCount; i++) {
            byte[] wrappedKey = (byte[]) in.readObject();
            SecretKey sectionKey = AESUtils.unwrapKey(wrappedKey, ksm.getPrivateKey());

            byte[] encryptedData = (byte[]) in.readObject();
            byte[] decryptedData = AESUtils.decrypt(encryptedData, sectionKey);
            result.write(decryptedData);
        }

        String filename = hm + "_estados.txt";
        saveLocalFile(filename, result.toByteArray());
        System.out.println("OK");
    }

    private static void handleEC(String[] parts) throws Exception {
        String hm = parts[1];
        String d = parts[2];
        String value = parts[3];

        // step a
        out.writeObject("EC " + hm + " " + d + " " + value);
        out.flush();

        // step b
        String response = (String) in.readObject();
        if (!"OK".equals(response)) {
            System.out.println(response);
            return;
        }

        byte[] wrappedKey = (byte[]) in.readObject();

        // step c
        SecretKey sectionKey = AESUtils.unwrapKey(wrappedKey, ksm.getPrivateKey());
        byte[] encryptedValue = AESUtils.encrypt(value.getBytes(), sectionKey);

        out.writeObject(encryptedValue);
        out.flush();

        System.out.println((String) in.readObject());
    }

    private static void handleAdd(String[] parts) throws Exception {
        String user = parts[1];
        String hm = parts[2];
        String section = parts[3];

        if (!tsh.hasCertificate(user)) {
            out.writeObject("GET-CERT " + user);
            out.flush();

            Object certResponse = in.readObject();
            if (certResponse instanceof String) {
                System.out.println(certResponse);
                return;
            }

            Certificate userCert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream((byte[]) certResponse));
            tsh.storeCertificate(user, userCert);
        }

        out.writeObject("ADD " + user + " " + hm + " " + section);
        out.flush();

        String response = (String) in.readObject();
        if (!"OK".equals(response)) {
            System.out.println(response);
            return;
        }

        byte[] wrappedForOwner = (byte[]) in.readObject();
        SecretKey sectionKey = AESUtils.unwrapKey(wrappedForOwner, ksm.getPrivateKey());
        byte[] wrappedForUser = AESUtils.wrapKey(sectionKey, tsh.getPublicKey(user));

        out.writeObject(wrappedForUser);
        out.flush();

        System.out.println((String) in.readObject());
    }

    private static void handleCreate(String[] parts) throws Exception {
        String hm = parts[1];

        out.writeObject("CREATE " + hm);
        out.flush();

        String response = (String) in.readObject();
        if (!"OK".equals(response)) {
            System.out.println(response);
            return;
        }

        PublicKey ownerPubKey = ksm.getCertificate().getPublicKey();

        for (@SuppressWarnings("unused")
        Section s : Section.values()) {
            SecretKey sectionKey = AESUtils.generateSectionKey();
            byte[] wrappedKey = AESUtils.wrapKey(sectionKey, ownerPubKey);
            out.writeObject(wrappedKey);
            out.flush();
        }
    }

    private static void saveLocalFile(String filename, byte[] data) {
        try (FileOutputStream fos = new FileOutputStream(new File(ROOT_DIR, filename))) {
            fos.write(data);
            System.out.println("Ficheiro guardado em: " + ROOT_DIR.getName() + "/" + filename);
        } catch (IOException e) {
            System.out.println("Erro ao gravar ficheiro local: " + e.getMessage());
        }
    }

    private static boolean attestate(File f) {
        try {
            long nonce = in.readLong();

            byte[] hashed = HashUtils.hashFileWithNonce(f, nonce);

            out.writeObject(hashed);
            out.flush();

            String attestationResponse = (String) in.readObject();
            if ("ATTESTATION_OK".equals(attestationResponse)) {
                System.out.println("ATTESTATION OK");
            } else {
                System.out.println("ATTESTATION FAILED");
                //System.out.println(attestationResponse);
                return false;
            }
        } catch (IOException e) {
            System.out.println("Erro atestacao IO"); // TODO nok?
            return false;
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Erro atestacao algorithm");
            return false;
        } catch (Exception e) {
            System.out.println("Erro atestacao desconhecido");
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private static boolean authenticate(String userId, String password) {
        boolean authenticated = false;
        String currentPassword = password;

        while (!authenticated) {
            String authResponse;
            try {
                out.writeObject(userId + " " + currentPassword);
                authResponse = (String) in.readObject();
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Erro autenticacao");
                e.printStackTrace();
                break; // TODO talvez n seja break aqui?
            }

            System.out.println(authResponse);

            if (authResponse.equals("SEND-CERT")) {
                sendCertificate();
                try {
                    authResponse = (String) in.readObject();
                    System.out.println(authResponse);
                } catch (ClassNotFoundException e) {
                    System.out.println("Erro mandar cert class");
                    e.printStackTrace();
                } catch (IOException e) {
                    System.out.println("Erro mandar cert io");
                    e.printStackTrace();
                }
            }

            if (authResponse.equals("OK-USER") || authResponse.equals("OK-NEW-USER")) {
                return true;
            } else if (authResponse.equals("TOO-MANY-ATTEMPTS")) {
                System.out.println("Demasiadas tentativas falhadas de conexao. A terminar...");
                break;
            } else if (authResponse.equals("WRONG-PWD")) {
                System.out.print("Password incorreta. Introduza nova password: ");
                currentPassword = scanner.nextLine().trim();
            } else if (authResponse.equals("ALREADY-LOGGED")) {
                System.out.println("Utilizador já logado noutro cliente. A terminar...");
                break;
            } else {
                System.out.println("Resposta de autenticacao desconhecida. A terminar...");
                break;
            }
        }

        return false;
    }

    private static void sendCertificate() {
        try {
            byte[] certBytes = ksm.getCertificate().getEncoded();
            out.writeObject(certBytes);
            out.flush();
        } catch (Exception e) {
            System.out.println("Erro ao enviar certificado");
        }
    }

    private static void printMenu() {
        System.out.println("\n--- Comandos Disponíveis ---");
        System.out.println("CREATE <hm>            # Criar casa <hm>");
        System.out.println("ADD <user1> <hm> <s>   # Adicionar utilizador <user1> à casa <hm>, secao <s>");
        System.out.println("RD <hm> <s>            # Registar um Dispositivo");
        System.out.println("EC <hm> <d> <int>      # Enviar valor <int> de estado/temporizacao");
        System.out.println("RT <hm>                # Receber informacao sobre último comando");
        System.out.println("RH <hm> <d>            # Receber o Histórico (ficheiro de log.csv)");
        System.out.println("Pressione CTRL+C para sair.\n");
    }
}