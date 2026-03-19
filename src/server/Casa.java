package src.server;

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

    public boolean addUser(EnumSet<Permissao> p, User user) {
        if (this.tabelaPermissoes.containsKey(user)) {
            return false;
        }

        this.tabelaPermissoes.put(user, p);

        return true;
    }

    public boolean updateUser(User user, EnumSet<Permissao> p) {
        if (!this.tabelaPermissoes.containsKey(user)) {
            return false;
        }

        this.tabelaPermissoes.put(user, p);

        return true;
    }

    public boolean addSeccao(Permissao p) {
        if (tabelaSeccoes.containsKey(p)) {
            return false;
        }

        Seccao s = new Seccao(p);
        tabelaSeccoes.put(p, s);

        return true;
    }

    public boolean addAparelho(Permissao p) {
        if (!tabelaSeccoes.containsKey(p)) {
            return false;
        }

        File f = new File("ficheiros/casas/" + this.id + "/" + p.name() + "/" + p.name()
                + (tabelaSeccoes.get(p).getAparelhoCount() + 1) + ".txt");

        tabelaSeccoes.get(p).addAparelho(f);
        return true;
    }

    public boolean addAparelho(Permissao p, int estado) {
        if (!tabelaSeccoes.containsKey(p)) {
            return false;
        }

        File f = new File("ficheiros/casas/" + this.id + "/" + p.name() + "/" + p.name()
                + (tabelaSeccoes.get(p).getAparelhoCount() + 1) + ".txt");

        tabelaSeccoes.get(p).addAparelho(estado, f);
        return true;
    }

    public boolean addAparelho(Permissao p, int estado, File existingLog) {
        if (!tabelaSeccoes.containsKey(p))
            return false;
        tabelaSeccoes.get(p).addAparelho(estado, existingLog);
        return true;
    }

    public String getOwner() {
        for (Map.Entry<User, EnumSet<Permissao>> entry : tabelaPermissoes.entrySet()) {
            if (entry.getValue().contains(Permissao.owner)) {
                return entry.getKey().nome;
            }
        }

        return "";
    }

    public int getEstado(String id) {
        if (id.length() < 2) {
            return -1;
        }

        try {
            Permissao p = Permissao.valueOf(String.valueOf(id.charAt(0)));

            try {
                int resto = Integer.parseInt(id.substring(1));

                if (!tabelaSeccoes.containsKey(p)) {
                    return -1;
                }

                return tabelaSeccoes.get(p).getEstado(resto);

            } catch (NumberFormatException e) {
                return -1;
            }
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    public boolean changeEstado(String id, int newEstado) {
        if (id.length() < 2) {
            return false;
        }

        try {
            Permissao p = Permissao.valueOf(String.valueOf(id.charAt(0)));

            try {
                int resto = Integer.parseInt(id.substring(1));

                if (!tabelaSeccoes.containsKey(p)) {
                    return false;
                }

                return tabelaSeccoes.get(p).changeEstado(resto, newEstado, this.id);

            } catch (NumberFormatException e) {
                return false;
            }
        } catch (IllegalArgumentException e) {
            return false;
        }

    }

    public Map<User, EnumSet<Permissao>> getPermissoes() {
        return tabelaPermissoes;
    }

    public Map<Permissao, Seccao> getSeccoes() {
        return tabelaSeccoes;
    }
}
