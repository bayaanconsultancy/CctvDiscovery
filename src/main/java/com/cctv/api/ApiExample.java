package com.cctv.api;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Example usage of CCTV Discovery API
 */
public class ApiExample {

    public static void main(String[] args) {
        // Simple synchronous discovery
        simpleDiscovery();

        // Async discovery with progress
        asyncDiscoveryWithProgress();

        // Advanced configuration
        advancedDiscovery();
    }

    private static void simpleDiscovery() {
        System.out.println("Simple Discovery");

        DiscoveryResult result = CctvDiscovery.builder()
                .ipRange("192.168.1.100-192.168.1.110")
                .credentials("admin", "admin123")
                .credentials("admin", "admin@123")
                .build()
                .discover();

        System.out.println("Found " + result.getSuccessfulDevices() + " cameras");
        System.out.println("JSON: " + result.toJson());
    }

    private static void asyncDiscoveryWithProgress() {
        System.out.println("\\nAsync Discovery with Progress");

        CompletableFuture<DiscoveryResult> future = CctvDiscovery.builder()
                .ipRange("192.168.1.1-192.168.1.50")
                .credentials("admin", "password")
                .credentials("root", "root")
                .onProgress(progress -> {
                    System.out.printf("[%s] %.1f%% - %s\\n",
                            progress.getPhase(),
                            progress.getPercentage(),
                            progress.getMessage());
                })
                .build()
                .discoverAsync();

        future.thenAccept(result -> {
            System.out.println("Discovery completed!");
            System.out.println("Cameras found: " + result.getSuccessfulDevices());
            System.out.println("Errors: " + result.getFailedDevices());
        });

        // Wait for completion
        future.join();
    }

    private static void advancedDiscovery() {
        System.out.println("\\nAdvanced Discovery");

        DiscoveryResult result = CctvDiscovery.builder()
                .ipRange("10.0.0.1-10.0.0.100")
                .credentials(Arrays.asList(
                        new Credential("admin", "admin"),
                        new Credential("admin", "12345"),
                        new Credential("root", "password"),
                        new Credential("user", "user")))
                .enableOnvif(true)
                .enablePortScan(true)
                .enableRtspGuessing(true)
                .enableNvrDetection(true)
                .threadCount(16)
                .timeout(60)
                .build()
                .discover();

        System.out.println("Advanced discovery results:");
        System.out.println("Duration: " + result.getDurationMs() + "ms");
        System.out.println("Total devices: " + result.getTotalDevices());

        for (CameraResult camera : result.getCameras()) {
            System.out.printf("Camera: %s [%s %s] - %s\\n",
                    camera.getIpAddress(),
                    camera.getManufacturer(),
                    camera.getModel(),
                    camera.getMainStream() != null ? camera.getMainStream().getRtspUrl() : "No stream");
        }
    }
}