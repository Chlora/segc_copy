package com.sperta.server;

import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;

public class CatalogoCasas {

    private Map<String, Casa> tabela;
    private static final File ROOT_DIR = new File("ficheiros/casas");

    private String cipherPassword;
    private byte[] serverSalt;

    public CatalogoCasas(CatalogoUsers c, String cipherPassword, byte[] serverSalt) {
        this.tabela = new HashMap<>();
        this.cipherPassword = cipherPassword;
        this.serverSalt = serverSalt;
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
        saveCasa(c, cipherPassword);
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

        // 1. load info.enc
        try {
            byte[] decInfo = SpertaServer.verifyAndDecrypt(infoFile, cipherPassword, serverSalt);
            String infoStr = new String(decInfo);
            
            String[] lines = infoStr.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                
                String[] parts = line.split(":");
                if (parts.length != 2) continue;

                String key = parts[0].trim();
                String value = parts[1].trim();

                if (key.equals("owner")) {
                    User owner = catalogoUsers.getWithNome(value);
                    if (owner == null) return null;
                    casa = new Casa(casaDir.getName(), owner);
                } else {
                    if (casa == null) return null;
                    User user = catalogoUsers.getWithNome(key);
                    if (user == null) continue;

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
        } catch (Exception e) {
            System.err.println("Erro ao decifrar/verificar info da casa " + casaDir.getName() + ": " + e.getMessage());
            System.out.println("NOK-INTEGRITY");
            System.exit(1);
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
                File counterFile = new File(seccaoDir, "counter.enc");
                if (!counterFile.exists())
                    continue;

                int counter = 0;
                try {
                    byte[] decCounter = SpertaServer.verifyAndDecrypt(counterFile, cipherPassword, serverSalt);
                    String counterStr = new String(decCounter).trim();
                    counter = Integer.parseInt(counterStr);
                } catch (Exception e) {
                    System.err.println("Erro ao decifrar counter da seccao: " + e.getMessage());
                    System.out.println("NOK-INTEGRITY");
                    System.exit(1);
                }

                // 4. aparelho by index
                for (int i = 1; i <= counter; i++) {
                    File deviceFile = new File(seccaoDir, p.name() + i + ".csv.enc");
                    casa.addAparelho(p, "0", deviceFile);
                }

            } catch (IllegalArgumentException e) {
                
            }
        }

        return casa;
    }


    public synchronized void saveCasa(Casa c, String cipherPassword) {
        File casaDir = new File(ROOT_DIR, c.id);
        if (!casaDir.exists()) {
            casaDir.mkdirs();
        }
            
        File infoFile = new File(casaDir, "info.enc");
        StringBuilder infoData = new StringBuilder();
        infoData.append("owner:").append(c.getOwner()).append("\n");

        for (Map.Entry<User, EnumSet<Permissao>> entry : c.getPermissoes().entrySet()) {
            if (entry.getValue().contains(Permissao.owner))
                continue;

            infoData.append(entry.getKey().nome).append(":");
            for (Permissao p : entry.getValue()) {
                infoData.append(p.name()).append(",");
            }
            infoData.setLength(infoData.length() - 1);
            infoData.append("\n");
        }
        
        try {
            SpertaServer.encryptAndSign(infoFile, infoData.toString().getBytes(), cipherPassword, serverSalt);
        } catch (Exception e) {
            System.err.println("Erro ao cifrar info da casa: " + e.getMessage());
        }

        for (Map.Entry<Permissao, Seccao> entry : c.getSeccoes().entrySet()) {
            File seccaoDir = new File(casaDir, entry.getKey().name());
            if (!seccaoDir.exists())
                seccaoDir.mkdirs();

            File counterFile = new File(seccaoDir, "counter.enc");
            try {
                String counterStr = String.valueOf(entry.getValue().getAparelhoCount());
                SpertaServer.encryptAndSign(counterFile, counterStr.getBytes(), cipherPassword, serverSalt);
            } catch (Exception e) {
                System.err.println("Erro ao cifrar counter da seccao: " + e.getMessage());
            }
        }
    }
    
    public byte[] getWrappedKey(String hm, Permissao p, String username, String pwd) {
        File keyFile = new File("ficheiros/casas/" + hm + "/" + p.name() + "/key." + hm + "." + p.name() + "." + username);
        if (!keyFile.exists()) return null;
        try { 
            return Files.readAllBytes(keyFile.toPath()); 
        } catch(Exception e) { 
            return null; 
        }
    }

    public void saveWrappedKey(String hm, Permissao p, String username, byte[] keyData, String pwd) {
        File keyFile = new File("ficheiros/casas/" + hm + "/" + p.name() + "/key." + hm + "." + p.name() + "." + username);
        keyFile.getParentFile().mkdirs();
        try { 
            Files.write(keyFile.toPath(), keyData); 
        } catch(Exception e) {}
    }
}