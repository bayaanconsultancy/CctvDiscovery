package com.cctv.discovery;

import com.cctv.model.Camera;
import com.cctv.model.StreamInfo;
import com.cctv.probe.StreamProbe;
import com.cctv.util.Logger;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class RtspUrlGuesser {
    
    private static final String[][] BUILTIN_PATTERNS = {
        // Hikvision (multiple firmware versions)
        {"/Streaming/Channels/101", "/Streaming/Channels/102"},
        {"/Streaming/Channels/1", "/Streaming/Channels/2"},
        {"/Streaming/Channels/1/0", "/Streaming/Channels/1/1"},
        {"/ISAPI/Streaming/channels/101", "/ISAPI/Streaming/channels/102"},
        {"/ISAPI/Streaming/channels/1", "/ISAPI/Streaming/channels/2"},
        {"/h264/ch1/main/av_stream", "/h264/ch1/sub/av_stream"},
        
        // Dahua
        {"/cam/realmonitor?channel=1&subtype=0", "/cam/realmonitor?channel=1&subtype=1"},
        
        // TP-Link, Tapo
        {"/stream1", "/stream2"},
        
        // Axis
        {"/axis-media/media.amp?videocodec=h264", "/axis-media/media.amp?videocodec=h264&resolution=640x360"},
        
        // Foscam
        {"/videoMain", "/videoSub"},
        
        // Generic patterns
        {"/live/channel0", "/live/channel1"},
        {"/live/main", "/live/sub"},
        {"/stream", "/substream"},
        {"/ch0", "/ch1"},
        {"/0", "/1"},
        {"/video", "/video2"},
        {"/h264", "/h264_sub"}
    };
    
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static ExecutorService executor;
    
    private static synchronized ExecutorService getExecutor() {
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        }
        return executor;
    }
    
    public static void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
    }
    
    public static void processDevices(List<Camera> cameras) {
        List<Future<Void>> futures = new ArrayList<>();
        for (Camera camera : cameras) {
            futures.add(getExecutor().submit(() -> {
                tryGuessUrls(camera);
                return null;
            }));
        }
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                Logger.error("Device processing failed", e);
            }
        }
    }
    
    public static boolean tryGuessUrls(Camera camera) {
        if (camera.getUsername() == null || camera.getPassword() == null) {
            Logger.info("Skipping RTSP URL guessing - no credentials");
            return false;
        }
        
        List<Integer> ports = camera.getOpenRtspPorts();
        if (ports.isEmpty()) {
            Logger.info("Skipping RTSP URL guessing - no RTSP ports open");
            return false;
        }
        
        String[][] urlPatterns = loadPatterns();
        
        Logger.info("=== RTSP URL Guessing for " + camera.getIpAddress() + " ===");
        Logger.info("Testing " + urlPatterns.length + " patterns on ports: " + ports);
        
        for (String[] pattern : urlPatterns) {
            for (int port : ports) {
                String mainUrl = buildRtspUrl(camera, pattern[0], port);
                Logger.info("Testing: " + mainUrl);
                
                TestResult mainResult = testRtspUrl(mainUrl);
                if (mainResult.success) {
                    Logger.info("SUCCESS: Main stream found");
                    
                    String subUrl = null;
                    if (pattern.length > 1) {
                        subUrl = buildRtspUrl(camera, pattern[1], port);
                        Logger.info("Testing sub: " + subUrl);
                        if (testRtspUrl(subUrl).success) {
                            Logger.info("SUCCESS: Sub stream found");
                        } else {
                            subUrl = null;
                        }
                    }
                    
                    StreamInfo main = new StreamInfo();
                    main.setRtspUrl(mainUrl);
                    camera.setMainStream(main);
                    StreamProbe.probe(main);
                    
                    if (subUrl != null) {
                        StreamInfo sub = new StreamInfo();
                        sub.setRtspUrl(subUrl);
                        camera.setSubStream(sub);
                        StreamProbe.probe(sub);
                    }
                    return true;
                } else if (mainResult.authFailed) {
                    Logger.info("Auth failed for " + camera.getIpAddress());
                    camera.setAuthFailed(true);
                    camera.setErrorMessage("RTSP Auth Failed: Invalid credentials");
                    return false;
                }
            }
        }
        
        Logger.info("No working RTSP URLs found for " + camera.getIpAddress());
        camera.setErrorMessage("RTSP URL guessing failed");
        return false;
    }
    
    private static String[][] loadPatterns() {
        List<String[]> patterns = new ArrayList<>();
        
        File configFile;
        try {
            String exePath = System.getProperty("launch4j.exepath");
            if (exePath != null) {
                configFile = new File(new File(exePath).getParent(), "rtsp-patterns.txt");
            } else {
                configFile = new File("rtsp-patterns.txt");
            }
        } catch (Exception e) {
            configFile = new File("rtsp-patterns.txt");
        }
        if (configFile.exists()) {
            Logger.info("Loading RTSP patterns from exe directory: " + configFile.getAbsolutePath());
            try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
                String line;
                int count = 0;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    
                    String[] parts = line.split(",");
                    if (parts.length >= 1) {
                        String[] pattern = new String[parts.length];
                        for (int i = 0; i < parts.length; i++) {
                            pattern[i] = parts[i].trim();
                        }
                        patterns.add(pattern);
                        count++;
                    }
                }
                Logger.info("Loaded " + count + " RTSP patterns from file");
            } catch (Exception e) {
                Logger.error("Failed to load RTSP patterns from file, using built-in patterns", e);
                for (String[] pattern : BUILTIN_PATTERNS) {
                    patterns.add(pattern);
                }
            }
        } else {
            Logger.info("No rtsp-patterns.txt found, using built-in patterns");
            for (String[] pattern : BUILTIN_PATTERNS) {
                patterns.add(pattern);
            }
        }
        
        return patterns.toArray(new String[0][]);
    }
    
    private static String buildRtspUrl(Camera camera, String path) {
        return buildRtspUrl(camera, path, 554);
    }
    
    private static String buildRtspUrl(Camera camera, String path, int port) {
        return String.format("rtsp://%s:%s@%s:%d%s", 
            camera.getUsername(), camera.getPassword(), camera.getIpAddress(), port, path);
    }
    
    public static TestResult testRtspUrl(String url) {
        FFmpegFrameGrabber grabber = null;
        try {
            grabber = new FFmpegFrameGrabber(url);
            grabber.setOption("rtsp_transport", "tcp");
            grabber.setOption("stimeout", "1000000"); // 1 second
            grabber.setOption("timeout", "1000000");
            grabber.setOption("reconnect", "0");
            grabber.setOption("reconnect_at_eof", "0");
            grabber.setOption("reconnect_streamed", "0");
            grabber.setOption("loglevel", "quiet"); // Suppress FFmpeg noise
            grabber.setTimeout(1000);
            grabber.start();
            
            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            
            grabber.stop();
            return new TestResult(width > 0 && height > 0, false);
        } catch (Exception e) {
            String msg = e.getMessage();
            boolean authFailed = msg != null && (msg.contains("401") || msg.contains("Unauthorized") || msg.contains("403"));
            return new TestResult(false, authFailed);
        } finally {
            if (grabber != null) {
                try {
                    grabber.release();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }
    
    public static class TestResult {
        boolean success;
        boolean authFailed;
        
        TestResult(boolean success, boolean authFailed) {
            this.success = success;
            this.authFailed = authFailed;
        }
    }
    
    private static class PatternResult {
        boolean success;
        String mainUrl;
        String subUrl;
        boolean authFailed;
        
        PatternResult(boolean success, String mainUrl, String subUrl, boolean authFailed) {
            this.success = success;
            this.mainUrl = mainUrl;
            this.subUrl = subUrl;
            this.authFailed = authFailed;
        }
    }
}
