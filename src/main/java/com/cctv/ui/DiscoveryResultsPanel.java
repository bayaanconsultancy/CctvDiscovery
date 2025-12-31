package com.cctv.ui;

import com.cctv.discovery.PortScanner;
import com.cctv.model.Camera;
import javax.swing.*;
import java.awt.*;
import java.util.List;

public class DiscoveryResultsPanel extends JPanel {

    public DiscoveryResultsPanel(WizardFrame frame, List<Camera> cameras, List<String> ipRange) {
        setLayout(new BorderLayout());

        JPanel contentPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        JLabel title = new JLabel("ONVIF Discovery Results");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        contentPanel.add(title, gbc);

        String message = cameras.isEmpty() ? "No devices found via ONVIF"
                : "Found " + cameras.size() + " camera(s) via ONVIF";

        JLabel countLabel = new JLabel(message);
        countLabel.setFont(new Font("Arial", Font.BOLD, 14));
        countLabel.setForeground(cameras.isEmpty() ? new Color(220, 53, 69) : new Color(0, 123, 255));
        gbc.gridy = 1;
        contentPanel.add(countLabel, gbc);

        if (cameras.isEmpty()) {
            JLabel noDevices = new JLabel("No devices found. Redirecting to port scan...");
            gbc.gridy = 2;
            contentPanel.add(noDevices, gbc);

            add(contentPanel, BorderLayout.CENTER);

            Timer timer = new Timer(2000, e -> startPortScan(frame, cameras, ipRange));
            timer.setRepeats(false);
            timer.start();
        } else {
            JLabel question = new JLabel("Proceed with found devices or perform port scan?");
            gbc.gridy = 2;
            contentPanel.add(question, gbc);

            JButton proceedButton = createStyledButton("Proceed", new Color(92, 184, 92), new Color(68, 157, 68));
            proceedButton.addActionListener(e -> {
                CredentialPanel credPanel = new CredentialPanel(frame, cameras);
                frame.addPanel(credPanel, "credentials");
                frame.showPanel("credentials");
            });
            gbc.gridy = 3;
            gbc.gridwidth = 1;
            contentPanel.add(proceedButton, gbc);

            JButton portScanButton = createStyledButton("Port Scan", new Color(74, 144, 226), new Color(53, 122, 189));
            portScanButton.addActionListener(e -> {
                proceedButton.setEnabled(false);
                portScanButton.setEnabled(false);
                startPortScan(frame, cameras, ipRange);
            });
            gbc.gridx = 1;
            contentPanel.add(portScanButton, gbc);

            add(contentPanel, BorderLayout.CENTER);
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

    private void startPortScan(WizardFrame frame, List<Camera> existingCameras, List<String> ipRange) {
        ProgressPanel progressPanel = new ProgressPanel("Performing port scan...");
        // Port scan usually scans many IPs, so updating "cameras processed" label might
        // be confusing if we just pass index.
        // But for now let's use the provided counts.
        frame.addPanel(progressPanel, "portscan");
        frame.showPanel("portscan");

        new SwingWorker<List<Camera>, Void>() {
            @Override
            protected List<Camera> doInBackground() {
                List<Camera> scanned = PortScanner.scan(ipRange, new com.cctv.discovery.ProgressListener() {
                    @Override
                    public void onProgress(String ip, int current, int total, String status) {
                        SwingUtilities.invokeLater(() -> {
                            progressPanel.setProgress(current, total);
                            progressPanel.updateCurrentCamera(ip, "Scanning...");
                        });
                    }

                    @Override
                    public void onComplete() {
                        // Handled in done
                    }

                    @Override
                    public void onCancelled() {
                        // Not implemented for port scan yet
                    }
                });

                for (Camera cam : scanned) {
                    if (!existingCameras.contains(cam)) {
                        existingCameras.add(cam);
                    }
                }
                return existingCameras;
            }

            @Override
            protected void done() {
                try {
                    List<Camera> allCameras = get();
                    CredentialPanel credPanel = new CredentialPanel(frame, allCameras);
                    frame.addPanel(credPanel, "credentials");
                    frame.showPanel("credentials");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Port scan failed: " + ex.getMessage(), "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
