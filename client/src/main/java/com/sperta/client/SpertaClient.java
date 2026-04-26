package com.sperta.client;

//TODO ver a seccao 2 do pdf e ver se estou a dar encrypt com rsa 2048 + 4.2 (le o pdf todo dnv)
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDateTime;

import javax.crypto.SecretKey;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.sperta.common.keystore.KeyStoreManager;
import com.sperta.common.Enums.Section;
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
            // e.printStackTrace();
        }
    }

    private static void handleRH(String[] parts) throws Exception {
        String hm = parts[1];
        String d = parts[2];

        out.writeObject("RH " + hm + " " + d);
        out.flush();

        String response = (String) in.readObject();
        if (!response.equals("OK")) {
            System.out.println(response);
            return;
        }

        int size = (int) in.readLong();
        System.out.println(size);
        byte[] data = in.readNBytes(size);
        ObjectInputStream payload = new ObjectInputStream(new ByteArrayInputStream(data));

        byte[] wrappedKey = (byte[]) payload.readObject();
        byte[] fileBytes = (byte[]) payload.readObject();

        SecretKey sectionKey = AESUtils.unwrapKey(wrappedKey, ksm.getPrivateKey());

        // read file
        String csv = new String(fileBytes, StandardCharsets.UTF_8);
        StringBuilder result = new StringBuilder();
        Pattern pattern = Pattern.compile("[0-9a-f]{64,}");

        for (String line : csv.split("\n")) {
            Matcher matcher = pattern.matcher(line);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                byte[] encBytes = HexFormat.of().parseHex(matcher.group());
                byte[] plain = AESUtils.decrypt(encBytes, sectionKey);
                int stateValue = ByteBuffer.wrap(plain).getInt();
                matcher.appendReplacement(sb, String.valueOf(stateValue));
            }
            matcher.appendTail(sb);
            result.append(sb).append("\n");
        }

        String filename = hm + "_" + d.toUpperCase() + "_" + LocalDateTime.now().format(fmt) + ".csv";
        saveLocalFile(filename, result.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("OK");
    }

    private static void handleRT(String[] parts) throws Exception {
        String hm = parts[1];

        out.writeObject("RT " + hm);
        out.flush();

        String response = (String) in.readObject();
        if (response.equals("NODATA")) {
            System.out.println(response);
            return;
        }

        int size = (int) in.readLong();
        System.out.println(size);
        byte[] data = in.readNBytes(size);
        // byte[] data = (byte[]) in.readObject();
        ObjectInputStream payload = new ObjectInputStream(new ByteArrayInputStream(data));

        ByteArrayOutputStream result = new ByteArrayOutputStream();

        int elegiveis = (int) payload.readObject();

        for (int i = 0; i < elegiveis; i++) {
            int aparelhoCount = (Integer) payload.readObject();
            byte[] sectionKey = (byte[]) payload.readObject();

            SecretKey secretKey = AESUtils.unwrapKey(sectionKey, ksm.getPrivateKey());

            for (int j = 0; j < aparelhoCount; j++) {
                String nome = (String) payload.readObject();
                byte[] cypheredInt = (byte[]) payload.readObject();

                byte[] decryptedData = AESUtils.decrypt(cypheredInt, secretKey);
                result.write((nome + ": ").getBytes());
                int value = ByteBuffer.wrap(decryptedData).getInt();
                result.write(String.valueOf(value).getBytes());
                result.write(("\n").getBytes());
            }
        }

        String filename = hm + "_estados.txt";
        System.out.println("OK, " + size + ", seguido de " + data.length + " bytes de dados.");
        saveLocalFile(filename, result.toByteArray());
    }

    private static void handleEC(String[] parts) throws Exception {
        String hm = parts[1];
        String d = parts[2];
        int value = Integer.parseInt(parts[3]);

        // presumo que este check tenha de ser aqui agora, jaq o server nao e suposto
        // desencriptar o valor (?)
        if (value < 0 || value > 600) {
            System.out.println("Estado tem de estar entre 0 e 600");
            return;
        }

        // step a
        out.writeObject("EC " + hm + " " + d);
        out.flush();

        // step b
        String response = (String) in.readObject();
        if (!"OK".equals(response)) {
            System.out.println(response);
            return;
        }

        byte[] wrappedKey = (byte[]) in.readObject();
        out.flush();

        // step c
        SecretKey sectionKey = AESUtils.unwrapKey(wrappedKey, ksm.getPrivateKey());
        byte[] bytes = ByteBuffer.allocate(4).putInt(value).array();
        byte[] encryptedValue = AESUtils.encrypt(bytes, sectionKey);

        out.writeObject(encryptedValue);
        out.flush();

        System.out.println((String) in.readObject());
    }

    private static void handleAdd(String[] parts) throws Exception {
        String user = parts[1];
        String hm = parts[2];
        String section = parts[3];

        // obter cert
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

        // obter skey
        byte[] rewrapped = null;
        try {
            rewrapped = handleSectionKey(parts[1], parts[2], parts[3]);
            if (rewrapped == null) {
                System.out.println("Erro a tratar section key");
                return;
            }
        } catch (Exception e) {
            System.out.println("Erro a tratar section key. Os argumentos estão corretos?");
            return;
        }

        out.writeObject("ADD " + user + " " + hm + " " + section);
        out.flush();

        out.writeObject(rewrapped);
        out.flush();

        System.out.println((String) in.readObject());

    }

    private static byte[] handleSectionKey(String userId, String hm, String seccao) throws Exception {
        // pedir section key
        out.writeObject("GET-SKEY " + hm + " " + seccao);

        String response = (String) in.readObject();

        if (!"OK".equals(response)) {
            System.out.println(response);
            return null;
        }

        byte[] skey = (byte[]) in.readObject();

        // decifrar
        PrivateKey pkey = ksm.getPrivateKey();
        SecretKey sectionKey = AESUtils.unwrapKey(skey, pkey);

        // cifrar
        byte[] rewrapped = AESUtils.wrapKey(sectionKey, tsh.getPublicKey(userId));

        return rewrapped;
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

        response = (String) in.readObject();
        System.out.println(response);
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
                // System.out.println(attestationResponse);
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

            if (authResponse.equals("OK-USER") || authResponse.equals("OK-NEW-USER")) {
                try {
                    authResponse = (String) in.readObject();
                    System.out.println(authResponse);
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                if (authResponse.equals("SEND-CERT")) {
                    System.out.println("A enviar certificado...");
                    sendCertificate();

                    try {
                        authResponse = (String) in.readObject();
                        System.out.println(authResponse);

                    } catch (ClassNotFoundException e) {
                        System.out.println("Erro mandar cert class");
                        e.printStackTrace();
                        return false;
                    } catch (IOException e) {
                        System.out.println("Erro mandar cert io");
                        e.printStackTrace();
                        return false;
                    }

                }
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