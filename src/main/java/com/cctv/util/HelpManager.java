package com.cctv.util;

import java.awt.Desktop;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HelpManager {
    private static final String MANUAL_RESOURCE = "/user-manual.html";
    private static final String ASSETS_FOLDER = "/manual-assets/";
    
    public static void openUserManual() {
        try {
            File tempManual = extractManualToTemp();
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(tempManual.toURI());
                Logger.info("Opened user manual in browser: " + tempManual.getAbsolutePath());
            } else {
                Logger.error("Desktop not supported - cannot open browser");
            }
        } catch (Exception e) {
            Logger.error("Failed to open user manual", e);
        }
    }
    
    private static File extractManualToTemp() throws IOException {
        // Create temp directory
        Path tempDir = Files.createTempDirectory("cctv-manual");
        tempDir.toFile().deleteOnExit();
        
        // Extract HTML file
        File manualFile = new File(tempDir.toFile(), "user-manual.html");
        extractResource(MANUAL_RESOURCE, manualFile);
        
        // Extract assets folder
        File assetsDir = new File(tempDir.toFile(), "manual-assets");
        assetsDir.mkdirs();
        assetsDir.deleteOnExit();
        
        // Extract all image assets
        String[] imageFiles = {
            "main-interface.png", "installation-extract.png", "welcome-screen.png",
            "network-selection.png", "network-interface-selection.png", "ip-range-input.png",
            "onvif-discovery.png", "onvif-fallback-portscan.png", "port-scanning.png",
            "credential-setup.png", "authentication-progress.png", "results-overview.png",
            "filtered-results.png", "excel-export.png", "excel-sample.png",
            "error-dialog.png", "custom-patterns.png", "nvr-channels.png", "retry-panel.png"
        };
        
        for (String imageFile : imageFiles) {
            File targetFile = new File(assetsDir, imageFile);
            extractResource(ASSETS_FOLDER + imageFile, targetFile);
            targetFile.deleteOnExit();
        }
        
        manualFile.deleteOnExit();
        return manualFile;
    }
    
    private static void extractResource(String resourcePath, File targetFile) throws IOException {
        try (InputStream is = HelpManager.class.getResourceAsStream(resourcePath);
             FileOutputStream fos = new FileOutputStream(targetFile)) {
            
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
    }
}