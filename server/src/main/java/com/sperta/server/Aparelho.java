package com.sperta.server;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

public class Aparelho {

    public final Permissao tipo;
    public final String nome;
    private final File logFile;
    private byte[] estado;
    private String ultimoEstado = "";

    private static final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Aparelho(Permissao tipo, String nome, byte[] estado, File f) {
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

    public boolean changeEstado(byte[] newEstado, String casaID) {
        String estadoFormatado = HexFormat.of().formatHex(estado);
        String newEstadoFormatado = HexFormat.of().formatHex(newEstado);

        log(estadoFormatado, newEstadoFormatado, casaID);
        this.estado = newEstado;
        ultimoEstado = "Estado mudado de " + estadoFormatado + " para " + newEstadoFormatado;

        saveEstado();
        return true;
    }

    public byte[] getEstado() {
        return this.estado;
    }

    private void log(String estado, String newEstado, String casaID) {
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

    public void saveEstado() {
        File stateFile = new File(logFile.getParent(), nome + ".bin");
        File ultimoFile = new File(logFile.getParent(), nome + ".ultimo");
        System.out.println(stateFile.getAbsolutePath());
        try {
            java.nio.file.Files.write(stateFile.toPath(), this.estado);
            java.nio.file.Files.writeString(ultimoFile.toPath(), this.ultimoEstado);
            //System.out.println("Wrote " + stateFile.length() + " bytes to " + stateFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Erro ao guardar estado de " + nome + ": " + e.getMessage());
        }

    }

    public String getPathToEstadoFile() {
        return logFile.getParent() + nome + ".bin";
    }

    public void setUltimoEstado(String u) {
        this.ultimoEstado = u;
    }
}
