package com.cctv.api;

import java.util.*;
import java.util.concurrent.*;
import java.net.NetworkInterface;
import java.net.InetAddress;

/**
 * Simple API test without FFmpeg dependencies
 */
public class SimpleApiTest {
    
    public static void main(String[] args) {
        // Add shutdown hook for cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down...");
        }));
        
        if (args.length == 0 || Arrays.asList(args).contains("--help")) {
            printHelp();
            return;
        }
        
        try {
            Config config = parseArgs(args);
            runDiscovery(config);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Use --help for usage information");
            System.exit(1);
        }
    }
    
    private static Config parseArgs(String[] args) throws Exception {
        Config config = new Config();
        
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Invalid argument: " + arg + ". Use --key=value format");
            }
            
            String[] parts = arg.substring(2).split("=", 2);
            String key = parts[0];
            String value = parts.length > 1 ? parts[1] : "";
            
            switch (key) {
                case "interface":
                    if (value.isEmpty()) throw new IllegalArgumentException("--interface requires a value");
                    config.interfaces.add(value);
                    break;
                case "ip":
                    if (value.isEmpty()) throw new IllegalArgumentException("--ip requires a value");
                    config.ipRanges.add(value);
                    break;
                case "cred":
                    if (value.isEmpty()) throw new IllegalArgumentException("--cred requires a value");
                    config.globalCredentials.add(parseCredential(value));
                    break;
                case "interface-cred":
                    if (value.isEmpty()) throw new IllegalArgumentException("--interface-cred requires a value");
                    parseInterfaceCredential(config, value);
                    break;
                case "ip-cred":
                    if (value.isEmpty()) throw new IllegalArgumentException("--ip-cred requires a value");
                    parseIpCredential(config, value);
                    break;
                case "stream-analysis":
                    config.enableStreamAnalysis = true;
                    break;
                case "test":
                    config.testMode = true;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown argument: --" + key);
            }
        }
        
        if (config.interfaces.isEmpty() && config.ipRanges.isEmpty()) {
            throw new IllegalArgumentException("Must specify at least one --interface or --ip");
        }
        
        return config;
    }
    
    private static Credential parseCredential(String value) throws Exception {
        String[] parts = value.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Credential must be in format username:password");
        }
        return new Credential(parts[0], parts[1]);
    }
    
    private static void parseInterfaceCredential(Config config, String value) throws Exception {
        String[] parts = value.split("=", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Interface credential must be in format interface=username:password");
        }
        config.interfaceCredentials.put(parts[0], parseCredential(parts[1]));
    }
    
    private static void parseIpCredential(Config config, String value) throws Exception {
        String[] parts = value.split("=", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("IP credential must be in format ip_range=username:password");
        }
        config.ipCredentials.put(parts[0], parseCredential(parts[1]));
    }
    
    private static void runDiscovery(Config config) throws Exception {
        System.out.println("CCTV Discovery API Test (No Stream Analysis)");
        System.out.println("Interfaces: " + config.interfaces);
        System.out.println("IP Ranges: " + config.ipRanges);
        System.out.println("Global Credentials: " + config.globalCredentials.size());
        System.out.println("Interface Credentials: " + config.interfaceCredentials.size());
        System.out.println("IP Credentials: " + config.ipCredentials.size());
        
        if (config.testMode) {
            System.out.println("\nTEST MODE - No actual discovery");
            
            // Test interface resolution
            for (String iface : config.interfaces) {
                try {
                    String ipRange = resolveInterface(iface);
                    List<Credential> creds = getCredentialsForInterface(config, iface);
                    System.out.println("Interface " + iface + " -> " + ipRange + " with " + creds.size() + " credentials");
                } catch (Exception e) {
                    System.out.println("Interface " + iface + " -> ERROR: " + e.getMessage());
                }
            }
            
            // Test IP ranges
            for (String ipRange : config.ipRanges) {
                List<Credential> creds = getCredentialsForIpRange(config, ipRange);
                System.out.println("IP Range " + ipRange + " with " + creds.size() + " credentials");
            }
            
            // Test 4: Validation Error Handling
            System.out.println("\n--- Test 4: Validation Error Handling ---");
            try {
                CctvDiscovery.builder()
                    .threadCount(-1) // Invalid
                    .build();
                System.err.println("FAILED: Validation check missed invalid thread count");
            } catch (IllegalArgumentException e) {
                System.out.println("SUCCESS: Caught expected validation error: " + e.getMessage());
            }

            try {
                CctvDiscovery.builder()
                    .ipRange("") // Invalid
                    .build();
                System.err.println("FAILED: Validation check missed empty IP range");
            } catch (IllegalStateException e) {
                System.out.println("SUCCESS: Caught expected validation error: " + e.getMessage());
            }
            
            return;
        }
        
        List<DiscoveryResult> results = new ArrayList<>();
        
        // Process interfaces
        for (String iface : config.interfaces) {
            String ipRange = resolveInterface(iface);
            List<Credential> creds = getCredentialsForInterface(config, iface);
            results.addAll(runDiscoveryForRange(ipRange, creds, config));
        }
        
        // Process IP ranges
        for (String ipRange : config.ipRanges) {
            List<Credential> creds = getCredentialsForIpRange(config, ipRange);
            results.addAll(runDiscoveryForRange(ipRange, creds, config));
        }
        
        printSummary(results);
    }
    
    private static String resolveInterface(String iface) throws Exception {
        if (iface.equals("all")) {
            return "0.0.0.0-255.255.255.255";
        }
        
        // Check if it's a single IP address
        if (iface.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            // Find interface that contains this IP
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface ni : interfaces) {
                List<InetAddress> addresses = Collections.list(ni.getInetAddresses());
                for (InetAddress addr : addresses) {
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                        String networkIp = addr.getHostAddress();
                        String[] parts = networkIp.split("\\.");
                        String network = parts[0] + "." + parts[1] + "." + parts[2] + ".";
                        if (iface.startsWith(network)) {
                            return iface + "-" + iface;  // Single IP range
                        }
                    }
                }
            }
            // If no matching interface found, treat as single IP
            return iface + "-" + iface;
        }
        
        // Check if it's already an IP range
        if (iface.contains("-")) {
            return iface;
        }
        
        // Try by index
        try {
            int index = Integer.parseInt(iface);
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            if (index >= 0 && index < interfaces.size()) {
                NetworkInterface ni = interfaces.get(index);
                return getNetworkRange(ni);
            }
        } catch (NumberFormatException e) {
            // Try by name
            NetworkInterface ni = NetworkInterface.getByName(iface);
            if (ni != null) {
                return getNetworkRange(ni);
            }
        }
        
        // Assume it's already an IP range
        return iface;
    }
    
    private static String getNetworkRange(NetworkInterface ni) {
        List<InetAddress> addresses = Collections.list(ni.getInetAddresses());
        for (InetAddress addr : addresses) {
            if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                String ip = addr.getHostAddress();
                String[] parts = ip.split("\\.");
                return parts[0] + "." + parts[1] + "." + parts[2] + ".1-" + 
                       parts[0] + "." + parts[1] + "." + parts[2] + ".254";
            }
        }
        return "192.168.1.1-192.168.1.254";
    }
    
    private static List<Credential> getCredentialsForInterface(Config config, String iface) {
        List<Credential> creds = new ArrayList<>();
        if (config.interfaceCredentials.containsKey(iface)) {
            creds.add(config.interfaceCredentials.get(iface));
        }
        creds.addAll(config.globalCredentials);
        return creds.isEmpty() ? Arrays.asList(new Credential("admin", "admin")) : creds;
    }
    
    private static List<Credential> getCredentialsForIpRange(Config config, String ipRange) {
        List<Credential> creds = new ArrayList<>();
        if (config.ipCredentials.containsKey(ipRange)) {
            creds.add(config.ipCredentials.get(ipRange));
        }
        creds.addAll(config.globalCredentials);
        return creds.isEmpty() ? Arrays.asList(new Credential("admin", "admin")) : creds;
    }
    
    private static List<DiscoveryResult> runDiscoveryForRange(String ipRange, List<Credential> credentials, Config config) throws Exception {
        List<DiscoveryResult> results = new ArrayList<>();
        
        for (Credential cred : credentials) {
            System.out.println("\nScanning " + ipRange + " with " + cred.getUsername() + ":" + cred.getPassword());
            
            try {
                // Create a timeout wrapper
                CompletableFuture<DiscoveryResult> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return CctvDiscovery.builder()
                            .ipRange(ipRange)
                            .credentials(cred.getUsername(), cred.getPassword())
                            .enableOnvif(true)
                            .enablePortScan(false)
                            .enableRtspGuessing(config.enableStreamAnalysis)
                            .enableNvrDetection(false)
                            .timeout(config.enableStreamAnalysis ? 30 : 5)
                            .threadCount(1)
                            .onProgress(progress -> 
                                System.out.printf("[%s] %d/%d - %s%n", 
                                    progress.getPhase(), 
                                    progress.getCurrent(), 
                                    progress.getTotal(), 
                                    progress.getMessage()))
                            .build()
                            .discover();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                
                // Wait max 15 seconds
                DiscoveryResult result = future.get(15, java.util.concurrent.TimeUnit.SECONDS);
                results.add(result);
                System.out.println("Completed scan for " + ipRange + " - Found " + result.getSuccessfulDevices() + " devices");
                
            } catch (java.util.concurrent.TimeoutException e) {
                System.err.println("Timeout scanning " + ipRange + " after 15 seconds");
            } catch (Exception e) {
                System.err.println("Failed to scan " + ipRange + ": " + e.getMessage());
            }
        }
        

        
        return results;
    }
    
    private static void printSummary(List<DiscoveryResult> results) {
        System.out.println("\nSummary");
        int totalDevices = results.stream().mapToInt(DiscoveryResult::getTotalDevices).sum();
        int successful = results.stream().mapToInt(DiscoveryResult::getSuccessfulDevices).sum();
        int failed = results.stream().mapToInt(DiscoveryResult::getFailedDevices).sum();
        long totalTime = results.stream().mapToLong(DiscoveryResult::getDurationMs).sum();
        
        System.out.println("Total devices: " + totalDevices);
        System.out.println("Successful: " + successful);
        System.out.println("Failed: " + failed);
        System.out.println("Total duration: " + totalTime + "ms");
        
        System.out.println("\nJSON Output");
        for (DiscoveryResult result : results) {
            System.out.println(result.toJson());
        }
    }
    
    private static void printHelp() {
        System.out.println("CCTV Discovery API Test");
        System.out.println();
        System.out.println("Usage: java -cp cctv-discovery-api.jar com.cctv.api.SimpleApiTest [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --interface=<value>     Network interface (index, name, 'all', or IP range)");
        System.out.println("  --ip=<range>           IP range (e.g., 192.168.1.1-192.168.1.50)");
        System.out.println("  --cred=<user:pass>     Global credentials for all ranges");
        System.out.println("  --interface-cred=<iface=user:pass>  Credentials for specific interface");
        System.out.println("  --ip-cred=<range=user:pass>         Credentials for specific IP range");
        System.out.println("  --stream-analysis      Enable FFmpeg stream analysis (resolution, codec, bitrate, FPS)");
        System.out.println("  --test                 Test mode - parse arguments without running discovery");
        System.out.println("  --help                 Show this help");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Scan interface 0 with global credentials");
        System.out.println("  java -cp api.jar SimpleApiTest --interface=0 --cred=admin:admin");
        System.out.println();
        System.out.println("  # Scan multiple IP ranges with different credentials");
        System.out.println("  java -cp api.jar SimpleApiTest --ip=192.168.1.1-50 --ip=10.0.0.1-20 --cred=admin:12345");
        System.out.println();
        System.out.println("  # Scan all interfaces with specific credentials per interface");
        System.out.println("  java -cp api.jar SimpleApiTest --interface=eth0 --interface-cred=eth0=admin:pass1 --interface=wlan0 --interface-cred=wlan0=user:pass2");
        System.out.println();
        System.out.println("  # Enable stream analysis with FFmpeg");
        System.out.println("  java -cp api.jar SimpleApiTest --ip=192.168.0.103 --cred=admin:pass --stream-analysis");
        System.out.println();
        System.out.println("  # Mixed global and specific credentials");
        System.out.println("  java -cp api.jar SimpleApiTest --ip=192.168.1.1-50 --ip=10.0.0.1-20 --cred=admin:admin --ip-cred=10.0.0.1-20=root:password");
    }
    
    private static class Config {
        List<String> interfaces = new ArrayList<>();
        List<String> ipRanges = new ArrayList<>();
        List<Credential> globalCredentials = new ArrayList<>();
        Map<String, Credential> interfaceCredentials = new HashMap<>();
        Map<String, Credential> ipCredentials = new HashMap<>();
        boolean testMode = false;
        boolean enableStreamAnalysis = false;
    }
}