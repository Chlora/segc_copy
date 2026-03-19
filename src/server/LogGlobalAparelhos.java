package src.server;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class LogGlobalAparelhos {

    private static final File f = new File("ficheiros/logs/global.txt");
    private static final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void init() {
        f.getParentFile().mkdirs();
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException e) {
                System.err.println("Erro ao criar log global: " + e.getMessage());
            }
        }
    }

    // 2026-03-19 18:22:47 : Casa1 M1 -> Estado mudado de 0 para 45
    public static void write(String casaId, String nome, String ultimoComando) {
        init();
        String timestamp = LocalDateTime.now().format(fmt);
        String newLine = timestamp + " : " + casaId + " " + nome + " -> " + ultimoComando;

        // ler todas as linhas, replace se nome existe, senao append
        File temp = new File(f.getParentFile(), "global_tmp.txt");
        boolean found = false;

        try (
                BufferedReader br = new BufferedReader(new FileReader(f));
                BufferedWriter bw = new BufferedWriter(new FileWriter(temp))) {
            String line;
            while ((line = br.readLine()) != null) {
                // match
                if (line.contains(casaId + " " + nome + " ->")) {
                    bw.write(newLine);
                    found = true;
                } else {
                    bw.write(line);
                }
                bw.newLine();
            }
            if (!found) {
                bw.write(newLine);
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Erro ao escrever log global: " + e.getMessage());
            return;
        }

        f.delete();
        temp.renameTo(f);
        sort();
    }

    private static void sort() {
        init();
        List<String> lines = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty())
                    lines.add(line);
            }
        } catch (IOException e) {
            System.err.println("Erro ao ler log global para ordenar: " + e.getMessage());
            return;
        }

        lines.sort((a, b) -> {
            String[] partsA = extractCasaNome(a);
            String[] partsB = extractCasaNome(b);
            int casaCmp = partsA[0].compareTo(partsB[0]);
            if (casaCmp != 0)
                return casaCmp;
            return partsA[1].compareTo(partsB[1]);
        });

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f))) {
            for (String line : lines) {
                bw.write(line);
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Erro ao ordenar log global: " + e.getMessage());
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