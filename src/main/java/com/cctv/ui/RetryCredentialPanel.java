package com.cctv.ui;

import com.cctv.discovery.DeviceProber;
import com.cctv.model.Camera;
import com.cctv.util.Logger;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class RetryCredentialPanel extends JPanel {
    private JTable table;
    private DefaultTableModel tableModel;
    private List<Camera> allCameras;
    private List<Camera> failedCameras;
    
    public RetryCredentialPanel(WizardFrame frame, List<Camera> allCameras, List<Camera> failedCameras) {
        this.allCameras = allCameras;
        this.failedCameras = failedCameras;
        setLayout(new BorderLayout());
        
        JLabel title = new JLabel("Authentication Failed - Retry Credentials");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(title, BorderLayout.NORTH);
        
        String[] columns = {"IP Address", "Username", "Password"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column > 0;
            }
        };
        
        for (Camera camera : failedCameras) {
            tableModel.addRow(new Object[]{camera.getIpAddress(), "admin", ""});
        }
        
        table = new JTable(tableModel);
        table.getTableHeader().setReorderingAllowed(false);
        table.setRowHeight(25);
        table.setShowGrid(true);
        table.setGridColor(new Color(230, 230, 230));
        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        
        JButton skipButton = createStyledButton("Skip", new Color(220, 53, 69), new Color(201, 48, 44));
        skipButton.addActionListener(e -> {
            ResultsPanel resultsPanel = new ResultsPanel(frame, allCameras);
            frame.addPanel(resultsPanel, "finalResults");
            frame.showPanel("finalResults");
        });
        buttonPanel.add(skipButton);
        
        JButton retryButton = createStyledButton("Retry", new Color(74, 144, 226), new Color(53, 122, 189));
        retryButton.addActionListener(e -> {
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
            
            boolean allPasswordsSet = true;
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String password = (String) tableModel.getValueAt(i, 2);
                if (password == null || password.trim().isEmpty()) {
                    allPasswordsSet = false;
                    break;
                }
            }
            
            if (!allPasswordsSet) {
                JOptionPane.showMessageDialog(frame, 
                    "Please enter password for all cameras before retrying.", 
                    "Missing Passwords", 
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            retryButton.setEnabled(false);
            skipButton.setEnabled(false);
            
            ProgressPanel progressPanel = new ProgressPanel("Retrying authentication...");
            frame.addPanel(progressPanel, "retryProgress");
            frame.showPanel("retryProgress");
            
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    for (int i = 0; i < tableModel.getRowCount(); i++) {
                        String username = (String) tableModel.getValueAt(i, 1);
                        String password = (String) tableModel.getValueAt(i, 2);
                        Camera cam = failedCameras.get(i);
                        
                        Logger.info("=== Retry: Setting credentials for " + cam.getIpAddress() + " ===");
                        Logger.info("New Username: " + username + ", Has Password: " + (password != null && !password.isEmpty()));
                        
                        cam.setUsername(username);
                        cam.setPassword(password);
                        cam.setAuthFailed(false);
                        cam.setErrorMessage(null);
                        cam.setMainStream(null);
                        cam.setSubStream(null);
                    }
                    
                    DeviceProber.probeAll(failedCameras, progressPanel);
                    
                    for (Camera cam : failedCameras) {
                        if (cam.getMainStream() == null && cam.getErrorMessage() != null && 
                            cam.getErrorMessage().contains("RTSP")) {
                            Logger.info("Retrying RTSP URL guessing for " + cam.getIpAddress());
                            com.cctv.discovery.RtspUrlGuesser.tryGuessUrls(cam);
                        }
                    }
                    
                    return null;
                }
                
                @Override
                protected void done() {
                    java.util.List<Camera> stillFailedCameras = new java.util.ArrayList<>();
                    for (Camera camera : failedCameras) {
                        if (camera.isAuthFailed() || camera.getErrorMessage() != null) {
                            stillFailedCameras.add(camera);
                        }
                    }
                    
                    if (!stillFailedCameras.isEmpty()) {
                        RetryCredentialPanel retryPanel = new RetryCredentialPanel(frame, allCameras, stillFailedCameras);
                        frame.addPanel(retryPanel, "retryAgain");
                        frame.showPanel("retryAgain");
                    } else {
                        ResultsPanel resultsPanel = new ResultsPanel(frame, allCameras);
                        frame.addPanel(resultsPanel, "finalResults");
                        frame.showPanel("finalResults");
                    }
                }
            }.execute();
        });
        buttonPanel.add(retryButton);
        
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
