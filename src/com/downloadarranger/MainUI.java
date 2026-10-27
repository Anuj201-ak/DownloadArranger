package com.downloadarranger;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

public class MainUI extends JFrame implements FolderWatchService.Listener {

    private final List<String> folderList = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, String> lastMovedMap = new ConcurrentHashMap<>();
    private final FolderWatchService watchService;

    private JTable table;
    private FolderTableModel tableModel;
    private JList<String> todayFilesList;
    private DefaultListModel<String> todayListModel;

    public MainUI(FolderWatchService watchService) {
        super("Download Arranger - Step 1");
        this.watchService = watchService;
        this.watchService.setListener(this);

        initFolders();
        initComponents();
        initTray();

        this.watchService.start();
        for (String path : folderList) {
            this.watchService.registerFolder(path);
        }
    }

    private void initFolders() {
        List<String> loaded = AppConfig.loadWatchFolders();
        folderList.addAll(loaded);
    }

    private void initComponents() {
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(1000, 520);
        setMinimumSize(new Dimension(850, 420));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        tableModel = new FolderTableModel();
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(26);

        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(280);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setPreferredWidth(260);

        table.setDefaultRenderer(Object.class, new StatusCellRenderer());

        todayListModel = new DefaultListModel<>();
        todayFilesList = new JList<>(todayListModel);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateTodayFileList();
            }
        });

        JScrollPane tableScroll = new JScrollPane(table);
        JPanel rightPanel = new JPanel(new BorderLayout(4, 4));
        rightPanel.add(new JLabel("Files in Selected Folder's Today Folder:"), BorderLayout.NORTH);
        rightPanel.add(new JScrollPane(todayFilesList), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, rightPanel);
        splitPane.setDividerLocation(660);
        splitPane.setResizeWeight(0.7);
        add(splitPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        JButton btnAdd = new JButton("Add Watch Folder");
        JButton btnRemove = new JButton("Remove Watch Folder");
        JButton btnPauseResume = new JButton("Pause / Resume");
        JButton btnOrganizeNow = new JButton("Organize Now");
        JButton btnOpenToday = new JButton("Open Today's Folder");

        btnAdd.addActionListener(e -> onAddFolder());
        btnRemove.addActionListener(e -> onRemoveFolder());
        btnPauseResume.addActionListener(e -> onTogglePause());
        btnOrganizeNow.addActionListener(e -> onOrganizeNow());
        btnOpenToday.addActionListener(e -> onOpenTodayFolder());

        buttonPanel.add(btnAdd);
        buttonPanel.add(btnRemove);
        buttonPanel.add(btnPauseResume);
        buttonPanel.add(btnOrganizeNow);
        buttonPanel.add(btnOpenToday);
        add(buttonPanel, BorderLayout.SOUTH);

        if (!folderList.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
            updateTodayFileList();
        }
    }

    private void initTray() {
        if (!SystemTray.isSupported()) {
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();
        Image iconImage = createTrayIconImage();

        PopupMenu popup = new PopupMenu();
        MenuItem openItem = new MenuItem("Open Window");
        MenuItem organizeItem = new MenuItem("Organize Now");
        MenuItem exitItem = new MenuItem("Exit");

        openItem.addActionListener(e -> {
            setVisible(true);
            setExtendedState(JFrame.NORMAL);
            toFront();
        });

        organizeItem.addActionListener(e -> onOrganizeNow());

        exitItem.addActionListener(e -> {
            watchService.stop();
            System.exit(0);
        });

        popup.add(openItem);
        popup.add(organizeItem);
        popup.addSeparator();
        popup.add(exitItem);

        TrayIcon trayIcon = new TrayIcon(iconImage, "Download Arranger", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> {
            setVisible(true);
            setExtendedState(JFrame.NORMAL);
            toFront();
        });

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.err.println("Could not add tray icon: " + e.getMessage());
        }

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                setVisible(false);
            }
        });
    }

    private Image createTrayIconImage() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(30, 144, 255));
        g.fillRect(1, 1, 14, 14);
        g.setColor(Color.WHITE);
        g.drawLine(8, 3, 8, 11);
        g.drawLine(5, 8, 8, 11);
        g.drawLine(11, 8, 8, 11);
        g.dispose();
        return image;
    }

    private String getSelectedFolder() {
        int row = table.getSelectedRow();
        if (row >= 0 && row < folderList.size()) {
            return folderList.get(row);
        }
        return null;
    }

    private void onAddFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Watch Folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            String newPath = chooser.getSelectedFile().getAbsolutePath();
            if (!folderList.contains(newPath)) {
                folderList.add(newPath);
                AppConfig.saveWatchFolders(new ArrayList<>(folderList));
                watchService.registerFolder(newPath);
                tableModel.fireTableDataChanged();
                int idx = folderList.size() - 1;
                table.setRowSelectionInterval(idx, idx);
                updateTodayFileList();
            }
        }
    }

    private void onRemoveFolder() {
        int row = table.getSelectedRow();
        if (row >= 0) {
            String removed = folderList.remove(row);
            AppConfig.saveWatchFolders(new ArrayList<>(folderList));
            watchService.unregisterFolder(removed);
            lastMovedMap.remove(removed);
            tableModel.fireTableDataChanged();
            todayListModel.clear();
            if (!folderList.isEmpty()) {
                int nextSel = Math.min(row, folderList.size() - 1);
                table.setRowSelectionInterval(nextSel, nextSel);
                updateTodayFileList();
            }
        } else {
            JOptionPane.showMessageDialog(this, "Select a folder row to remove.", "Notice", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void onTogglePause() {
        String selected = getSelectedFolder();
        if (selected != null) {
            boolean current = watchService.isPaused(selected);
            watchService.setPaused(selected, !current);
            tableModel.fireTableDataChanged();
        } else {
            JOptionPane.showMessageDialog(this, "Select a folder row to pause/resume.", "Notice", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void onOrganizeNow() {
        new Thread(() -> {
            for (String folderStr : folderList) {
                Path folderPath = Path.of(folderStr);
                if (Files.exists(folderPath) && Files.isDirectory(folderPath)) {
                    FileOrganizer.organizeLooseFilesInFolder(folderPath);
                }
            }
            SwingUtilities.invokeLater(() -> {
                tableModel.fireTableDataChanged();
                updateTodayFileList();
            });
        }, "OrganizeNow-Worker").start();
    }

    private void onOpenTodayFolder() {
        String selected = getSelectedFolder();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a folder row first.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Path target = FileOrganizer.getTodayTargetDirectory(Path.of(selected));
        try {
            if (!Files.exists(target)) {
                Files.createDirectories(target);
            }
            Desktop.getDesktop().open(target.toFile());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not open folder: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateTodayFileList() {
        todayListModel.clear();
        String selected = getSelectedFolder();
        if (selected == null) return;

        Path todayDir = FileOrganizer.getTodayTargetDirectory(Path.of(selected));
        if (Files.exists(todayDir) && Files.isDirectory(todayDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(todayDir)) {
                List<String> files = new ArrayList<>();
                for (Path p : stream) {
                    if (Files.isRegularFile(p)) {
                        files.add(p.getFileName().toString());
                    }
                }
                files.sort(String.CASE_INSENSITIVE_ORDER);
                for (String f : files) {
                    todayListModel.addElement(f);
                }
            } catch (IOException ignored) {}
        }
    }

    private int getTodayCount(String folderPathStr) {
        Path todayDir = FileOrganizer.getTodayTargetDirectory(Path.of(folderPathStr));
        if (!Files.exists(todayDir) || !Files.isDirectory(todayDir)) {
            return 0;
        }
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(todayDir)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    count++;
                }
            }
        } catch (IOException ignored) {}
        return count;
    }

    @Override
    public void onFolderStateChanged(String folderPath) {
        SwingUtilities.invokeLater(() -> {
            tableModel.fireTableDataChanged();
            updateTodayFileList();
        });
    }

    @Override
    public void onFileProcessed(String folderPath, String originalName, String newName) {
        lastMovedMap.put(folderPath, originalName + " -> " + newName);
        SwingUtilities.invokeLater(() -> {
            tableModel.fireTableDataChanged();
            updateTodayFileList();
        });
    }

    private class FolderTableModel extends AbstractTableModel {
        private final String[] COLUMNS = {
                "Status", "Watch Folder", "Today's Folder", "Count Today", "Last File Moved"
        };

        @Override
        public int getRowCount() {
            return folderList.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int col) {
            return COLUMNS[col];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex >= folderList.size()) return "";
            String path = folderList.get(rowIndex);

            switch (columnIndex) {
                case 0:
                    if (!watchService.isAvailable(path)) return "Unavailable";
                    return watchService.isPaused(path) ? "Paused" : "Watching";
                case 1:
                    return path;
                case 2:
                    return FileOrganizer.getTodayTargetDirectory(Path.of(path)).toString();
                case 3:
                    return getTodayCount(path);
                case 4:
                    return lastMovedMap.getOrDefault(path, "-");
                default:
                    return "";
            }
        }
    }

    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
            if (column == 0 && value != null) {
                String status = value.toString();
                if ("Watching".equals(status)) {
                    c.setForeground(new Color(0, 130, 0));
                } else if ("Paused".equals(status)) {
                    c.setForeground(new Color(180, 110, 0));
                } else {
                    c.setForeground(Color.RED);
                }
            } else {
                c.setForeground(isSelected ? tbl.getSelectionForeground() : Color.BLACK);
            }
            return c;
        }
    }
}