package com.sperta.client;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import java.time.LocalDateTime;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class SpertaClient {

    private static final File ROOT_DIR = new File("ficheirosRecebidos/");
    private static final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static PrivateKey myPrivateKey;
    private static PublicKey myPublicKey;
    private static byte[] myCertBytes;

    public static void main(String[] args) {
        if (args.length < 7) {
            System.out.println("Uso: SpertaClient <serverAddress> <truststore> <password-truststore> <keystore> <password-keystore> <user-id> <password>");
            return;
        }

        ensureRootDir();

        String[] serverAddressParts = args[0].split(":");
        String host = serverAddressParts[0];
        int port = serverAddressParts.length > 1 ? Integer.parseInt(serverAddressParts[1]) : 22345;

        String truststorePath = args[1];
        String truststorePwd = args[2];
        String keystorePath = args[3];
        String keystorePwd = args[4];
        String userId = args[5];
        String password = args[6];

        System.setProperty("javax.net.ssl.trustStore", truststorePath);
        System.setProperty("javax.net.ssl.trustStorePassword", truststorePwd);

        try {

            KeyStore ks = KeyStore.getInstance("JCEKS"); 
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                ks.load(fis, keystorePwd.toCharArray());
            }

            myPrivateKey = (PrivateKey) ks.getKey(userId, keystorePwd.toCharArray());
            Certificate myCert = ks.getCertificate(userId);
            myPublicKey = myCert.getPublicKey();
            myCertBytes = myCert.getEncoded();

            if (myPrivateKey == null || myCert == null) {
                System.err.println("Erro: Nao foi possivel encontrar a chave privada/certificado para o alias '" + userId + "' na keystore.");
                return;
            }
        } catch (Exception e) {
            System.err.println("Erro ao carregar a keystore local: " + e.getMessage());
            return;
        }

        SSLSocketFactory ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();

        try (SSLSocket socket = (SSLSocket) ssf.createSocket(host, port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
             Scanner scanner = new Scanner(System.in)) {

            socket.startHandshake();

            long nonce = in.readLong();
            File clientJar = new File("SpertaClient.jar"); // Assume que o executavel esta na mesma diretoria
            if (!clientJar.exists()) {
                System.err.println("Aviso: SpertaClient.jar nao encontrado localmente. A atestacao ira falhar.");
            } else {
                byte[] jarBytes = Files.readAllBytes(clientJar.toPath());
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(ByteBuffer.allocate(8).putLong(nonce).array());
                md.update(jarBytes);
                out.writeObject(md.digest());
                out.flush();
            }

            String attestationResponse = (String) in.readObject();
            if ("ATTESTATION_OK".equals(attestationResponse)) {
                System.out.println("ATTESTATION OK");
            } else {
                System.out.println("ATTESTATION FAILED");
                return;
            }

            boolean authenticated = false;
            String currentPassword = password;

            while (!authenticated) {
                out.writeObject(userId + " " + currentPassword);
                String authResponse = (String) in.readObject();
                System.out.println(authResponse);

                if (authResponse.equals("OK-USER") || authResponse.equals("OK-NEW-USER")) {
                    authenticated = true;
                
                }else if (authResponse.equals("SEND-CERT")) {
                    // Servidor pediu o certificado (Novo utilizador)
                    out.writeObject(myCertBytes);
                    out.flush();
                    String newResp = (String) in.readObject();
                    if ("OK-NEW-USER".equals(newResp)) {
                        authenticated = true;
                        System.out.println("Novo utilizador registado e autenticado com sucesso.");
                    }

                } else if (authResponse.equals("TOO-MANY-ATTEMPTS")) {
                    System.out.println("Demasiadas tentativas falhadas de conexao. A terminar...");
                    return;
                } else if (authResponse.equals("WRONG-PWD")) {
                    System.out.print("Password incorreta. Introduza nova password: ");
                    currentPassword = scanner.nextLine().trim();
                } else if (authResponse.equals("ALREADY-LOGGED")) {
                    System.out.println("Utilizador já logado noutro cliente. A terminar...");
                    return;
                } else {
                    System.out.println("Resposta de autenticacao desconhecida. A terminar...");
                    return;
                }
            }

            // 3. Menu de Comandos
            printMenu();

            while (true) {
                System.out.print("Comando: ");
                String command = scanner.nextLine().trim();

                if (command.isEmpty()) continue;

                String[] parts = command.split("\\s+");
                String cmdUpper = parts[0].toUpperCase();

                try {
                    if (cmdUpper.equals("ADD") && parts.length >= 4) {

                        String targetUser = parts[1];
                        
                        out.writeObject("GETCERT " + targetUser);
                        String certResp = (String) in.readObject();

                        if (!"OK".equals(certResp)) {
                            System.out.println("Erro: Nao foi possivel obter o certificado do utilizador " + targetUser);
                            continue;
                        }

                        byte[] targetCertBytes = (byte[]) in.readObject();
                        CertificateFactory cf = CertificateFactory.getInstance("X.509");
                        Certificate targetCert = cf.generateCertificate(new ByteArrayInputStream(targetCertBytes));
                        PublicKey targetPubKey = targetCert.getPublicKey();

                        out.writeObject(command);
                        String addResp = (String) in.readObject();
                        
                        if ("OK-ADD-HANDSHAKE".equals(addResp)) {

                            byte[] ownerWrappedKey = (byte[]) in.readObject();
                            SecretKey sectionAESKey = unwrapAESKey(ownerWrappedKey, myPrivateKey);
                            
                            byte[] newWrappedKey = wrapAESKey(sectionAESKey, targetPubKey);
                            out.writeObject(newWrappedKey);
                            out.flush();
                            
                            System.out.println("Resposta: " + in.readObject());
                        } else {
                            System.out.println("Resposta: " + addResp);
                        }

                    } else if (cmdUpper.equals("RD") && parts.length >= 3) {

                        out.writeObject(command);
                        String rdResp = (String) in.readObject();
                        
                        if ("SEND-KEY".equals(rdResp)) {
                        
                            SecretKey newAESKey = generateAESKey();
                            byte[] wrappedKey = wrapAESKey(newAESKey, myPublicKey);
                            out.writeObject(wrappedKey);
                            out.flush();
                            System.out.println("Resposta: OK (Nova seccao e chave geradas)");

                        } else {
                            System.out.println("Resposta: " + rdResp);
                        }

                    } else if (cmdUpper.equals("EC") && parts.length >= 4) {

                        String hm = parts[1];
                        String d = parts[2];
                        String estadoValue = parts[3];
                        
                        out.writeObject("EC " + hm + " " + d);
                        String ecResp = (String) in.readObject();
                        
                        if ("OK-KEY".equals(ecResp)) {
                            byte[] wrappedKey = (byte[]) in.readObject();
                            SecretKey sectionAESKey = unwrapAESKey(wrappedKey, myPrivateKey);
                            
                            // Cifrar o <int> em Base64
                            String estadoCifrado = encryptState(estadoValue, sectionAESKey);
                            out.writeObject(estadoCifrado);
                            out.flush();
                            
                            System.out.println("Resposta: " + in.readObject());

                        } else {
                            System.out.println("Resposta: " + ecResp);
                        }

                    } else if (cmdUpper.equals("RT") && parts.length >= 2) {

                        out.writeObject(command);
                        String rtResp = (String) in.readObject();
                        
                        if ("OK-DATA".equals(rtResp)) {
                            Map<String, SecretKey> keysMap = new HashMap<>();
                            
                            while (true) {
                                String sectionName = (String) in.readObject();
                                if ("END-KEYS".equals(sectionName)) break;
                                byte[] wrappedKey = (byte[]) in.readObject();
                                keysMap.put(sectionName, unwrapAESKey(wrappedKey, myPrivateKey));
                            }
                            
                            String encryptedData = (String) in.readObject();
                            if ("NODATA".equals(encryptedData)) {
                                System.out.println("Nenhum dado encontrado.");
                            } else {
                                System.out.println("\n--- Últimos Estados ---");
                                String[] lines = encryptedData.split("\n");
                                StringBuilder decryptedOutput = new StringBuilder();
                                
                                for (String line : lines) {
                                    if (line.trim().isEmpty()) continue;
                                    String[] lineParts = line.split(":");
                                    String device = lineParts[0];
                                    String encState = lineParts[1];
                                    String sectionPrefix = String.valueOf(device.charAt(0));
                                    
                                    SecretKey secKey = keysMap.get(sectionPrefix);
                                    if (secKey != null) {
                                        String clearState = decryptState(encState, secKey);
                                        decryptedOutput.append(device).append(" : ").append(clearState).append("\n");
                                    }
                                }
                                System.out.print(decryptedOutput.toString());
                                saveLocalFile(parts[1] + "_estados.txt", decryptedOutput.toString().getBytes());
                            }
                        } else {
                            System.out.println("Resposta: " + rtResp);
                        }

                    } else if (cmdUpper.equals("RH") && parts.length >= 3) {

                        out.writeObject(command);
                        String rhResp = (String) in.readObject();
                        
                        if ("OK".equals(rhResp)) {
                            byte[] wrappedKey = (byte[]) in.readObject();
                            SecretKey sectionAESKey = unwrapAESKey(wrappedKey, myPrivateKey);
                            
                            long fileSize = in.readLong();
                            byte[] fileData = (byte[]) in.readObject();
                            
                            // Decifrar o log
                            String logContent = new String(fileData);
                            String[] lines = logContent.split("\n");
                            StringBuilder decryptedLog = new StringBuilder();
                            
                            for (String line : lines) {
                                if (line.trim().isEmpty()) continue;
                                String[] lineParts = line.split(" : ");
                                if (lineParts.length == 2) {
                                    String timestamp = lineParts[0];
                                    String clearState = decryptState(lineParts[1], sectionAESKey);
                                    decryptedLog.append(timestamp).append(" : ").append(clearState).append("\n");
                                }
                            }
                            
                            System.out.println("Resposta: OK. Log recebido e decifrado.");
                            String filename = parts[1] + "_" + parts[2].toUpperCase() + "_" + LocalDateTime.now().format(fmt) + ".csv";
                            saveLocalFile(filename, decryptedLog.toString().getBytes());
                        } else {
                            System.out.println("Resposta: " + rhResp);
                        }

                    } else {

                        out.writeObject(command);
                        System.out.println("Resposta: " + in.readObject());
                    }
                } catch (Exception ex) {
                    System.err.println("Erro na operacao criptografica do cliente: " + ex.getMessage());
                }
            }

        } catch (NoSuchElementException e) {
            System.out.println("A terminar..."); 
        } catch (Exception e) {
            System.err.println("Erro na conexao: " + e.getMessage());
        }

        System.exit(0); 
    }

    private static SecretKey generateAESKey() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(128);
        return kg.generateKey();
    }

    private static byte[] wrapAESKey(SecretKey keyToWrap, PublicKey pubKey) throws Exception {
        Cipher c = Cipher.getInstance("RSA");
        c.init(Cipher.WRAP_MODE, pubKey);
        return c.wrap(keyToWrap);
    }

    private static SecretKey unwrapAESKey(byte[] wrappedKey, PrivateKey privKey) throws Exception {
        Cipher c = Cipher.getInstance("RSA");
        c.init(Cipher.UNWRAP_MODE, privKey);
        return (SecretKey) c.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY);
    }

    private static String encryptState(String plainState, SecretKey aesKey) throws Exception {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, aesKey);
        byte[] iv = c.getIV();
        byte[] encrypted = c.doFinal(plainState.getBytes());
        
        ByteBuffer bb = ByteBuffer.allocate(iv.length + encrypted.length);
        bb.put(iv).put(encrypted);
        return Base64.getEncoder().encodeToString(bb.array());
    }

    private static String decryptState(String base64State, SecretKey aesKey) throws Exception {
        byte[] raw = Base64.getDecoder().decode(base64State);
        byte[] iv = Arrays.copyOfRange(raw, 0, 16);
        byte[] encrypted = Arrays.copyOfRange(raw, 16, raw.length);
        
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(iv));
        return new String(c.doFinal(encrypted));
    }

    private static void saveLocalFile(String filename, byte[] data) {
        try (FileOutputStream fos = new FileOutputStream(new File(ROOT_DIR, filename))) {
            fos.write(data);
            System.out.println("Ficheiro guardado em: " + ROOT_DIR.getName() + "/" + filename);
        } catch (IOException e) {
            System.out.println("Erro ao gravar ficheiro local: " + e.getMessage());
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

    private static void ensureRootDir() {
        if (!ROOT_DIR.exists()) {
            ROOT_DIR.mkdirs();
        }
    }
}