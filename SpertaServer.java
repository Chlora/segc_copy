//TODO O servidor mantém um ficheiro de texto com o nome da aplicação e o respetivo tamanho (ex., SpertaClient:2734)

import java.io.*;
import java.net.*;
import java.util.*;

import src.server.Casa;
import src.server.CatalogoCasas;
import src.server.CatalogoUsers;
import src.server.Permissao;
import src.server.User;
import java.util.EnumSet;

public class SpertaServer {
    private static final int DEFAULT_PORT = 22345;
    private static final long EXPECTED_CLIENT_SIZE = 2734L;

    private static CatalogoUsers catalogoUsers;
    private static CatalogoCasas catalogoCasas;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Porto invalido, a usar " + DEFAULT_PORT);
            }
        }

        catalogoUsers = new CatalogoUsers();
        catalogoCasas = new CatalogoCasas(catalogoUsers);

        System.out.println("SpertaServer a iniciar no porto " + port + "...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Novo cliente: " + clientSocket.getInetAddress());
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        }
    }

    // cria casa
    // TODO
    private static void create(String hm) {

    }

    // da permissao s ao username na casa hm
    // TODO
    private static void add(String username, String hm, String string) {

    }

    // regista aparelho na casa hm seccao s
    private static void rd(String hm, String s) {

    }

    // mete o dispositivo d com estado v na casa hm
    // TODO
    private static void ec(String hm, String d, int v) {

    }

    // envia ao cliente um .txt contendo todos os ultimos comandos dos aparelhos da
    // casa hm
    // TODO
    private static void rt(String hm) {

    }

    // da ao cliente o log do dispositivo d da casa hm
    // TODO
    private static void rh(String hm, String d) {

    }

    // processa a string de comando, faz validacoes, e chama um dos metodos acima
    // TODO
    private static void proccessCommand(String comando) {
    }

    // processa a string
    // ve se o utilizador existe. se sim, ve se a pass tem match
    // senao cria novo registo
    // escreve username em loggedUser
    private static boolean authenticate(String composta) {
        return false;
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String loggedUser;

        int max_attempts = 3;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            // TODO

            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());


                // atestacao
                long clientSize = in.readLong();
                if (clientSize == EXPECTED_CLIENT_SIZE) {
                    out.writeObject("ATTESTATION_OK");
                } else {
                    out.writeObject("ATTESTATION_FAILED");
                    socket.close();
                    return;
                }

                // auth loop
                for (int i = 0; i < max_attempts; i++) {
                    String auth = (String) in.readObject();
                    boolean result = authenticate(auth);

                    if (result) {
                        break;
                    }
                }

                // disconnect se excedeu max attempts
                if(loggedUser == null) {
                    socket.close();
                }

                // command loop
                //TODO
                

            } catch (EOFException e) {
                //TODO cliente desconectou bem
            } 
            catch (Exception e) {
                // TODO
            }
            finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    // TODO: handle exception
                }
            }
        }
    }
}