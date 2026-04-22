package com.sperta.server;

import com.sperta.server.*;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Aparelho {

    public final Permissao tipo;
    public final String nome;
    private final File logFile;
    private String estadoCifrado;
    private String ultimoEstado = "";

    private static final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Aparelho(Permissao tipo, String nome, String estadoCifrado, File f) {
        this.tipo = tipo;
        this.nome = nome;
        this.estadoCifrado = estadoCifrado;
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

    public boolean changeEstado(String newEstado, String casaID) {
        log(this.estadoCifrado, newEstado, casaID);
        this.estadoCifrado = newEstado;
        ultimoEstado = newEstado;
        return true;
    }

    public String getEstado() {
        return this.estadoCifrado;
    }

    private void log(String estado, String newEstado, String casaID) {
        LogGlobalAparelhos.write(casaID, nome, newEstado);
        if (logFile == null) {
            return;
        }

        String logsAntigos = "";
        if (logFile.exists()) {
            try {
                byte[] dec = SpertaServer.verifyAndDecrypt(logFile, SpertaServer.getCipherPassword(),
                        SpertaServer.getServerSalt());
                logsAntigos = new String(dec);
            } catch (Exception e) {
                
            }
        }

        String novoLog = LocalDateTime.now().format(fmt) + " : " + newEstado + "\n";
        String logsAtualizados = logsAntigos + novoLog;

        try {
            SpertaServer.encryptAndSign(logFile, logsAtualizados.getBytes(), SpertaServer.getCipherPassword(),
                    SpertaServer.getServerSalt());
        } catch (Exception e) {
            System.err.println("Erro ao escrever log seguro: " + e.getMessage());
        }
    }

    public String GetUltimoEstado() {
        return this.ultimoEstado;
    }
}
