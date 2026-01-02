package com.cctv.ui;

import com.cctv.util.HelpManager;
import javax.swing.*;
import java.awt.*;

public class WelcomePanel extends JPanel {
    
    public WelcomePanel(WizardFrame frame) {
        setLayout(new BorderLayout());
        
        JPanel centerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        
        JLabel title = new JLabel("CCTV Discovery Tool");
        title.setFont(new Font("Arial", Font.BOLD, 24));
        gbc.gridx = 0;
        gbc.gridy = 0;
        centerPanel.add(title, gbc);
        
        JLabel subtitle = new JLabel("Discover and analyze IP cameras on your network");
        subtitle.setFont(new Font("Arial", Font.PLAIN, 14));
        gbc.gridy = 1;
        centerPanel.add(subtitle, gbc);
        
        JButton startButton = createStyledButton("Start Discovery", new Color(92, 184, 92), new Color(68, 157, 68));
        startButton.setFont(new Font("Arial", Font.BOLD, 16));
        startButton.setPreferredSize(new Dimension(180, 45));
        startButton.addActionListener(e -> frame.showPanel("network"));
        gbc.gridy = 2;
        gbc.insets = new Insets(30, 10, 10, 10);
        centerPanel.add(startButton, gbc);
        
        JButton helpButton = createStyledButton("Help", new Color(91, 192, 222), new Color(49, 176, 213));
        helpButton.setFont(new Font("Arial", Font.PLAIN, 14));
        helpButton.setPreferredSize(new Dimension(100, 35));
        helpButton.addActionListener(e -> HelpManager.openUserManual());
        gbc.gridy = 3;
        gbc.insets = new Insets(10, 10, 10, 10);
        centerPanel.add(helpButton, gbc);
        
        add(centerPanel, BorderLayout.CENTER);
    }
    
    private JButton createStyledButton(String text, Color color1, Color color2) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, color1, 0, getHeight(), color2);
                g2d.setPaint(gp);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2d.dispose();
                super.paintComponent(g);
            }
        };
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }
}
