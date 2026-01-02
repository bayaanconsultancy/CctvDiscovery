package com.cctv.ui;

import com.cctv.export.ExcelExporter;
import com.cctv.model.Camera;
import com.cctv.util.Logger;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ResultsPanel extends JPanel {
    private JButton exportButton;
    private JButton exitButton;

    public ResultsPanel(WizardFrame frame, List<Camera> cameras) {
        setLayout(new BorderLayout());

        // Separate cameras into successful and failed
        List<Camera> successfulCameras = new ArrayList<>();
        List<Camera> failedCameras = new ArrayList<>();

        for (Camera camera : cameras) {
            // Debug logging to verify camera state
            Logger.info("ResultsPanel - Camera " + camera.getIpAddress() + ": MainStream=" + 
                       (camera.getMainStream() != null ? camera.getMainStream().getRtspUrl() : "null") + 
                       ", SubStream=" + (camera.getSubStream() != null ? camera.getSubStream().getRtspUrl() : "null"));
            
            if (camera.getMainStream() != null || camera.getSubStream() != null) {
                successfulCameras.add(camera);
            } else if (!camera.isNvr()) {
                failedCameras.add(camera);
            }
        }

        // Header with counts
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Discovery Results");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        headerPanel.add(title, BorderLayout.NORTH);

        JLabel summary = new JLabel(String.format(
                "Successful: %d  |  Failed: %d  |  Total: %d",
                successfulCameras.size(),
                failedCameras.size(),
                cameras.size()));
        summary.setFont(new Font("Arial", Font.PLAIN, 14));
        summary.setForeground(Color.GRAY);
        summary.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        headerPanel.add(summary, BorderLayout.SOUTH);

        add(headerPanel, BorderLayout.NORTH);

        // Create tabbed pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Arial", Font.BOLD, 12));

        // All tab
        JPanel allPanel = createResultsTable(cameras, null);
        tabbedPane.addTab("All Cameras (" + cameras.size() + ")",
                null, allPanel, "All discovered cameras");

        // Successful tab
        JPanel successPanel = createResultsTable(successfulCameras, true);
        tabbedPane.addTab("Successful (" + successfulCameras.size() + ")",
                null, successPanel, "Cameras with discovered streams");
        tabbedPane.setForegroundAt(1, new Color(40, 167, 69)); // Darker green for text
        tabbedPane.setBackgroundAt(1, new Color(220, 255, 220)); // Light green background

        // Failed tab
        JPanel failedPanel = createResultsTable(failedCameras, false);
        tabbedPane.addTab("Failed (" + failedCameras.size() + ")",
                null, failedPanel, "Cameras that failed discovery");
        tabbedPane.setForegroundAt(2, new Color(220, 53, 69)); // Red text
        tabbedPane.setBackgroundAt(2, new Color(255, 220, 220)); // Light red background

        add(tabbedPane, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());

        exitButton = createStyledButton("Exit", new Color(220, 53, 69), new Color(201, 48, 44));
        exitButton.setEnabled(false);
        exitButton.addActionListener(e -> System.exit(0));
        buttonPanel.add(exitButton);

        exportButton = createStyledButton("Export to Excel", new Color(74, 144, 226), new Color(53, 122, 189));
        exportButton.addActionListener(e -> exportToExcel(frame, cameras));
        buttonPanel.add(exportButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JPanel createResultsTable(List<Camera> cameras, Boolean successFilter) {
        JPanel panel = new JPanel(new BorderLayout());

        String[] columns = { "Status", "IP Address", "MAC Address", "Main Stream", "Sub Stream", "Manufacturer", "Model" };
        DefaultTableModel tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        for (Camera camera : cameras) {
            // Skip based on filter
            if (successFilter != null) {
                boolean hasStreams = camera.getMainStream() != null || camera.getSubStream() != null;
                if (successFilter && !hasStreams)
                    continue;
                if (!successFilter && hasStreams)
                    continue;
            }

            String status = (camera.getMainStream() != null || camera.getSubStream() != null) ? "Success" : "Failed";
            String mainUrl = camera.getMainStream() != null ? camera.getMainStream().getRtspUrl() : "N/A";
            String subUrl = camera.getSubStream() != null ? camera.getSubStream().getRtspUrl() : "N/A";
            String macAddress = camera.getMacAddress() != null ? camera.getMacAddress() : "Unknown";
            String manufacturer = camera.getManufacturer() != null ? camera.getManufacturer() : "Unknown";
            String model = camera.getModel() != null ? camera.getModel() : "Unknown";

            tableModel.addRow(new Object[] { status, camera.getIpAddress(), macAddress, mainUrl, subUrl, manufacturer, model });
        }

        JTable table = new JTable(tableModel);
        table.setRowHeight(25);
        table.setShowGrid(true);
        table.setGridColor(new Color(230, 230, 230));
        table.setFont(new Font("Arial", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));

        // Custom renderer for status column with colors
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(CENTER);

                if (!isSelected) {
                    if ("Success".equals(value)) {
                        c.setForeground(new Color(40, 167, 69));
                        c.setFont(new Font("Arial", Font.BOLD, 12));
                    } else {
                        c.setForeground(new Color(220, 53, 69));
                        c.setFont(new Font("Arial", Font.BOLD, 12));
                    }
                }
                return c;
            }
        });

        // Alternate row colors
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (!isSelected) {
                    if (column == 0) {
                        // Status column handled by specific renderer
                        return c;
                    }
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 248, 248));
                    c.setForeground(Color.BLACK);
                }

                return c;
            }
        });

        // Set column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(0).setMaxWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);
        table.getColumnModel().getColumn(3).setPreferredWidth(280);
        table.getColumnModel().getColumn(4).setPreferredWidth(280);
        table.getColumnModel().getColumn(5).setPreferredWidth(100);
        table.getColumnModel().getColumn(6).setPreferredWidth(100);

        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void exportToExcel(WizardFrame frame, List<Camera> cameras) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmm");
        String timestamp = sdf.format(new Date());
        String defaultFilename = "CCTV-Discovery-" + timestamp + ".xlsx";

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Excel File");
        fileChooser.setSelectedFile(new File(defaultFilename));

        int result = fileChooser.showSaveDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String filePath = file.getAbsolutePath();
            if (!filePath.endsWith(".xlsx")) {
                filePath += ".xlsx";
            }

            final String finalPath = filePath;
            exportButton.setEnabled(false);

            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    ExcelExporter.export(cameras, finalPath);
                    return null;
                }

                @Override
                protected void done() {
                    JOptionPane.showMessageDialog(frame,
                            "Export completed: " + finalPath,
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    exitButton.setEnabled(true);
                }
            }.execute();
        }
    }

    private JButton createStyledButton(String text, Color color1, Color color2) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (isEnabled()) {
                    GradientPaint gp = new GradientPaint(0, 0, color1, 0, getHeight(), color2);
                    g2d.setPaint(gp);
                } else {
                    g2d.setColor(new Color(200, 200, 200));
                }

                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2d.dispose();
                super.paintComponent(g);
            }
        };

        button.setForeground(Color.WHITE);
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setPreferredSize(new Dimension(140, 35));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        return button;
    }
}
