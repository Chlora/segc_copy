import java.io.*;
import java.net.*;
import java.util.*;

public class SpertaServer {
    private static final int DEFAULT_PORT = 22345;
    
    // Estruturas de dados em memória (Deve ser sincronizado com ficheiros locais)
    private static Map<String, String> users = new HashMap<>(); 
    // Exemplo para atestação
    private static final long EXPECTED_CLIENT_SIZE = 2734L; 

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        System.out.println("SpertaServer a iniciar no porto " + port + "...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Novo cliente conectado: " + clientSocket.getInetAddress());
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        }
    }

    // Thread para tratar cada cliente concorrentemente
    private static class ClientHandler extends Thread {
        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String loggedUser;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                // 1. Atestação
                long clientSize = in.readLong();
                if (clientSize == EXPECTED_CLIENT_SIZE) {
                    out.writeObject("ATTESTATION_OK");
                } else {
                    out.writeObject("ATTESTATION_FAILED");
                    socket.close();
                    return;
                }

                // 2. Autenticação
                String authRequest = (String) in.readObject();
                String[] authParts = authRequest.split(" ");
                String user = authParts[0];
                String pwd = authParts[1];

                if (!users.containsKey(user)) {
                    users.put(user, pwd); // Regista novo utilizador
                    out.writeObject("OK-NEW-USER");
                    loggedUser = user;
                } else if (users.get(user).equals(pwd)) {
                    out.writeObject("OK-USER");
                    loggedUser = user;
                } else {
                    out.writeObject("WRONG-PWD");
                    socket.close();
                    return;
                }

                // 3. Ciclo de Comandos
                String command;
                while ((command = (String) in.readObject()) != null) {
                    String response = processCommand(command);
                    out.writeObject(response);
                    
                    // Tratamento especial para comandos que enviam ficheiros (RT e RH)
                    if (command.startsWith("RT ") && response.startsWith("OK")) {
                        sendFileData("dados_rt.txt"); // TODO: Implementar leitura do ficheiro real
                    } else if (command.startsWith("RH ") && response.startsWith("OK")) {
                        sendFileData("log.csv"); // TODO: Implementar leitura do log real
                    }
                }

            } catch (EOFException e) {
                System.out.println("Cliente " + loggedUser + " desconectou-se.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private String processCommand(String cmdLine) {
            String[] tokens = cmdLine.split(" ");
            String cmd = tokens[0].toUpperCase();

            // TODO: Adicionar validações de ficheiros e lógica de negócio real
            switch (cmd) {
                case "CREATE":
                    if (tokens.length == 2) return "OK"; // Criar casa
                    break;
                case "ADD":
                    if (tokens.length == 4) return "OK"; // Adicionar user
                    break;
                case "RD":
                    if (tokens.length == 3) return "OK"; // Registar Dispositivo
                    break;
                case "EC":
                    if (tokens.length == 4) return "OK"; // Enviar Comando
                    break;
                case "RT":
                    if (tokens.length == 2) return "OK"; // Devolver OK e depois ficheiro
                    break;
                case "RH":
                    if (tokens.length == 3) return "OK"; // Devolver OK e depois ficheiro
                    break;
            }
            return "NOK";
        }

        private void sendFileData(String filename) throws IOException {
            // TODO: Ler o ficheiro real usando FileInputStream e enviar o tamanho (long) seguido dos bytes
            long dummySize = 100L;
            out.writeLong(dummySize);
            out.writeObject("CONTEUDO_DUMMY_DO_FICHEIRO".getBytes());
            out.flush();
        }
    }
}