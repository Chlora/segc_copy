package com.sperta.server;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.sperta.common.Enums.Section;

public class SectionKeyUtils {

    private static final String KEYS_PATH = "./ficheiros/sectionKeys";

    public static void saveKeyFile(byte[] wrappedKey, String casaId, Section section, String userId)
            throws IOException {
        Path dir = Paths.get(KEYS_PATH);
        Files.createDirectories(dir);

        String filename = "key." + casaId + "." + section.name() + "." + userId;
        Path keyPath = dir.resolve(filename);
        Files.write(keyPath, wrappedKey);
    }

    public static byte[] getKeyFile(String casaId, Section section, String userId)
            throws IOException {
        String filename = "key." + casaId + "." + section.name() + "." + userId;
        Path keyPath = Paths.get(KEYS_PATH, filename);

        if (!Files.exists(keyPath)) {
            throw new FileNotFoundException("Section key not found: " + filename);
        }

        return Files.readAllBytes(keyPath);
    }
}
