package com.cctv.ui;

import com.cctv.export.ExcelExporter;
import com.cctv.model.Camera;
import com.cctv.util.Logger;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ResultsPanel extends JPanel {
    private JButton exportButton;
    private JButton exitButton;
    
    public ResultsPanel(WizardFrame frame, List<Camera> cameras) {
        setLayout(new BorderLayout());
        
        JLabel title = new JLabel("Discovery Results - " + cameras.size() + " Camera(s)");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(title, BorderLayout.NORTH);
        
        String[] columns = {"IP", "Main URL", "Sub URL", "Status"};
        DefaultTableModel tableModel = new DefaultTableModel(columns, 0);
        
        for (Camera camera : cameras) {
            String mainUrl = camera.getMainStream() != null ? camera.getMainStream().getRtspUrl() : "N/A";
            String subUrl = camera.getSubStream() != null ? camera.getSubStream().getRtspUrl() : "N/A";
            String status = camera.getErrorMessage() != null ? "Error" : "OK";
            tableModel.addRow(new Object[]{camera.getIpAddress(), mainUrl, subUrl, status});
        }
        
        JTable table = new JTable(tableModel);
        table.setEnabled(false);
        table.setRowHeight(25);
        table.setShowGrid(true);
        table.setGridColor(new Color(230, 230, 230));
        table.setFont(new Font("Arial", Font.PLAIN, 12));
        
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 248, 248));
                return c;
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        
        exitButton = createStyledButton("Exit", new Color(220, 53, 69), new Color(201, 48, 44));
        exitButton.setEnabled(false);
        exitButton.addActionListener(e -> System.exit(0));
        buttonPanel.add(exitButton);
        
        exportButton = createStyledButton("Export to Excel", new Color(74, 144, 226), new Color(53, 122, 189));
        exportButton.addActionListener(e -> {
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
                        JOptionPane.showMessageDialog(frame, "Export completed: " + finalPath, "Success", JOptionPane.INFORMATION_MESSAGE);
                        exitButton.setEnabled(true);
                    }
                }.execute();
            }
        });
        buttonPanel.add(exportButton);
        
        add(buttonPanel, BorderLayout.SOUTH);
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
