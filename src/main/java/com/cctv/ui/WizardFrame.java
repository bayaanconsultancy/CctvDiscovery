package com.cctv.ui;

import javax.swing.*;
import java.awt.*;

public class WizardFrame extends JFrame {
    private CardLayout cardLayout;
    private JPanel mainPanel;

    public WizardFrame() {
        setTitle("CCTV Discovery Tool");
        setSize(1024, 768);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // Set window icon
        try {
            java.net.URL iconURL = getClass().getResource("/icon.png");
            if (iconURL != null) {
                ImageIcon icon = new ImageIcon(iconURL);
                setIconImage(icon.getImage());
            }
        } catch (Exception e) {
            System.err.println("Could not load window icon: " + e.getMessage());
        }
        
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        
        mainPanel.add(new WelcomePanel(this), "welcome");
        mainPanel.add(new NetworkSelectionPanel(this), "network");
        
        add(mainPanel);
    }

    public void showPanel(String panelName) {
        cardLayout.show(mainPanel, panelName);
    }

    public void addPanel(JPanel panel, String name) {
        mainPanel.add(panel, name);
    }

    public void removePanel(String name) {
        for (Component comp : mainPanel.getComponents()) {
            if (comp.getName() != null && comp.getName().equals(name)) {
                mainPanel.remove(comp);
                break;
            }
        }
    }
}
