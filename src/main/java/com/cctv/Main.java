package com.cctv;

import com.cctv.ui.WizardFrame;
import com.cctv.util.Logger;
import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        Logger.info("CCTV Discovery Tool started");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Shutting down CCTV Discovery Tool...");
            com.cctv.discovery.RtspUrlGuesser.shutdown();
        }));
        
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                Logger.error("Failed to set look and feel", e);
            }
            
            WizardFrame frame = new WizardFrame();
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    com.cctv.discovery.RtspUrlGuesser.shutdown();
                    System.exit(0);
                }
            });
            frame.setVisible(true);
        });
    }
}
