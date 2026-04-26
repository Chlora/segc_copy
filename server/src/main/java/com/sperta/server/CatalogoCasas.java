package com.sperta.server;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Key;
import java.util.HashMap;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;

import com.sperta.common.crypto.HashUtils;

public class CatalogoCasas {

    private Map<String, Casa> tabela;
    private static final File ROOT_DIR = new File("ficheiros/casas");
    private final Key pbeKey;

    public CatalogoCasas(CatalogoUsers c, Key pbeKey) {
        this.pbeKey = pbeKey;
        tabela = new HashMap<>();
        loadAll(c);
    }

    public synchronized Casa getWithId(String id) {
        return tabela.get(id);
    }

    public synchronized Collection<Casa> getAll() {
        return tabela.values();
    }

    public synchronized boolean addCasa(String id, User owner) {
        if (tabela.containsKey(id)) {
            return false;
        }
        Casa c = new Casa(id, owner);
        tabela.put(id, c);
        saveCasa(c);
        return true;
    }

    public synchronized boolean exists(String id) {
        return tabela.containsKey(id);
    }

    private synchronized void loadAll(CatalogoUsers catalogoUsers) {
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

        // 1. load info.txt — decrypt, verify, then parse
        byte[] infoPlaintext = HashUtils.decryptAndVerify(infoFile, pbeKey);

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(infoPlaintext), StandardCharsets.UTF_8))) {

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
                    casa.givePerms(user, perms);
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

                // 3. read counter
                File counterFile = new File(seccaoDir, "counter.txt");
                if (!counterFile.exists())
                    continue;

                int counter = 0;
                try {
                    byte[] counterPlaintext = HashUtils.decryptAndVerify(counterFile, pbeKey);
                    String counterLine = new String(counterPlaintext, StandardCharsets.UTF_8).trim();
                    counter = Integer.parseInt(counterLine);
                } catch (NumberFormatException e) {
                    System.err.println("Erro ao ler counter da seccao: " + e.getMessage());
                    continue;
                }

                // 4. aparelho by index
                for (int i = 1; i <= counter; i++) {
                    File deviceFile = new File(seccaoDir, p.name() + i + ".csv");
                    File stateFile = new File(seccaoDir, p.name() + i + ".bin");

                    byte[] estado;
                    if (stateFile.exists()) {
                        try {
                            estado = Files.readAllBytes(stateFile.toPath());
                        } catch (IOException e) {
                            System.err.println("Erro ao ler estado de " + p.name() + i + ": " + e.getMessage());
                            estado = ByteBuffer.allocate(4).putInt(0).array();
                        }
                    } else {
                        estado = ByteBuffer.allocate(4).putInt(0).array();
                    }

                    File ultimoFile = new File(seccaoDir, p.name() + i + ".ultimo");
                    String ultimoEstado = "";
                    if (ultimoFile.exists()) {
                        try {
                            ultimoEstado = Files.readString(ultimoFile.toPath()).trim();
                        } catch (IOException e) {
                            System.err.println("Erro ao ler ultimoEstado de " + p.name() + i + ": " + e.getMessage());
                        }
                    }

                    casa.addAparelho(p, estado, ultimoEstado, deviceFile);
                }

            } catch (IllegalArgumentException e) {
                System.err.println("Seccao invalida: " + seccaoDir.getName());
            }
        }

        return casa;
    }

    public synchronized void saveCasa(Casa c) {
        File casaDir = new File(ROOT_DIR, c.id);
        if (!casaDir.exists()) {
            casaDir.mkdirs();
        }

        // info.txt
        File infoFile = new File(casaDir, "info.txt");
        StringBuilder infoContent = new StringBuilder();
        infoContent.append("owner:").append(c.getOwner()).append(System.lineSeparator());

        for (Map.Entry<User, EnumSet<Permissao>> entry : c.getPermissoes().entrySet()) {
            if (entry.getValue().contains(Permissao.owner))
                continue;

            StringBuilder sb = new StringBuilder();
            sb.append(entry.getKey().nome).append(":");
            for (Permissao p : entry.getValue()) {
                sb.append(p.name()).append(",");
            }
            sb.setLength(sb.length() - 1);
            infoContent.append(sb).append(System.lineSeparator());
        }

        HashUtils.saveEncryptedWithHash(infoFile, infoContent.toString().getBytes(StandardCharsets.UTF_8), pbeKey);

        // section counters
        for (Map.Entry<Permissao, Seccao> entry : c.getSeccoes().entrySet()) {
            File seccaoDir = new File(casaDir, entry.getKey().name());
            if (!seccaoDir.exists())
                seccaoDir.mkdirs();

            File counterFile = new File(seccaoDir, "counter.txt");
            byte[] counterContent = String.valueOf(entry.getValue().getAparelhoCount())
                    .getBytes(StandardCharsets.UTF_8);

            HashUtils.saveEncryptedWithHash(counterFile, counterContent, pbeKey);
        }
    }

    
}