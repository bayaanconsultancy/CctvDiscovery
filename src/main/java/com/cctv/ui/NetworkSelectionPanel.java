package com.cctv.ui;

import com.cctv.discovery.OnvifDiscovery;
import com.cctv.model.Camera;
import com.cctv.network.IpRangeValidator;
import com.cctv.network.NetworkInterface;
import com.cctv.util.Logger;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.List;

public class NetworkSelectionPanel extends JPanel {
    private JComboBox<NetworkInterface> networkCombo;
    private JTextField startIpField;
    private JTextField endIpField;
    private JButton scanButton;
    private JLabel validationLabel;
    
    private final Color ERROR_COLOR = new Color(220, 53, 69);
    private final Color SUCCESS_COLOR = new Color(40, 167, 69);
    private final Border DEFAULT_BORDER = UIManager.getBorder("TextField.border");
    private final Border ERROR_BORDER = BorderFactory.createLineBorder(ERROR_COLOR, 1);
    private final Border SUCCESS_BORDER = BorderFactory.createLineBorder(SUCCESS_COLOR, 1);
    
    public NetworkSelectionPanel(WizardFrame frame) {
        setLayout(new BorderLayout());
        
        JPanel centerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        
        JLabel title = new JLabel("Network Discovery");
        title.setFont(new Font("Arial", Font.BOLD, 24));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        centerPanel.add(title, gbc);
        
        JLabel subtitle = new JLabel("Select network interface or enter IP range");
        subtitle.setFont(new Font("Arial", Font.PLAIN, 14));
        gbc.gridy = 1;
        gbc.insets = new Insets(5, 10, 20, 10);
        centerPanel.add(subtitle, gbc);
        
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(10, 10, 10, 10);
        
        gbc.gridy = 2;
        gbc.gridx = 0;
        JLabel networkLabel = new JLabel("Network Interface:");
        networkLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        centerPanel.add(networkLabel, gbc);
        
        networkCombo = new JComboBox<>();
        networkCombo.setPreferredSize(new Dimension(450, 50));
        networkCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value != null) {
                    String text = value.toString();
                    if (text.length() > 50) {
                        text = "<html><div style='width:430px'>" + text + "</div></html>";
                    }
                    label.setText(text);
                }
                return label;
            }
        });
        List<NetworkInterface> interfaces = NetworkInterface.getAvailableInterfaces();
        for (NetworkInterface ni : interfaces) {
            networkCombo.addItem(ni);
        }
        gbc.gridx = 1;
        centerPanel.add(networkCombo, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(15, 10, 15, 10);
        JLabel orLabel = new JLabel("OR");
        orLabel.setFont(new Font("Arial", Font.BOLD, 12));
        orLabel.setForeground(Color.GRAY);
        centerPanel.add(orLabel, gbc);
        
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(10, 10, 10, 10);
        
        gbc.gridy = 4;
        gbc.gridx = 0;
        JLabel startLabel = new JLabel("Start IP:");
        startLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        centerPanel.add(startLabel, gbc);
        
        startIpField = new JTextField();
        startIpField.setPreferredSize(new Dimension(450, 25));
        startIpField.setFont(new Font("Arial", Font.PLAIN, 12));
        gbc.gridx = 1;
        centerPanel.add(startIpField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 5;
        JLabel endLabel = new JLabel("End IP:");
        endLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        centerPanel.add(endLabel, gbc);
        
        endIpField = new JTextField();
        endIpField.setPreferredSize(new Dimension(450, 25));
        endIpField.setFont(new Font("Arial", Font.PLAIN, 12));
        gbc.gridx = 1;
        centerPanel.add(endIpField, gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 6;
        validationLabel = new JLabel(" ");
        validationLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        validationLabel.setForeground(ERROR_COLOR);
        centerPanel.add(validationLabel, gbc);
        
        // Add listeners for real-time validation
        DocumentListener validationListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { validateInputs(); }
            @Override
            public void removeUpdate(DocumentEvent e) { validateInputs(); }
            @Override
            public void changedUpdate(DocumentEvent e) { validateInputs(); }
        };
        
        startIpField.getDocument().addDocumentListener(validationListener);
        endIpField.getDocument().addDocumentListener(validationListener);
        networkCombo.addActionListener(e -> validateInputs());
        
        scanButton = createStyledButton("Start Scan", new Color(92, 184, 92), new Color(68, 157, 68));
        scanButton.setFont(new Font("Arial", Font.BOLD, 16));
        scanButton.setPreferredSize(new Dimension(180, 45));
        scanButton.addActionListener(e -> startScan(frame));
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(20, 10, 10, 10);
        centerPanel.add(scanButton, gbc);
        
        add(centerPanel, BorderLayout.CENTER);
    }
    
    private void startScan(WizardFrame frame) {
        String startIp = startIpField.getText().trim();
        String endIp = endIpField.getText().trim();
        
        if (!startIp.isEmpty() || !endIp.isEmpty()) {
            if (startIp.isEmpty() || endIp.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Both start and end IP addresses are required", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!IpRangeValidator.isValidIp(startIp) || !IpRangeValidator.isValidIp(endIp)) {
                JOptionPane.showMessageDialog(frame, "Invalid IP address format", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else if (networkCombo.getSelectedItem() == null) {
            JOptionPane.showMessageDialog(frame, "Please select a network interface or enter IP range", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        scanButton.setEnabled(false);
        
        new SwingWorker<List<Camera>, Void>() {
            @Override
            protected List<Camera> doInBackground() {
                try {
                    return OnvifDiscovery.discover();
                } catch (Exception e) {
                    Logger.error("ONVIF discovery failed", e);
                    throw e;
                }
            }
            
            @Override
            protected void done() {
                try {
                    List<Camera> cameras = get();
                    List<String> ipRange = null;
                    
                    String startIp = startIpField.getText().trim();
                    String endIp = endIpField.getText().trim();
                    if (!startIp.isEmpty() && !endIp.isEmpty()) {
                        ipRange = IpRangeValidator.generateRange(startIp, endIp);
                    } else if (networkCombo.getSelectedItem() != null) {
                        NetworkInterface ni = (NetworkInterface) networkCombo.getSelectedItem();
                        ipRange = ni.getIpRange();
                    }
                    
                    DiscoveryResultsPanel resultsPanel = new DiscoveryResultsPanel(frame, cameras, ipRange);
                    frame.addPanel(resultsPanel, "results");
                    frame.showPanel("results");
                } catch (Exception ex) {
                    Logger.error("Discovery process failed", ex);
                    JOptionPane.showMessageDialog(frame, "Discovery failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    scanButton.setEnabled(true);
                }
            }
        }.execute();
    }
    
    private void validateInputs() {
        String startIp = startIpField.getText().trim();
        String endIp = endIpField.getText().trim();
        boolean manualEntry = !startIp.isEmpty() || !endIp.isEmpty();
        
        scanButton.setEnabled(true);
        validationLabel.setText(" ");
        startIpField.setBorder(DEFAULT_BORDER);
        endIpField.setBorder(DEFAULT_BORDER);
        
        if (manualEntry) {
            networkCombo.setEnabled(false);
            boolean isValid = true;
            
            if (startIp.isEmpty()) {
                startIpField.setBorder(DEFAULT_BORDER); // Not error, just empty
                validationLabel.setText("Start IP is required");
                isValid = false;
            } else if (!IpRangeValidator.isValidIp(startIp)) {
                startIpField.setBorder(ERROR_BORDER);
                validationLabel.setText("Invalid Start IP format");
                isValid = false;
            } else {
                startIpField.setBorder(SUCCESS_BORDER);
            }
            
            if (endIp.isEmpty()) {
                endIpField.setBorder(DEFAULT_BORDER);
                if (isValid) validationLabel.setText("End IP is required"); // Only show if start is valid
                isValid = false;
            } else if (!IpRangeValidator.isValidIp(endIp)) {
                endIpField.setBorder(ERROR_BORDER);
                validationLabel.setText("Invalid End IP format");
                isValid = false;
            } else {
                endIpField.setBorder(SUCCESS_BORDER);
            }
            
            scanButton.setEnabled(isValid);
        } else {
            networkCombo.setEnabled(true);
            // reset borders
            startIpField.setBorder(DEFAULT_BORDER);
            endIpField.setBorder(DEFAULT_BORDER);
            
            if (networkCombo.getSelectedItem() == null) {
                scanButton.setEnabled(false);
            }
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
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }
}
