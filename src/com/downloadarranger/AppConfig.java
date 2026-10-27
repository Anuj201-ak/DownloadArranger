package com.downloadarranger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AppConfig {
    private static final Path CONFIG_PATH = Path.of("watch_folders.txt");
    private static final List<String> DEFAULT_FOLDERS = List.of(
            "W:\\1 anuj32737",
            "W:\\1 anuj272727"
    );

    public static synchronized List<String> loadWatchFolders() {
        if (!Files.exists(CONFIG_PATH)) {
            saveWatchFolders(DEFAULT_FOLDERS);
            return new ArrayList<>(DEFAULT_FOLDERS);
        }

        try {
            List<String> lines = Files.readAllLines(CONFIG_PATH, StandardCharsets.UTF_8);
            Set<String> cleanPaths = new LinkedHashSet<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    cleanPaths.add(trimmed);
                }
            }
            if (cleanPaths.isEmpty()) {
                cleanPaths.addAll(DEFAULT_FOLDERS);
                saveWatchFolders(new ArrayList<>(cleanPaths));
            }
            return new ArrayList<>(cleanPaths);
        } catch (IOException e) {
            System.err.println("Failed to read " + CONFIG_PATH + ": " + e.getMessage());
            return new ArrayList<>(DEFAULT_FOLDERS);
        }
    }

    public static synchronized void saveWatchFolders(List<String> folders) {
        try {
            Files.write(CONFIG_PATH, folders, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to save " + CONFIG_PATH + ": " + e.getMessage());
        }
    }
}