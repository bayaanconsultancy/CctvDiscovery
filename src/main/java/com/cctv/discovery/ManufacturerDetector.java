package com.cctv.discovery;

import com.cctv.model.Camera;
import com.cctv.util.Logger;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Detects camera manufacturer to optimize RTSP pattern testing.
 * Uses multiple detection methods: ONVIF, HTTP headers, and MAC address OUI.
 */
public class ManufacturerDetector {
    
    // MAC Address OUI (Organizationally Unique Identifier) to Manufacturer mapping
    private static final Map<String, String> MAC_OUI_MAP = new HashMap<String, String>() {{
        // Hikvision
        put("44:19:B6", "Hikvision");
        put("4C:BD:8F", "Hikvision");
        put("BC:AD:28", "Hikvision");
        put("C0:56:E3", "Hikvision");
        
        // Dahua
        put("00:12:16", "Dahua");
        put("A4:14:37", "Dahua");
        put("08:57:00", "Dahua");
        
        // Axis
        put("00:40:8C", "Axis");
        put("AC:CC:8E", "Axis");
        put("B8:A4:4F", "Axis");
        
        // TP-Link
        put("50:C7:BF", "TP-Link");
        put("F4:F2:6D", "TP-Link");
        put("98:DA:C4", "TP-Link");
        
        // Foscam
        put("C4:F0:81", "Foscam");
        put("00:62:6E", "Foscam");
        
        // Reolink
        put("EC:71:DB", "Reolink");
        
        // Amcrest
        put("9C:8E:CD", "Amcrest");
        
        // Ubiquiti
        put("FC:EC:DA", "Ubiquiti");
        put("74:83:C2", "Ubiquiti");
        
        // CP Plus (uses Dahua OEM)
        put("00:18:61", "CP Plus");
        put("B4:A5:EF", "CP Plus");
    }};
    
    /**
     * Detect camera manufacturer using multiple methods.
     * Priority: ONVIF > HTTP Headers > MAC Address
     */
    public static String detect(Camera camera) {
        String manufacturer = null;
        
        // Method 1: Check ONVIF manufacturer field (most reliable)
        if (camera.getManufacturer() != null && !camera.getManufacturer().trim().isEmpty()) {
            manufacturer = normalizeManufacturer(camera.getManufacturer());
            Logger.info("Manufacturer detected from ONVIF: " + manufacturer);
            return manufacturer;
        }
        
        // Method 2: Check HTTP Server header
        manufacturer = detectFromHttpHeaders(camera.getIpAddress());
        if (manufacturer != null) {
            Logger.info("Manufacturer detected from HTTP headers: " + manufacturer);
            return manufacturer;
        }
        
        // Method 3: Check MAC address OUI (requires ARP or network access)
        manufacturer = detectFromMacAddress(camera.getIpAddress());
        if (manufacturer != null) {
            Logger.info("Manufacturer detected from MAC address: " + manufacturer);
            return manufacturer;
        }
        
        Logger.info("Manufacturer detection failed for " + camera.getIpAddress() + ", using generic patterns");
        return "Generic";
    }
    
    /**
     * Detect manufacturer from HTTP Server header.
     */
    private static String detectFromHttpHeaders(String ipAddress) {
        String[] ports = {"80", "8080", "443", "8000"};
        
        for (String port : ports) {
            try {
                URL url = new URL("http://" + ipAddress + ":" + port);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.connect();
                
                String server = conn.getHeaderField("Server");
                String wwwAuth = conn.getHeaderField("WWW-Authenticate");
                
                conn.disconnect();
                
                // Check Server header
                if (server != null) {
                    if (server.toLowerCase().contains("hikvision")) return "Hikvision";
                    if (server.toLowerCase().contains("dahua")) return "Dahua";
                    if (server.toLowerCase().contains("cp plus") || server.toLowerCase().contains("cpplus")) return "CP Plus";
                    if (server.toLowerCase().contains("axis")) return "Axis";
                    if (server.toLowerCase().contains("foscam")) return "Foscam";
                }
                
                // Check WWW-Authenticate header
                if (wwwAuth != null) {
                    if (wwwAuth.toLowerCase().contains("hikvision")) return "Hikvision";
                    if (wwwAuth.toLowerCase().contains("dahua")) return "Dahua";
                    if (wwwAuth.toLowerCase().contains("cp plus") || wwwAuth.toLowerCase().contains("cpplus")) return "CP Plus";
                }
                
            } catch (Exception e) {
                // Try next port
            }
        }
        
        return null;
    }
    
    /**
     * Detect manufacturer from MAC address OUI.
     * Note: This requires ARP table access which may not always be available.
     */
    private static String detectFromMacAddress(String ipAddress) {
        try {
            String mac = getMacAddressFromArp(ipAddress);
            if (mac != null && mac.length() >= 8) {
                String oui = mac.substring(0, 8).toUpperCase();
                if (MAC_OUI_MAP.containsKey(oui)) {
                    return MAC_OUI_MAP.get(oui);
                }
            }
        } catch (Exception e) {
            Logger.debug("Failed to get MAC address for " + ipAddress);
        }
        
        return null;
    }
    
    /**
     * Get MAC address from ARP table (Windows).
     */
    private static String getMacAddressFromArp(String ipAddress) {
        try {
            Process process = Runtime.getRuntime().exec("arp -a " + ipAddress);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains(ipAddress)) {
                    // Parse MAC address from ARP output
                    String[] parts = line.trim().split("\\s+");
                    for (String part : parts) {
                        if (part.matches("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})")) {
                            return part.replace("-", ":").toUpperCase();
                        }
                    }
                }
            }
            
            reader.close();
            process.waitFor();
        } catch (Exception e) {
            // ARP lookup failed
        }
        
        return null;
    }
    
    /**
     * Normalize manufacturer name to standard format.
     */
    private static String normalizeManufacturer(String manufacturer) {
        String lower = manufacturer.toLowerCase();
        
        if (lower.contains("hikvision") || lower.contains("hik")) return "Hikvision";
        if (lower.contains("dahua")) return "Dahua";
        if (lower.contains("cp plus") || lower.contains("cpplus") || lower.contains("cp-plus")) return "CP Plus";
        if (lower.contains("axis")) return "Axis";
        if (lower.contains("tp-link") || lower.contains("tapo")) return "TP-Link";
        if (lower.contains("foscam")) return "Foscam";
        if (lower.contains("reolink")) return "Reolink";
        if (lower.contains("amcrest")) return "Amcrest";
        if (lower.contains("ubiquiti") || lower.contains("unifi")) return "Ubiquiti";
        if (lower.contains("d-link")) return "D-Link";
        if (lower.contains("vivotek")) return "Vivotek";
        if (lower.contains("panasonic")) return "Panasonic";
        if (lower.contains("sony")) return "Sony";
        if (lower.contains("bosch")) return "Bosch";
        if (lower.contains("pelco")) return "Pelco";
        
        return manufacturer;
    }
}
