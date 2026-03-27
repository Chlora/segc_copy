package com.sperta.server;

import com.sperta.server.*;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Aparelho {

    public final Permissao tipo;
    public final String nome;
    private final File logFile;
    private int estado;
    private String ultimoEstado = "";

    private static final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Aparelho(Permissao tipo, String nome, int estado, File f) {
        this.tipo = tipo;
        this.nome = nome;
        this.estado = estado;
        this.logFile = f;

        if (f != null && !f.exists()) {
            f.getParentFile().mkdirs();
            try {
                f.createNewFile();
            } catch (IOException e) {
                System.err.println("Erro ao criar ficheiro de log de " + nome + ": " + e.getMessage());
            }
        }
    }

    public boolean changeEstado(int newEstado, String casaID) {
        if (newEstado < 0 || newEstado > 600) {
            return false;
        }

        log(this.estado, newEstado, casaID);
        this.estado = newEstado;
        ultimoEstado = "Estado mudado de " + estado + " para " + newEstado;

        return true;
    }

    public int getEstado() {
        return this.estado;
    }

    private void log(int estado, int newEstado, String casaID) {
        LogGlobalAparelhos.write(casaID, nome, " : Estado mudado de " + estado + " para " + newEstado);
        if (logFile == null)
            return;
        logFile.getParentFile().mkdirs();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true))) {
            bw.write(LocalDateTime.now().format(fmt) + " : Estado mudado de " + estado + " para " + newEstado);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("Erro ao escrever log de " + nome + ": " + e.getMessage());
        }
    }

    public String GetUltimoEstado() {
        return this.ultimoEstado;
    }
}
