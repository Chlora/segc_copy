package com.sperta.server;

import com.sperta.server.*;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class LogGlobalAparelhos {

    private static final File f = new File("ficheiros/logs/global.enc");
    private static final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 2026-03-19 18:22:47 : Casa1 M1 -> Estado mudado de 0 para 45
public static synchronized void write(String casaId, String nome, String ultimoComando) {
        
        List<String> lines = new ArrayList<>();
    
        if (f.exists()) {
            try {
                byte[] dec = SpertaServer.verifyAndDecrypt(f, SpertaServer.getCipherPassword(), SpertaServer.getServerSalt());
                String logsAntigos = new String(dec);
                for (String line : logsAntigos.split("\n")) {
                    if (!line.trim().isEmpty()) {
                        lines.add(line);
                    }
                }
            } catch (Exception e) {
                System.out.println("NOK-INTEGRITY");
                System.exit(1);
            }
        }

        String timestamp = LocalDateTime.now().format(fmt);
        String newLine = timestamp + " : " + casaId + " " + nome + " -> " + ultimoComando;
        String prefixoProcura = casaId + " " + nome + " ->";

        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(prefixoProcura)) {
                lines.set(i, newLine);
                found = true;
                break;
            }
        }
        if (!found) {
            lines.add(newLine);
        }

        lines.sort((a, b) -> {
            String[] partsA = extractCasaNome(a);
            String[] partsB = extractCasaNome(b);
            int casaCmp = partsA[0].compareTo(partsB[0]);
            if (casaCmp != 0) return casaCmp;
            return partsA[1].compareTo(partsB[1]);
        });

        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append("\n");
        }

        try {
            f.getParentFile().mkdirs();
            SpertaServer.encryptAndSign(f, sb.toString().getBytes(), SpertaServer.getCipherPassword(), SpertaServer.getServerSalt());
        } catch (Exception e) {
            System.err.println("Erro ao cifrar/gravar log global: " + e.getMessage());
        }
    }

    private static String[] extractCasaNome(String line) {
        try {
            // "timestamp : casaId nome -> ultimoComando"
            String afterColon = line.split(" : ", 2)[1]; // "casaId nome -> ultimoComando"
            String beforeArrow = afterColon.split(" -> ")[0]; // "casaId nome"
            String[] parts = beforeArrow.trim().split(" ", 2); // ["casaId", "nome"]
            return parts;
        } catch (Exception e) {
            return new String[] { "", "" };
        }
    }
}