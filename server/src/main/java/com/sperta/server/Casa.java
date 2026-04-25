package com.sperta.server;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.io.*;

public class Casa {

    private Map<User, EnumSet<Permissao>> tabelaPermissoes;
    public final String id;
    private Map<Permissao, Seccao> tabelaSeccoes;

    public Casa(String id, User owner) {
        this.id = id;
        tabelaPermissoes = new HashMap<>();
        tabelaSeccoes = new HashMap<>();

        tabelaPermissoes.put(owner, EnumSet.of(Permissao.owner));
    }

    public synchronized void givePerms(User user, Permissao p) {
        if (!this.tabelaPermissoes.containsKey(user)) {
            this.tabelaPermissoes.put(user, EnumSet.of(p));
            return;
        }

        EnumSet<Permissao> perms = this.tabelaPermissoes.get(user);

        if (p == Permissao.all) {
            perms.clear();
            perms.add(Permissao.all);
        } else {
            perms.remove(Permissao.all);
            perms.add(p);
        }
    }

    public synchronized void givePerms(User user, EnumSet<Permissao> p) {
        if (!this.tabelaPermissoes.containsKey(user)) {
            this.tabelaPermissoes.put(user, p);
            return;
        }

        this.tabelaPermissoes.get(user).addAll(p);
    }

    public synchronized boolean addSeccao(Permissao p) {
        if (tabelaSeccoes.containsKey(p)) {
            return false;
        }

        Seccao s = new Seccao(p);
        tabelaSeccoes.put(p, s);

        return true;
    }

    public synchronized boolean addAparelho(Permissao p) {
        if (!tabelaSeccoes.containsKey(p)) {
            addSeccao(p);
        }

        File f = new File("ficheiros/casas/" + this.id + "/" + p.name() + "/" + p.name()
                + (tabelaSeccoes.get(p).getAparelhoCount() + 1) + ".csv");

        tabelaSeccoes.get(p).addAparelho(f);
        return true;
    }

    public synchronized boolean addAparelho(Permissao p, String estado) {
        if (!tabelaSeccoes.containsKey(p)) {
            addSeccao(p);
        }

        File f = new File("ficheiros/casas/" + this.id + "/" + p.name() + "/" + p.name()
                + (tabelaSeccoes.get(p).getAparelhoCount() + 1) + ".csv");

        tabelaSeccoes.get(p).addAparelho(estado, f);
        return true;
    }

    public synchronized boolean addAparelho(Permissao p, String estado, File existingLog) {
        if (!tabelaSeccoes.containsKey(p)) {
            addSeccao(p);
        }
        tabelaSeccoes.get(p).addAparelho(estado, existingLog);
        return true;
    }

    // unused
    /**
     * public synchronized boolean removeAparelho(int id, Permissao p) {
     * if (!tabelaPermissoes.containsKey(p)) {
     * return false;
     * }
     * 
     * return tabelaSeccoes.get(p).removeAparelho(id);
     * }
     */

    public String getOwner() {
        for (Map.Entry<User, EnumSet<Permissao>> entry : tabelaPermissoes.entrySet()) {
            if (entry.getValue().contains(Permissao.owner)) {
                return entry.getKey().nome;
            }
        }

        return "";
    }

    public synchronized String getEstado(String id) {
        if (id.length() < 2) {
            return "";
        }

        try {
            Permissao p = Permissao.valueOf(String.valueOf(id.charAt(0)));

            try {
                int resto = Integer.parseInt(id.substring(1));

                if (!tabelaSeccoes.containsKey(p)) {
                    return "";
                }

                return tabelaSeccoes.get(p).getEstado(resto);

            } catch (NumberFormatException e) {
                return "";
            }
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    public synchronized boolean changeEstado(String id, String newEstado) {
        if (id.length() < 2) {
            return false;
        }

        try {
            Permissao p = Permissao.valueOf(String.valueOf(id.charAt(0)));

            int resto = Integer.parseInt(id.substring(1));

            if (!tabelaSeccoes.containsKey(p)) {
                return false;
            }

            return tabelaSeccoes.get(p).changeEstado(resto, newEstado, this.id);

        } catch (IllegalArgumentException e) {
            return false;
        }

    }

    public synchronized Map<User, EnumSet<Permissao>> getPermissoes() {
        return tabelaPermissoes;
    }

    public synchronized boolean UserTemPermParaSeccao(User u, Permissao p) {
        return tabelaPermissoes.containsKey(u)
                && (tabelaPermissoes.get(u).contains(p) || tabelaPermissoes.get(u).contains(Permissao.all)
                        || tabelaPermissoes.get(u).contains(Permissao.owner));
    }

    public synchronized Map<Permissao, Seccao> getSeccoes() {
        return tabelaSeccoes;
    }

    public synchronized boolean ExisteSeccao(Permissao p) {
        return tabelaSeccoes.containsKey(p);
    }

    public synchronized boolean ExisteAparelho(String id) {
        try {
            Permissao p = Permissao.valueOf(String.valueOf(id.charAt(0)));
            Seccao s = tabelaSeccoes.get(p);

            int resto = Integer.parseInt(id.substring(1));

            return s != null && s.getAparelhoCount() >= resto;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
