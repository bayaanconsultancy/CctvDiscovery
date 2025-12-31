package com.cctv.discovery;

import com.cctv.model.Camera;
import com.cctv.util.Logger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class PortScanner {
    // Extended port list for better camera discovery
    private static final int[] PORTS = {
            554, 8554, // Standard RTSP
            80, 8080, 8000, // HTTP
            443, 8443, // HTTPS
            5000, 5001, // Synology NAS cameras
            37777, 37778, // Dahua proprietary
            7447, // Reolink
            9000, 9001 // Custom/Generic
    };
    private static final int TIMEOUT_MS = 500;

    // Dynamic thread pool size based on available processors
    private static final int THREAD_POOL_SIZE = Math.max(4,
            Math.min(Runtime.getRuntime().availableProcessors() * 2, 50));

    public static List<Camera> scan(List<String> ipAddresses, ProgressListener listener) {
        Logger.info("Starting port scan for " + ipAddresses.size() + " IPs with " + THREAD_POOL_SIZE + " threads");
        Set<Camera> cameras = Collections.synchronizedSet(new HashSet<>());
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        final java.util.concurrent.atomic.AtomicInteger completed = new java.util.concurrent.atomic.AtomicInteger(0);

        // Add shutdown hook for graceful cleanup
        final Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Shutdown detected, stopping port scanner...");
            executor.shutdownNow();
        }));

        // Total tasks is number of IPs
        int totalTasks = ipAddresses.size();

        for (String ip : ipAddresses) {
            executor.submit(() -> {
                try {
                    // Update progress at start of IP scan? No, better to update on completion to
                    // show progress bar moving
                    Camera camera = null;
                    List<Integer> rtspPorts = new ArrayList<>();
                    for (int port : PORTS) {
                        if (isPortOpen(ip, port)) {
                            if (camera == null) {
                                camera = new Camera(ip);
                            }
                            if (port == 554 || port == 8554) {
                                rtspPorts.add(port);
                            } else if (port == 80 || port == 8080) {
                                camera.setOnvifServiceUrl("http://" + ip + ":" + port + "/onvif/device_service");
                            }
                            Logger.info("Port scan found: " + ip + ":" + port);
                        }
                    }
                    if (camera != null) {
                        camera.setOpenRtspPorts(rtspPorts);
                        cameras.add(camera);
                    }
                } catch (Exception e) {
                    Logger.error("Error scanning " + ip, e);
                } finally {
                    int current = completed.incrementAndGet();
                    if (listener != null) {
                        listener.onProgress(ip, current, totalTasks, "Scanned " + ip);
                    }
                }
            });
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
                Logger.warn("Port scan timeout reached, forcing shutdown");
                executor.shutdownNow();
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    Logger.error("Port scanner failed to terminate");
                }
            }
        } catch (InterruptedException e) {
            Logger.error("Port scan interrupted", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (listener != null) {
            listener.onComplete();
        }

        Logger.info("Port scan completed. Found " + cameras.size() + " cameras");
        return new ArrayList<>(cameras);
    }

    // Legacy method for backward compatibility if needed, though we should update
    // callers
    public static List<Camera> scan(List<String> ipAddresses) {
        return scan(ipAddresses, null);
    }

    private static boolean isPortOpen(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
