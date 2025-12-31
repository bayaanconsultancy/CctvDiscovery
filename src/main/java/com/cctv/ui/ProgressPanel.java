package com.cctv.ui;

import javax.swing.*;
import java.awt.*;

public class ProgressPanel extends JPanel {
    private JLabel messageLabel;
    private JLabel currentCameraLabel;
    private JLabel countLabel;
    private JProgressBar progressBar;
    private JButton cancelButton;
    private Runnable onCancel;

    public ProgressPanel(String message) {
        this(message, null);
    }

    public ProgressPanel(String message, Runnable onCancel) {
        this.onCancel = onCancel;
        setLayout(new BorderLayout());

        JPanel centerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        // Main message
        messageLabel = new JLabel(message);
        messageLabel.setFont(new Font("Arial", Font.BOLD, 16));
        gbc.gridx = 0;
        gbc.gridy = 0;
        centerPanel.add(messageLabel, gbc);

        // Current camera being processed
        currentCameraLabel = new JLabel(" ");
        currentCameraLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        currentCameraLabel.setForeground(new Color(74, 144, 226));
        gbc.gridy = 1;
        centerPanel.add(currentCameraLabel, gbc);

        // Count label
        countLabel = new JLabel(" ");
        countLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        countLabel.setForeground(Color.GRAY);
        gbc.gridy = 2;
        centerPanel.add(countLabel, gbc);

        // Progress bar
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(400, 30));
        progressBar.setStringPainted(true);
        gbc.gridy = 3;
        centerPanel.add(progressBar, gbc);

        // Cancel button
        if (onCancel != null) {
            cancelButton = createCancelButton();
            gbc.gridy = 4;
            gbc.insets = new Insets(20, 10, 10, 10);
            centerPanel.add(cancelButton, gbc);
        }

        add(centerPanel, BorderLayout.CENTER);
    }

    private JButton createCancelButton() {
        JButton button = new JButton("Cancel Discovery") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (isEnabled()) {
                    GradientPaint gp = new GradientPaint(0, 0, new Color(220, 53, 69), 0, getHeight(),
                            new Color(201, 48, 44));
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
        button.setPreferredSize(new Dimension(160, 35));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(
                    this,
                    "Cancel discovery? Partial results will be shown.",
                    "Confirm Cancel",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (result == JOptionPane.YES_OPTION && onCancel != null) {
                button.setEnabled(false);
                button.setText("Cancelling...");
                onCancel.run();
            }
        });

        return button;
    }

    public void setProgress(int current, int total) {
        SwingUtilities.invokeLater(() -> {
            if (total > 0) {
                progressBar.setIndeterminate(false);
                progressBar.setMaximum(total);
                progressBar.setValue(current);
                progressBar.setString(null); // Shows percentage by default
                countLabel.setText(current + " of " + total + " processed");
            } else {
                progressBar.setIndeterminate(true);
                progressBar.setString("");
                countLabel.setText(" ");
            }
        });
    }

    public void updateMessage(String message) {
        SwingUtilities.invokeLater(() -> messageLabel.setText(message));
    }

    public void updateCurrentCamera(String camera, String status) {
        SwingUtilities.invokeLater(() -> {
            if (camera != null && !camera.isEmpty()) {
                currentCameraLabel.setText(camera + ": " + status);
            } else {
                currentCameraLabel.setText(" ");
            }
        });
    }

    public void disableCancel() {
        SwingUtilities.invokeLater(() -> {
            if (cancelButton != null) {
                cancelButton.setEnabled(false);
            }
        });
    }
}
