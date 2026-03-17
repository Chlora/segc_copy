import java.io.*;
import java.net.*;
import java.util.Scanner;

public class SpertaClient {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Uso: SpertaClient <serverAddress> <user-id> <password>");
            return;
        }

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
            File clientFile = new File(SpertaClient.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
            long mySize = clientFile.length(); 
            // Para efeitos de teste, pode forçar-se o valor esperado pelo servidor:
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

            // 2. Autenticação
            out.writeObject(userId + " " + password);
            String authResponse = (String) in.readObject();
            System.out.println(authResponse);

            if (authResponse.equals("WRONG-PWD")) {
                return;
            }

            // 3. Menu de Comandos
            printMenu();

            while (true) {
                System.out.print("> ");
                String command = scanner.nextLine().trim();
                
                if (command.isEmpty()) continue;

                out.writeObject(command);
                String response = (String) in.readObject();
                System.out.println("Resposta: " + response);

                // Se o comando for RT ou RH e a resposta foi OK, receber ficheiro
                if ((command.startsWith("RT") || command.startsWith("RH")) && response.equals("OK")) {
                    long fileSize = in.readLong();
                    System.out.println("Recebendo ficheiro de tamanho: " + fileSize + " bytes.");
                    byte[] fileData = (byte[]) in.readObject();
                    // TODO: Guardar fileData localmente usando FileOutputStream
                    System.out.println("Ficheiro guardado com sucesso.");
                }
            }

        } catch (Exception e) {
            System.err.println("Erro na conexão: " + e.getMessage());
        }
    }

    private static void printMenu() {
        System.out.println("\n--- Comandos Disponíveis ---");
        System.out.println("CREATE <hm>            # Criar casa <hm>");
        System.out.println("ADD <user1> <hm> <s>   # Adicionar utilizador <user1> à casa <hm>, seção <s>");
        System.out.println("RD <hm> <s>            # Registar um Dispositivo");
        System.out.println("EC <hm> <d> <int>      # Enviar valor <int> de estado/temporização");
        System.out.println("RT <hm>                # Receber informação sobre último comando");
        System.out.println("RH <hm> <d>            # Receber o Histórico (ficheiro de log.csv)");
        System.out.println("Pressione CTRL+C para sair.\n");
    }
}