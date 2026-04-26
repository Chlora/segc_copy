package com.sperta.server;

import java.util.Arrays;

import java.io.*;
import java.nio.ByteBuffer;

public class Seccao {

    public final Permissao id;
    private Aparelho[] aparelhos;
    private int counter = 1;

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

    public void addAparelho(byte[] estado, File f) {
        Aparelho ap = new Aparelho(id, "" + id.name() + (1 + this.aparelhos.length), estado, f);
        insertInArray(ap);

        counter++;
    }

    public void addAparelho(File f) {
        byte[] bytes = ByteBuffer.allocate(4).putInt(0).array();
        Aparelho ap = new Aparelho(id, "" + id.name() + (1 + this.aparelhos.length), bytes, f);
        insertInArray(ap);

        counter++;
    }

    public void addAparelho(byte[] estado, String ultimoEstado, File f) {
        Aparelho ap = new Aparelho(id, "" + id.name() + (1 + this.aparelhos.length), estado, f);
        ap.setUltimoEstado(ultimoEstado);
        insertInArray(ap);

        counter++;
    }

    //unused
    /**
    public boolean removeAparelho(int id) {
        return false;
    }
    */

    public byte[] getEstado(int id) {
        if (id < 1 || id > this.aparelhos.length) {
            return null;
        }
        return this.aparelhos[id - 1].getEstado();
    }

    public String GetUltimoEstado(int id) {
        if (id < 1 || id > this.aparelhos.length) {
            return "";
        }
        return this.aparelhos[id - 1].GetUltimoEstado();
    }

    public boolean changeEstado(int id, byte[] newEstado, String casaID) {
        if (id < 1 || id > this.aparelhos.length) {
            return false;
        }

        return this.aparelhos[id - 1].changeEstado(newEstado, casaID);
    }

    public int getAparelhoCount() {
        return this.counter - 1;
    }

    public Aparelho[] getAparelhos() {
        return aparelhos;
    }
}
