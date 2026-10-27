package com.downloadarranger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HistoryLogger {
    private static final Path LOG_PATH = Path.of("history.log");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern LOG_LINE_PATTERN = Pattern.compile("^([^|]+)\\|([^|]+)\\|([^|]+)\\|([^|]+)\\|(\\d+)[^|]*\\|.*$");

    public static synchronized void append(String watchFolder, String originalName, String newName, String newPath) {
        LocalDateTime now = LocalDateTime.now();
        String dateStr = now.format(DATE_FMT);
        String timeStr = now.format(TIME_FMT);

        String entry = String.format("%s | %s | %s | %s | %s | %s%n",
                dateStr, timeStr, watchFolder, originalName, newName, newPath);

        try {
            if (!Files.exists(LOG_PATH)) {
                Files.createFile(LOG_PATH);
            }
            Files.writeString(LOG_PATH, entry, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to write history log: " + e.getMessage());
        }
    }

    /**
     * Reads history.log and returns the highest sequence number recorded today
     * for this specific watch folder.
     */
    public static synchronized int getMaxSequenceLoggedToday(String watchFolder, String todayDateStr) {
        if (!Files.exists(LOG_PATH)) {
            return 0;
        }

        int maxSeq = 0;
        try {
            List<String> lines = Files.readAllLines(LOG_PATH, StandardCharsets.UTF_8);
            for (String line : lines) {
                String[] parts = line.split("\\s*\\|\\s*");
                if (parts.length >= 6) {
                    String logDate = parts[0].trim();
                    String logFolder = parts[2].trim();
                    String logNewName = parts[4].trim();

                    if (logDate.equals(todayDateStr) && logFolder.equalsIgnoreCase(watchFolder)) {
                        Matcher matcher = Pattern.compile("^(\\d+)").matcher(logNewName);
                        if (matcher.find()) {
                            try {
                                int seq = Integer.parseInt(matcher.group(1));
                                if (seq > maxSeq) {
                                    maxSeq = seq;
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to inspect history log: " + e.getMessage());
        }
        return maxSeq;
    }
}