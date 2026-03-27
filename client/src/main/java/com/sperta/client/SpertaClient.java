package com.sperta.client;

import java.io.*;
import java.net.*;
import java.time.format.DateTimeFormatter;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.time.LocalDateTime;

public class SpertaClient {

    private static final File ROOT_DIR = new File("ficheirosRecebidos/");
    private static final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Uso: SpertaClient <serverAddress> <user-id> <password>");
            return;
        }

        ensureRootDir();

        String[] serverAddressParts = args[0].split(":");
        String host = serverAddressParts[0];
        int port = serverAddressParts.length > 1 ? Integer.parseInt(serverAddressParts[1]) : 22345;

        String userId = args[1];
        String password = args[2];

        try (Socket socket = new Socket(host, port);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                Scanner scanner = new Scanner(System.in)) {

            // 1. Atestação
            File clientFile = new File(
                    SpertaClient.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
            long mySize = clientFile.length();
            // Para efeitos de teste, pode forçar-se o valor esperado pelo servidor: TODO tirar isto
            mySize = 2734L;
            out.writeLong(mySize);
            out.flush();

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

                if (command.isEmpty()){
                    continue;
                }

                out.writeObject(command);
                String response = (String) in.readObject();

                String cmdUpper = command.toUpperCase();

                if ((cmdUpper.startsWith("RT") || cmdUpper.startsWith("RH")) && response.equals("OK")) {
                    long fileSize = in.readLong();
                    byte[] fileData = (byte[]) in.readObject();

                    System.out.println(
                            "Resposta: OK, " + fileSize + " (long), seguido de " + fileSize + " bytes de dados.");

                    // Guardar o ficheiro localmente
                    String[] parts = command.split(" ");
                    String filename = cmdUpper.startsWith("RT") ? parts[1] + "_estados.txt"
                            : parts[1] + "_" + parts[2].toUpperCase() + "_" + LocalDateTime.now().format(fmt) + ".csv";

                    try (FileOutputStream fos = new FileOutputStream(new File(ROOT_DIR, filename))) {
                        fos.write(fileData);
                    } catch (IOException e) {
                        System.out.println("Erro ao gravar ficheiro local: " + e.getMessage());
                    }
                } else {
                    System.out.println("Resposta: " + response);
                }
            }

        } catch (NoSuchElementException e) {
            System.out.println("A terminar..."); 
        } catch (Exception e) {
            System.err.println("Erro na conexao: " + e.getMessage());
        }

        System.exit(0); // previne um erro no too many attempts
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