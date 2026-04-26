package com.sperta.server;

import java.io.*;

import javax.net.ssl.SSLSocket;
import javax.crypto.SecretKey;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

import com.sperta.common.crypto.HashUtils;
import com.sperta.common.keystore.KeyStoreManager;

public class SpertaServer {
    private static final int DEFAULT_PORT = 22345;

    private static CatalogoUsers catalogoUsers;
    private static CatalogoCasas catalogoCasas;

    private static KeyStoreManager ksm;

    private static SSLContext sslContext;

    private static final File ROOT_FILES = new File("./ficheiros/");;

    private static final String SALT_PATH = "./ficheiros/server.salt";
    private static byte[] salt;

    private static final String ATTEST_PATH = "./ficheiros/attest.enc";

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Uso: SpertaServer <port> <password-cifra> <keystore> <password-keystore>");
            return;
        }

        if (!ROOT_FILES.exists()) {
            ROOT_FILES.mkdirs();
        }

        // google disse que 1025 era o minimo para o windows
        int port = Integer.parseInt(args[0]);
        if (1025 > port || port > 65535) {
            port = DEFAULT_PORT;
            System.out.println("Port inválida. Prosseguindo utilizando " + DEFAULT_PORT + ".");
        }

        String cypherPassword = args[1];
        String keystorePath = args[2];
        String keystorePassword = args[3];

        if (!verifyArgs(cypherPassword, keystorePath, keystorePassword)) {
            System.out.println("A terminar...");
            return;
        }

        salt = FileUtils.loadOrCreateSalt(SALT_PATH);
        if (salt == null) {
            return;
        }

        FileUtils.setSalt(salt);

        SecretKey k;
        try {
            k = HashUtils.derivePBEKey(cypherPassword, salt);
        } catch (Exception e) {
            System.out.println("Erro ao derivar password");
            e.printStackTrace();
            return;
        }

        catalogoUsers = new CatalogoUsers();
        catalogoCasas = new CatalogoCasas(catalogoUsers, k);

        // criar SSLfactory
        try {
            ksm = new KeyStoreManager(keystorePath, keystorePassword.toCharArray());
        } catch (Exception e) {
            System.out.println("Erro ao inicializar KeyStoreManager (" + keystorePath + ").");
            e.printStackTrace();
            return;
        }

        try {
            sslContext = ksm.createSSLContext();
        } catch (Exception e) {
            System.out.println("Erro ao inicializar SSLContext");
            e.printStackTrace();
            return;
        }

        ClientHandler.setCatalogos(catalogoUsers, catalogoCasas);
        ClientHandler.setAttestParams(ATTEST_PATH, cypherPassword);

        SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
        try {
            SSLServerSocket socket = (SSLServerSocket) ssf.createServerSocket(port);
            System.out.println("SpertaServer a iniciar no porto " + port + "...");
            while (true) {
                SSLSocket clientSocket = (SSLSocket) socket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.out.println("Erro ao criar socket servidor");
            e.printStackTrace();
            return;
        }

    }


    private static boolean verifyArgs(String cypherPassword, String keystorePath, String keystorePassword) {
        if (cypherPassword == null) {
            System.out.println("Password de cifra vazia");
            return false;
        }

        if (keystorePath == null) {
            System.out.println("Path para keystore vazia");
            return false;
        }

        if (keystorePassword == null) {
            System.out.println("Password de keystore vazia");
            return false;
        }
        return true;
    }

}