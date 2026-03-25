//TODO O servidor mantém um ficheiro de texto com o nome da aplicação e o respetivo tamanho (ex., SpertaClient:2734)

import java.io.*;
import java.net.*;
import java.util.Map;

import src.server.Casa;
import src.server.CatalogoCasas;
import src.server.CatalogoUsers;
import src.server.Permissao;
import src.server.Seccao;
import src.server.User;

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
    private static void create(User u, String hm, ObjectOutputStream out) throws IOException {
        if (catalogoCasas.addCasa(hm, u)) {
            out.writeObject("OK");
            return;
        }
        out.writeObject("NOK");
    }

    // da permissao s ao username na casa hm
    private static void add(User u, String username, String hm, String s, ObjectOutputStream out) throws IOException {
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
            out.writeObject("OK");
        } catch (IllegalArgumentException e) {
            out.writeObject("NOK");
        }
    }

    // regista aparelho na casa hm seccao s
    private static void rd(User u, String hm, String s, ObjectOutputStream out) throws IOException {
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
            out.writeObject("OK");
        } catch (IllegalArgumentException e) {
            out.writeObject("NOK");
        }
    }

    // mete o dispositivo d com estado v na casa hm
    private static void ec(User u, String hm, String d, int v, ObjectOutputStream out) throws IOException {
        Casa c = catalogoCasas.getWithId(hm);

        if (c == null) {
            out.writeObject("NOHM");
            return;
        }

        if (!c.ExisteAparelho(d)) {
            out.writeObject("NOD");
            return;
        }

        try {
            Permissao p = Permissao.valueOf(String.valueOf(d.charAt(0)));

            if (!c.UserTemPermParaSeccao(u, p)) {
                out.writeObject("NOPERM");
                return;
            }

            if (!c.changeEstado(d, v)) {
                out.writeObject("NOK");
                return;
            }

            catalogoCasas.saveCasa(c);
            out.writeObject("OK");
        } catch (IllegalArgumentException e) {
            out.writeObject("NOK");
        }
    }

    // envia ao cliente um .txt contendo todos os ultimos comandos dos aparelhos da
    // casa hm
    // TODO
    private static void rt(User u, String hm, ObjectOutputStream out) throws IOException {
        Casa c = catalogoCasas.getWithId(hm);

        if (c == null) {
            out.writeObject("NOHM");
            return;
        }

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<Permissao, Seccao> entry : c.getSeccoes().entrySet()) {
            if (!c.UserTemPermParaSeccao(u, entry.getKey())) {
                continue;
            }

            Seccao s = entry.getValue();
            for (int i = 1; i <= s.getAparelhoCount(); i++) {
                String deviceId = entry.getKey().name() + i;
                sb.append(deviceId).append(":").append(s.GetUltimoEstado(i)).append("\n");
            }
        }

        if (sb.length() == 0) {
            out.writeObject("NOPERM"); //TODO ver se é NODATA
            return;
        }

        byte[] data = sb.toString().getBytes();
        out.writeObject("OK");
        out.writeLong(data.length);
        out.write(data);
    }

    // da ao cliente o log do dispositivo d da casa hm
    // TODO
    private static void rh(User u, String hm, String d, ObjectOutputStream out) throws IOException {
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

        File logFile = new File("ficheiros/casas/" + hm + "/" + d.charAt(0) + "/" + d + ".txt");

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
        out.write(data);
    }

    // processa a string de comando, faz validacoes, e chama um dos metodos acima
    // TODO
    private static void proccessCommand(String comando, User u, ObjectOutputStream out) throws IOException {
        String[] tokens = comando.trim().split("\\s+");

        switch (tokens[0].toUpperCase()) {
            case "CREATE":
                if (tokens.length < 2) {
                    out.writeObject("NOK");
                    return;
                }
                create(u, tokens[1], out);
                break;
            case "ADD":
                if (tokens.length < 4) {
                    out.writeObject("NOK");
                    return;
                }
                add(u, tokens[1], tokens[2], tokens[3], out);
                break;
            case "RD":
                if (tokens.length < 3) {
                    out.writeObject("NOK");
                    return;
                }
                rd(u, tokens[1], tokens[2], out);
                break;
            case "EC":
                if (tokens.length < 4) {
                    out.writeObject("NOK");
                    return;
                }
                try {
                    ec(u, tokens[1], tokens[2], Integer.parseInt(tokens[3]), out);
                } catch (NumberFormatException e) {
                    out.writeObject("NOK");
                }
                break;
            case "RT":
                if (tokens.length < 2) {
                    out.writeObject("NOK");
                    return;
                }
                rt(u, tokens[1], out);
                break;
            case "RH":
                if (tokens.length < 3) {
                    out.writeObject("NOK");
                    return;
                }
                rh(u, tokens[1], tokens[2], out);
                break;
            default:
                out.writeObject("NOK");
                break;
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private User loggedUser;

        private static final int max_attempts = 3;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        // processa a string
        // ve se o utilizador existe. se sim, ve se a pass tem match
        // senao cria novo registo
        // retorna user e escreve no out
        private static synchronized User authenticate(String composta, ObjectOutputStream out) throws IOException {
            String[] tokens = composta.trim().split("\\s+");

            if (tokens.length < 2) {
                return null;
            }

            if (catalogoUsers.exists(tokens[0])) {
                if (catalogoUsers.authenticate(tokens[0], tokens[1])) {
                    out.writeObject("OK-USER");
                    return catalogoUsers.getWithNome(tokens[0]);
                }
                out.writeObject("WRONG-PWD");
                return null;
            }

            catalogoUsers.addUser(tokens[0], tokens[1]);
            out.writeObject("OK-NEW-USER");
            return catalogoUsers.getWithNome(tokens[0]);
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
                    User result = authenticate(auth, out);

                    if (result != null) {
                        loggedUser = result;
                        break;
                    }
                }

                // disconnect se excedeu max attempts
                if (loggedUser == null) {
                    out.writeObject("TOO-MANY-ATTEMPTS");
                    socket.close();
                    return;
                }

                // command loop
                String command;
                while ((command = (String) in.readObject()) != null) {
                    proccessCommand(command, loggedUser, out);
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
            }
        }
    }
}