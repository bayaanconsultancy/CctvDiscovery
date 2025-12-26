package com.cctv.ui;

import javax.swing.*;
import java.awt.*;

public class ProgressPanel extends JPanel {
    private JLabel messageLabel;
    private JLabel countLabel;
    private JProgressBar progressBar;
    
    public ProgressPanel(String message) {
        setLayout(new BorderLayout());
        
        JPanel centerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        
        messageLabel = new JLabel(message);
        messageLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        gbc.gridx = 0;
        gbc.gridy = 0;
        centerPanel.add(messageLabel, gbc);
        
        countLabel = new JLabel(" ");
        countLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        countLabel.setForeground(Color.GRAY);
        gbc.gridy = 1;
        centerPanel.add(countLabel, gbc);
        
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(300, 30));
        gbc.gridy = 2;
        centerPanel.add(progressBar, gbc);
        
        add(centerPanel, BorderLayout.CENTER);
    }
    
    public void setProgress(int current, int total) {
        SwingUtilities.invokeLater(() -> {
            if (total > 0) {
                progressBar.setIndeterminate(false);
                progressBar.setMaximum(total);
                progressBar.setValue(current);
                countLabel.setText(current + " / " + total);
            } else {
                progressBar.setIndeterminate(true);
                countLabel.setText(" ");
            }
        });
    }
    
    public void updateMessage(String message) {
        SwingUtilities.invokeLater(() -> messageLabel.setText(message));
    }
}
