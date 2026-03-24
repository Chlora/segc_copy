package src.server;

import java.util.Arrays;
import java.util.HashMap;
import java.io.*;

public class Seccao {

    public final Permissao id;
    private Aparelho[] aparelhos;
    private int counter = 0; //TODO mudar a logica basicamente toda de acesso ao array

    public Seccao(Permissao id) {
        this.id = id;
        this.aparelhos = new Aparelho[0];
    }

    public Seccao(Permissao id, Aparelho[] aparelhos) {
        this.id = id;
        this.aparelhos = aparelhos.clone();
    }

    private void insertInArray(Aparelho ap) {
        Aparelho[] ap2 = Arrays.copyOf(this.aparelhos, this.aparelhos.length + 1);
        ap2[ap2.length - 1] = ap;
        this.aparelhos = ap2;
    }

    public void addAparelho(int estado, File f) {
        Aparelho ap = new Aparelho(id, "" + id.name() + (1 + this.aparelhos.length), estado, f);
        insertInArray(ap);

        counter++;
    }

    public void addAparelho(File f) {
        Aparelho ap = new Aparelho(id, "" + id.name() + (1 + this.aparelhos.length), 0, f);
        insertInArray(ap);

        counter++;
    }

    //TODO
    public boolean removeAparelho(int id) {
        return false;
    }

    public int getEstado(int id) {
        if (id < 1 || id > this.aparelhos.length) {
            return -1;
        }
        return this.aparelhos[id - 1].getEstado();
    }

    public boolean changeEstado(int id, int newEstado, String casaID) {
        if (id < 1 || id > this.aparelhos.length) {
            return false;
        }

        return this.aparelhos[id - 1].changeEstado(newEstado, casaID);
    }

    public int getAparelhoCount() {
        return aparelhos.length;
    }
}
