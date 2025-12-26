package com.cctv.discovery;

import com.cctv.model.Camera;
import com.cctv.util.Logger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class PortScanner {
    private static final int[] PORTS = {554, 8554, 80, 8080};
    private static final int TIMEOUT_MS = 500;
    private static final int THREAD_POOL_SIZE = 50;

    public static List<Camera> scan(List<String> ipAddresses) {
        return scan(ipAddresses, null);
    }
    
    public static List<Camera> scan(List<String> ipAddresses, com.cctv.ui.ProgressPanel progressPanel) {
        Logger.info("Starting port scan for " + ipAddresses.size() + " IPs");
        Set<Camera> cameras = Collections.synchronizedSet(new HashSet<>());
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        final java.util.concurrent.atomic.AtomicInteger completed = new java.util.concurrent.atomic.AtomicInteger(0);
        
        if (progressPanel != null) {
            progressPanel.setProgress(0, ipAddresses.size());
        }
        
        for (String ip : ipAddresses) {
            executor.submit(() -> {
                try {
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
                } finally {
                    if (progressPanel != null) {
                        int current = completed.incrementAndGet();
                        progressPanel.setProgress(current, ipAddresses.size());
                    }
                }
            });
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Logger.error("Port scan interrupted", e);
        }
        
        Logger.info("Port scan completed. Found " + cameras.size() + " cameras");
        return new ArrayList<>(cameras);
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
