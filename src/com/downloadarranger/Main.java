package com.downloadarranger;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            FolderWatchService watchService = new FolderWatchService();
            MainUI ui = new MainUI(watchService);
            ui.setVisible(true);
        });
    }
}