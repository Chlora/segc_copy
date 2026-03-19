package src.server;

import java.io.*;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;

public class CatalogoCasas {

    private Map<String, Casa> tabela;
    private static final File ROOT_DIR = new File("ficheiros/casas");

    public CatalogoCasas(CatalogoUsers c) {
        tabela = new HashMap<>();
        loadAll(c);
    }

    public Casa getWithId(String id) {
        return tabela.get(id);
    }

    public Collection<Casa> getAll() {
        return tabela.values();
    }

    public boolean addCasa(String id, User owner) {
        if (tabela.containsKey(id)) {
            return false;
        }
        Casa c = new Casa(id, owner);
        tabela.put(id, c);
        return true;
    }

    public boolean exists(String id) {
        return tabela.containsKey(id);
    }

    private void loadAll(CatalogoUsers catalogoUsers) {
        File[] casaDirs = ROOT_DIR.listFiles(File::isDirectory);
        if (casaDirs == null)
            return;

        for (File casaDir : casaDirs) {
            Casa c = loadCasa(casaDir, catalogoUsers);
            if (c != null)
                tabela.put(c.id, c);
        }
    }

    private Casa loadCasa(File casaDir, CatalogoUsers catalogoUsers) {
        File infoFile = new File(casaDir, "info.txt");
        if (!infoFile.exists())
            return null;

        Casa casa = null;

        // 1. load info.txt
        try (BufferedReader br = new BufferedReader(new FileReader(infoFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length != 2)
                    continue;

                String key = parts[0].trim();
                String value = parts[1].trim();

                if (key.equals("owner")) {
                    User owner = catalogoUsers.getWithNome(value);
                    if (owner == null)
                        return null;
                    casa = new Casa(casaDir.getName(), owner);
                } else {
                    if (casa == null)
                        return null;
                    User user = catalogoUsers.getWithNome(key);
                    if (user == null)
                        continue;

                    EnumSet<Permissao> perms = EnumSet.noneOf(Permissao.class);
                    for (String p : value.split(",")) {
                        try {
                            perms.add(Permissao.valueOf(p.trim()));
                        } catch (IllegalArgumentException e) {
                            System.err.println("Permissao invalida: " + p);
                        }
                    }
                    casa.addUser(perms, user);
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao dar load a casa " + casaDir.getName() + ": " + e.getMessage());
            return null;
        }

        if (casa == null)
            return null;

        // 2. subfolders
        File[] seccaoDirs = casaDir.listFiles(File::isDirectory);
        if (seccaoDirs == null)
            return casa;

        for (File seccaoDir : seccaoDirs) {
            try {
                Permissao p = Permissao.valueOf(seccaoDir.getName());
                casa.addSeccao(p);

                // 3. aparelhos
                File[] deviceFiles = seccaoDir.listFiles(
                        f -> f.isFile() && f.getName().endsWith(".txt"));
                if (deviceFiles == null)
                    continue;

                Arrays.sort(deviceFiles, Comparator.comparing(File::getName));

                for (File deviceFile : deviceFiles) {
                    int estado = readLastEstado(deviceFile);
                    Permissao p2 = Permissao.valueOf(seccaoDir.getName());
                    casa.addAparelho(p2, estado, deviceFile);
                }

            } catch (IllegalArgumentException e) {
                System.err.println("Seccao invalida: " + seccaoDir.getName());
            }
        }

        return casa;
    }

    private int readLastEstado(File logFile) {
        int estado = 0;
        if (!logFile.exists())
            return estado;
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String line, last = null;
            while ((line = br.readLine()) != null)
                last = line;
            if (last != null) {
                // format: "2026-03-19 14:32:01 : Estado mudado de 0 para 45"
                String[] parts = last.split(" para ");
                if (parts.length == 2)
                    estado = Integer.parseInt(parts[1].trim());
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Erro a ler ultimo estado: " + e.getMessage());
        }
        return estado;
    }

    public void saveCasa(Casa c) {
        File casaDir = new File(ROOT_DIR, c.id);
        if (!casaDir.exists())
            casaDir.mkdirs();

        // info.txt
        File infoFile = new File(casaDir, "info.txt");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(infoFile))) {
            bw.write("owner:" + c.getOwner());
            bw.newLine();

            for (Map.Entry<User, EnumSet<Permissao>> entry : c.getPermissoes().entrySet()) {
                if (entry.getValue().contains(Permissao.owner))
                    continue;

                StringBuilder sb = new StringBuilder();
                sb.append(entry.getKey().nome).append(":");
                for (Permissao p : entry.getValue()) {
                    sb.append(p.name()).append(",");
                }
                sb.setLength(sb.length() - 1); // remove trailing comma
                bw.write(sb.toString());
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Erro ao gravar info da casa: " + e.getMessage());
        }
    }
}