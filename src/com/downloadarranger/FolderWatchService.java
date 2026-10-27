package com.downloadarranger;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FolderWatchService {
    public interface Listener {
        void onFolderStateChanged(String folderPath);
        void onFileProcessed(String folderPath, String originalName, String newName);
    }

    private final Map<String, Boolean> pausedStates = new ConcurrentHashMap<>();
    private final Map<String, WatchKey> registeredKeys = new ConcurrentHashMap<>();
    private final Map<WatchKey, Path> keyToPath = new ConcurrentHashMap<>();
    private final ExecutorService fileProcessorPool = Executors.newCachedThreadPool();
    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean running = false;
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public synchronized void start() {
        if (running) return;
        try {
            watchService = FileSystems.getDefault().newWatchService();
            running = true;
            watchThread = new Thread(this::runWatchLoop, "WatchService-Thread");
            watchThread.setDaemon(true);
            watchThread.start();
        } catch (IOException e) {
            System.err.println("Could not create WatchService: " + e.getMessage());
        }
    }

    public synchronized void registerFolder(String folderPathStr) {
        Path path = Path.of(folderPathStr);
        pausedStates.putIfAbsent(folderPathStr, false);

        if (!Files.exists(path) || !Files.isDirectory(path)) {
            notifyStateChanged(folderPathStr);
            return;
        }

        if (watchService == null || !running) {
            return;
        }

        try {
            WatchKey key = path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
            registeredKeys.put(folderPathStr, key);
            keyToPath.put(key, path);
        } catch (IOException e) {
            System.err.println("Could not register " + folderPathStr + ": " + e.getMessage());
        }
        notifyStateChanged(folderPathStr);
    }

    public synchronized void unregisterFolder(String folderPathStr) {
        WatchKey key = registeredKeys.remove(folderPathStr);
        if (key != null) {
            key.cancel();
            keyToPath.remove(key);
        }
        pausedStates.remove(folderPathStr);
        notifyStateChanged(folderPathStr);
    }

    public void setPaused(String folderPathStr, boolean paused) {
        pausedStates.put(folderPathStr, paused);
        notifyStateChanged(folderPathStr);
    }

    public boolean isPaused(String folderPathStr) {
        return pausedStates.getOrDefault(folderPathStr, false);
    }

    public boolean isAvailable(String folderPathStr) {
        Path p = Path.of(folderPathStr);
        return Files.exists(p) && Files.isDirectory(p);
    }

    private void runWatchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                break;
            }

            Path parentPath = keyToPath.get(key);
            if (parentPath == null) {
                key.reset();
                continue;
            }

            String parentStr = parentPath.toString();
            boolean paused = isPaused(parentStr);

            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path filename = ev.context();
                Path fullPath = parentPath.resolve(filename);

                if (paused) {
                    continue;
                }

                if (Files.isDirectory(fullPath) || FileOrganizer.isIgnoredExtension(fullPath)) {
                    continue;
                }

                fileProcessorPool.submit(() -> {
                    if (FileOrganizer.waitForFileReady(fullPath)) {
                        String origName = fullPath.getFileName().toString();
                        Path moved = FileOrganizer.organizeSingleFile(fullPath, parentPath);
                        if (moved != null && listener != null) {
                            listener.onFileProcessed(parentStr, origName, moved.getFileName().toString());
                        }
                    }
                });
            }

            boolean valid = key.reset();
            if (!valid) {
                keyToPath.remove(key);
                registeredKeys.remove(parentStr);
                notifyStateChanged(parentStr);
            }
        }
    }

    private void notifyStateChanged(String folderPathStr) {
        if (listener != null) {
            listener.onFolderStateChanged(folderPathStr);
        }
    }

    public synchronized void stop() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {}
        }
        fileProcessorPool.shutdownNow();
    }
}