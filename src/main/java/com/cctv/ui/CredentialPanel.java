package com.cctv.ui;

import com.cctv.discovery.DeviceProber;
import com.cctv.model.Camera;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CredentialPanel extends JPanel {
    private JTable table;
    private DefaultTableModel tableModel;
    private List<Camera> cameras;
    private JButton setPasswordButton;
    private JButton continueButton;
    private JLabel statusLabel;
    
    public CredentialPanel(WizardFrame frame, List<Camera> cameras) {
        this.cameras = cameras;
        setLayout(new BorderLayout());
        
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Assign Credentials");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        headerPanel.add(title, BorderLayout.NORTH);
        
        statusLabel = new JLabel("0/" + cameras.size() + " cameras configured");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(220, 53, 69));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        headerPanel.add(statusLabel, BorderLayout.SOUTH);
        
        add(headerPanel, BorderLayout.NORTH);
        
        String[] columns = {"Select", "IP Address", "Username", "Password"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? Boolean.class : String.class;
            }
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0 || column == 2 || column == 3;
            }
        };
        
        for (Camera camera : cameras) {
            tableModel.addRow(new Object[]{false, camera.getIpAddress(), "", ""});
        }
        
        table = new JTable(tableModel);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.setRowHeight(25);
        table.setShowGrid(true);
        table.setGridColor(new Color(230, 230, 230));
        
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    String username = (String) tableModel.getValueAt(row, 2);
                    String password = (String) tableModel.getValueAt(row, 3);
                    if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
                        c.setBackground(new Color(255, 250, 205));
                    } else {
                        c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 248, 248));
                    }
                }
                return c;
            }
        };
        
        for (int i = 1; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
        
        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        
        JButton selectAllButton = createStyledButton("Select All", new Color(74, 144, 226), new Color(53, 122, 189));
        selectAllButton.addActionListener(e -> {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                tableModel.setValueAt(true, i, 0);
            }
        });
        buttonPanel.add(selectAllButton);
        
        setPasswordButton = createStyledButton("Set Credentials", new Color(74, 144, 226), new Color(53, 122, 189));
        setPasswordButton.setEnabled(false);
        setPasswordButton.addActionListener(e -> showCredentialDialog());
        buttonPanel.add(setPasswordButton);
        
        continueButton = createStyledButton("Continue", new Color(92, 184, 92), new Color(68, 157, 68));
        continueButton.setEnabled(false);
        continueButton.addActionListener(e -> proceedToProbing(frame));
        buttonPanel.add(continueButton);
        
        table.getModel().addTableModelListener(e -> {
            boolean anySelected = false;
            int configuredCount = 0;
            
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                if ((Boolean) tableModel.getValueAt(i, 0)) {
                    anySelected = true;
                }
                String username = (String) tableModel.getValueAt(i, 2);
                String password = (String) tableModel.getValueAt(i, 3);
                if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                    configuredCount++;
                }
            }
            
            setPasswordButton.setEnabled(anySelected);
            continueButton.setEnabled(configuredCount == cameras.size());
            
            statusLabel.setText(configuredCount + "/" + cameras.size() + " cameras configured");
            if (configuredCount == cameras.size()) {
                statusLabel.setForeground(new Color(92, 184, 92));
            } else {
                statusLabel.setForeground(new Color(220, 53, 69));
            }
            
            table.repaint();
        });
        
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
        
        button.setForeground(isEnabled() ? Color.WHITE : Color.GRAY);
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setPreferredSize(new Dimension(140, 35));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        return button;
    }
    
    private void showCredentialDialog() {
        JTextField userField = new JTextField("admin", 15);
        JPasswordField passField = new JPasswordField(15);
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        panel.add(new JLabel("Username:"));
        panel.add(userField);
        panel.add(new JLabel("Password:"));
        panel.add(passField);
        
        SwingUtilities.invokeLater(() -> passField.requestFocusInWindow());
        
        int result = JOptionPane.showConfirmDialog(this, panel, "Enter Credentials", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String username = userField.getText().trim();
            String password = new String(passField.getPassword());
            
            if (username.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username cannot be empty", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                if ((Boolean) tableModel.getValueAt(i, 0)) {
                    tableModel.setValueAt(username, i, 2);
                    tableModel.setValueAt(password, i, 3);
                    cameras.get(i).setUsername(username);
                    cameras.get(i).setPassword(password);
                }
            }
        }
    }
    
    private void proceedToProbing(WizardFrame frame) {
        setPasswordButton.setEnabled(false);
        continueButton.setEnabled(false);
        
        ProgressPanel progressPanel = new ProgressPanel("Fetching camera details...");
        frame.addPanel(progressPanel, "probing");
        frame.showPanel("probing");
        
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                DeviceProber.probeAll(cameras);
                return null;
            }
            
            @Override
            protected void done() {
                List<Camera> failedCameras = new ArrayList<>();
                for (Camera camera : cameras) {
                    if (camera.isAuthFailed() || camera.getErrorMessage() != null) {
                        failedCameras.add(camera);
                    }
                }
                
                if (!failedCameras.isEmpty()) {
                    RetryCredentialPanel retryPanel = new RetryCredentialPanel(frame, cameras, failedCameras);
                    frame.addPanel(retryPanel, "retry");
                    frame.showPanel("retry");
                } else {
                    ResultsPanel resultsPanel = new ResultsPanel(frame, cameras);
                    frame.addPanel(resultsPanel, "finalResults");
                    frame.showPanel("finalResults");
                }
            }
        }.execute();
    }
}
