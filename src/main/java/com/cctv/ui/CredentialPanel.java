package com.cctv.ui;

import com.cctv.api.Credential;
import com.cctv.model.Camera;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CredentialPanel extends JPanel {
    private JTable cameraTable;
    private DefaultTableModel cameraTableModel;
    private List<Camera> cameras;

    // Credential Management
    private JTable credentialTable;
    private DefaultTableModel credentialTableModel;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton addCredentialButton;
    private JButton removeCredentialButton;
    private JButton continueButton;

    public CredentialPanel(WizardFrame frame, List<Camera> cameras) {
        this.cameras = cameras;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- Header ---
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Discovery Results & Credentials");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        headerPanel.add(title, BorderLayout.NORTH);

        JLabel subtitle = new JLabel("Add credentials to try against all discovered devices.");
        subtitle.setForeground(Color.GRAY);
        headerPanel.add(subtitle, BorderLayout.SOUTH);

        add(headerPanel, BorderLayout.NORTH);

        // --- Main Content (Split Pane) ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.6); // 60% for camera list
        splitPane.setDividerLocation(500);

        // 1. Left: Camera List
        JPanel cameraListPanel = createCameraListPanel();
        splitPane.setLeftComponent(cameraListPanel);

        // 2. Right: Credential Manager
        JPanel credentialManagerPanel = createCredentialManagerPanel();
        splitPane.setRightComponent(credentialManagerPanel);

        add(splitPane, BorderLayout.CENTER);

        // --- Bottom: Actions ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER)); // Centered
        continueButton = createStyledButton("Start Probing", new Color(92, 184, 92), new Color(68, 157, 68));
        continueButton.setEnabled(false); // Enable only when at least one credential is added
        continueButton.setPreferredSize(new Dimension(200, 40));
        continueButton.addActionListener(e -> proceedToProbing(frame));
        buttonPanel.add(continueButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JPanel createCameraListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Discovered Devices"));

        String[] columns = { "#", "IP Address", "Manufacturer", "Status" };
        cameraTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Read-only
            }
        };

        int index = 1;
        for (Camera camera : cameras) {
            String manufacturer = camera.getManufacturer() != null ? camera.getManufacturer() : "Unknown";
            cameraTableModel.addRow(new Object[] { index++, camera.getIpAddress(), manufacturer, "Pending" });
        }

        cameraTable = new JTable(cameraTableModel);
        cameraTable.getColumnModel().getColumn(0).setMaxWidth(40);
        cameraTable.setRowHeight(25);
        cameraTable.setShowGrid(true);
        cameraTable.setGridColor(new Color(230, 230, 230));

        // Custom renderer for rows
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        for (int i = 0; i < cameraTable.getColumnCount(); i++) {
            cameraTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        panel.add(new JScrollPane(cameraTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createCredentialManagerPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Credential Pool"));

        // Input Form
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        inputPanel.add(new JLabel("Username:"), gbc);

        gbc.gridx = 1;
        usernameField = new JTextField("admin", 12); // Default to "admin"
        inputPanel.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        inputPanel.add(new JLabel("Password:"), gbc);

        gbc.gridx = 1;
        passwordField = new JPasswordField(12);
        inputPanel.add(passwordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        addCredentialButton = createStyledButton("Add to List", new Color(74, 144, 226), new Color(53, 122, 189));
        addCredentialButton.setPreferredSize(new Dimension(100, 30));
        addCredentialButton.addActionListener(e -> addCredential());
        inputPanel.add(addCredentialButton, gbc);

        panel.add(inputPanel, BorderLayout.NORTH);

        // Table Display
        String[] columns = { "Username", "Password" };
        credentialTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        credentialTable = new JTable(credentialTableModel);
        credentialTable.setRowHeight(25);
        credentialTable.setShowGrid(true);
        credentialTable.setGridColor(new Color(230, 230, 230));
        credentialTable.getSelectionModel().addListSelectionListener(e -> {
            removeCredentialButton.setEnabled(credentialTable.getSelectedRow() != -1);
        });

        panel.add(new JScrollPane(credentialTable), BorderLayout.CENTER);

        // Remove Button
        JPanel bottomPanel = new JPanel(new FlowLayout());
        removeCredentialButton = new JButton("Remove Selected");
        removeCredentialButton.setEnabled(false);
        removeCredentialButton.addActionListener(e -> {
            int selectedRow = credentialTable.getSelectedRow();
            if (selectedRow != -1) {
                credentialTableModel.removeRow(selectedRow);
                updateContinueButton();
            }
        });
        bottomPanel.add(removeCredentialButton);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void addCredential() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username cannot be empty.", "Input Error",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Add to table (Password visible as requested)
        credentialTableModel.addRow(new Object[] { username, password });

        // Clear fields (reset username to admin for convenience)
        usernameField.setText("admin");
        passwordField.setText("");
        passwordField.requestFocus();

        updateContinueButton();
    }

    private void updateContinueButton() {
        continueButton.setEnabled(credentialTableModel.getRowCount() > 0);
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

    private void proceedToProbing(WizardFrame frame) {
        // Disable buttons
        continueButton.setEnabled(false);
        addCredentialButton.setEnabled(false);
        removeCredentialButton.setEnabled(false);

        // Collect credentials from table
        List<Credential> credentials = new ArrayList<>();
        for (int i = 0; i < credentialTableModel.getRowCount(); i++) {
            String user = (String) credentialTableModel.getValueAt(i, 0);
            String pass = (String) credentialTableModel.getValueAt(i, 1);
            credentials.add(new Credential(user, pass));
        }

        ProgressPanel progressPanel = new ProgressPanel("Fetching camera details...", () -> {
            com.cctv.discovery.DeviceProber.cancel();
        });
        frame.addPanel(progressPanel, "probing");
        frame.showPanel("probing");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                // Probe with global credential list
                com.cctv.discovery.DeviceProber.probeAll(cameras, credentials,
                        new com.cctv.discovery.ProgressListener() {
                            @Override
                            public void onProgress(String camera, int current, int total, String status) {
                                progressPanel.updateCurrentCamera(camera, status);
                                progressPanel.setProgress(current, total);
                            }

                            @Override
                            public void onComplete() {
                            }

                            @Override
                            public void onCancelled() {
                            }
                        });

                return null;
            }

            @Override
            protected void done() {
                progressPanel.disableCancel();

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
