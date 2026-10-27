package com.downloadarranger;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileOrganizer {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern LEADING_DIGITS = Pattern.compile("^(\\d+)");
    private static final Map<String, Object> FOLDER_LOCKS = new ConcurrentHashMap<>();

    public static boolean isIgnoredExtension(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".crdownload") || name.endsWith(".part") || name.endsWith(".tmp");
    }

    private static Object getFolderLock(String folderPath) {
        return FOLDER_LOCKS.computeIfAbsent(folderPath.toLowerCase(), k -> new Object());
    }

    public static String getTodayString() {
        return LocalDate.now().format(DATE_FORMATTER);
    }

    public static Path getTodayTargetDirectory(Path watchFolderPath) {
        return watchFolderPath.resolve(getTodayString());
    }

    /**
     * Waits for the file size to be stable for 2 seconds and not locked.
     * Retries every 2 seconds up to 30 seconds.
     */
    public static boolean waitForFileReady(Path filePath) {
        long timeoutMillis = 30_000;
        long pollInterval = 2_000;
        long startTime = System.currentTimeMillis();
        long previousSize = -1;

        while (System.currentTimeMillis() - startTime <= timeoutMillis) {
            if (!Files.exists(filePath)) {
                return false;
            }

            try {
                long currentSize = Files.size(filePath);
                if (currentSize == previousSize && currentSize >= 0) {
                    if (isNotLocked(filePath)) {
                        return true;
                    }
                }
                previousSize = currentSize;
            } catch (IOException ignored) {}

            try {
                Thread.sleep(pollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static boolean isNotLocked(Path filePath) {
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "rw")) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Determines next sequence number: checks existing files in target folder and history log.
     */
    public static int getNextSequenceNumber(Path todayDir, String watchFolderPath) {
        int maxInFolder = 0;
        if (Files.exists(todayDir) && Files.isDirectory(todayDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(todayDir)) {
                for (Path entry : stream) {
                    if (!Files.isDirectory(entry)) {
                        Matcher matcher = LEADING_DIGITS.matcher(entry.getFileName().toString());
                        if (matcher.find()) {
                            try {
                                int num = Integer.parseInt(matcher.group(1));
                                if (num > maxInFolder) {
                                    maxInFolder = num;
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error reading today dir: " + e.getMessage());
            }
        }

        int maxInHistory = HistoryLogger.getMaxSequenceLoggedToday(watchFolderPath, getTodayString());
        int highest = Math.max(maxInFolder, maxInHistory);
        return highest + 1;
    }

    public static String formatSequenceNumber(int sequence) {
        return sequence < 100 ? String.format("%02d", sequence) : String.valueOf(sequence);
    }

    private static String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex >= 0) ? fileName.substring(dotIndex) : "";
    }

    /**
     * Renames and moves a single file safely. Thread-safe per watch folder.
     */
    public static Path organizeSingleFile(Path sourceFile, Path watchFolderPath) {
        if (!Files.exists(sourceFile) || Files.isDirectory(sourceFile) || isIgnoredExtension(sourceFile)) {
            return null;
        }

        synchronized (getFolderLock(watchFolderPath.toString())) {
            try {
                Path todayDir = getTodayTargetDirectory(watchFolderPath);
                if (!Files.exists(todayDir)) {
                    Files.createDirectories(todayDir);
                }

                int nextSeq = getNextSequenceNumber(todayDir, watchFolderPath.toString());
                String extension = getExtension(sourceFile.getFileName().toString());
                String newFileName = formatSequenceNumber(nextSeq) + extension;
                Path destinationPath = todayDir.resolve(newFileName);

                while (Files.exists(destinationPath)) {
                    nextSeq++;
                    newFileName = formatSequenceNumber(nextSeq) + extension;
                    destinationPath = todayDir.resolve(newFileName);
                }

                Files.move(sourceFile, destinationPath, StandardCopyOption.ATOMIC_MOVE);

                HistoryLogger.append(
                        watchFolderPath.toString(),
                        sourceFile.getFileName().toString(),
                        newFileName,
                        destinationPath.toAbsolutePath().toString()
                );

                return destinationPath;
            } catch (IOException e) {
                System.err.println("Failed to move file " + sourceFile + ": " + e.getMessage());
                return null;
            }
        }
    }

    /**
     * Organizes loose files sitting directly in the watch folder, oldest first.
     */
    public static int organizeLooseFilesInFolder(Path watchFolderPath) {
        if (!Files.exists(watchFolderPath) || !Files.isDirectory(watchFolderPath)) {
            return 0;
        }

        List<Path> looseFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(watchFolderPath)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p) && !isIgnoredExtension(p)) {
                    looseFiles.add(p);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading folder: " + e.getMessage());
            return 0;
        }

        looseFiles.sort(Comparator.comparingLong(p -> {
            try {
                return Files.getLastModifiedTime(p).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }));

        int count = 0;
        for (Path file : looseFiles) {
            if (waitForFileReady(file)) {
                Path moved = organizeSingleFile(file, watchFolderPath);
                if (moved != null) {
                    count++;
                }
            }
        }
        return count;
    }
}