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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class RtspUrlGuesser {
    
    // Manufacturer-specific patterns for faster discovery
    private static final String[][] HIKVISION_PATTERNS = {
        {"/Streaming/Channels/101", "/Streaming/Channels/102"},
        {"/Streaming/Channels/1", "/Streaming/Channels/2"},
        {"/ISAPI/Streaming/channels/101", "/ISAPI/Streaming/channels/102"},
        {"/ISAPI/Streaming/channels/1", "/ISAPI/Streaming/channels/2"},
        {"/h264/ch1/main/av_stream", "/h264/ch1/sub/av_stream"},
        {"/Streaming/Channels/1/0", "/Streaming/Channels/1/1"}
    };
    
    private static final String[][] DAHUA_PATTERNS = {
        {"/cam/realmonitor?channel=1&subtype=0", "/cam/realmonitor?channel=1&subtype=1"},
        {"/cam/realmonitor?channel=1&subtype=0&unicast=true&proto=Onvif", "/cam/realmonitor?channel=1&subtype=1&unicast=true&proto=Onvif"},
        {"/live/ch1", "/live/ch2"},
        {"/live/main", "/live/sub"}
    };
    
    private static final String[][] AXIS_PATTERNS = {
        {"/axis-media/media.amp?videocodec=h264", "/axis-media/media.amp?videocodec=h264&resolution=640x360"},
        {"/axis-media/media.amp", "/axis-media/media.amp?resolution=320x240"},
        {"/onvif-media/media.amp", "/onvif-media/media.amp?profile=profile_1_h264"}
    };
    
    private static final String[][] TPLINK_PATTERNS = {
        {"/stream1", "/stream2"},
        {"/live/ch00_0", "/live/ch00_1"},
        {"/11", "/12"},
        {"/onvif1", "/onvif2"}
    };
    
    private static final String[][] FOSCAM_PATTERNS = {
        {"/videoMain", "/videoSub"},
        {"/video.cgi?resolution=32", "/video.cgi?resolution=8"},
        {"/h264_ulaw.sdp", "/h264_ulaw_sub.sdp"}
    };
    
    private static final String[][] REOLINK_PATTERNS = {
        {"/h264Preview_01_main", "/h264Preview_01_sub"},
        {"/bcs/channel0_main.bcs?channel=0&stream=0", "/bcs/channel0_sub.bcs?channel=0&stream=1"}
    };
    
    private static final String[][] CPPLUS_PATTERNS = {
        // CP Plus uses Dahua OEM firmware
        {"/cam/realmonitor?channel=1&subtype=0", "/cam/realmonitor?channel=1&subtype=1"},
        {"/live/ch1", "/live/ch2"},
        {"/live/main", "/live/sub"},
        {"/streaming/channels/1", "/streaming/channels/2"}
    };
    
    private static final String[][] GENERIC_PATTERNS = {
        {"/live/channel0", "/live/channel1"},
        {"/live/main", "/live/sub"},
        {"/stream", "/substream"},
        {"/stream1", "/stream2"},
        {"/ch0", "/ch1"},
        {"/0", "/1"},
        {"/video", "/video2"},
        {"/h264", "/h264_sub"},
        {"/main", "/sub"},
        {"/streaming/channels/1", "/streaming/channels/2"}
    };
    
    // Pattern cache: manufacturer:model -> successful pattern
    private static final Map<String, String[]> patternCache = new ConcurrentHashMap<>();
    
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
                Thread.currentThread().interrupt();
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
        
        // Detect manufacturer for intelligent pattern selection
        String manufacturer = ManufacturerDetector.detect(camera);
        String cacheKey = manufacturer + ":" + (camera.getModel() != null ? camera.getModel() : "");
        
        Logger.info("RTSP URL Guessing for " + camera.getIpAddress() + " (Manufacturer: " + manufacturer + ")");
        
        // Try cached pattern first
        if (patternCache.containsKey(cacheKey)) {
            Logger.info("Trying cached pattern for " + cacheKey);
            String[] cachedPattern = patternCache.get(cacheKey);
            if (tryPattern(camera, cachedPattern, ports)) {
                Logger.info("SUCCESS: Cached pattern worked!");
                return true;
            }
        }
        
        // Get manufacturer-specific patterns
        String[][] patterns = getPatternsForManufacturer(manufacturer);
        Logger.info("Testing " + patterns.length + " patterns for " + manufacturer + " on ports: " + ports);
        
        // Try parallel pattern testing for faster discovery
        if (tryPatternsParallel(camera, patterns, ports, cacheKey)) {
            return true;
        }
        
        // If manufacturer-specific patterns failed, try generic patterns
        if (!manufacturer.equals("Generic")) {
            Logger.info("Manufacturer patterns failed, trying generic patterns");
            if (tryPatternsParallel(camera, GENERIC_PATTERNS, ports, cacheKey)) {
                return true;
            }
        }
        
        Logger.info("No working RTSP URLs found for " + camera.getIpAddress());
        camera.setErrorMessage("RTSP URL guessing failed");
        return false;
    }
    
    /**
     * Get patterns specific to manufacturer for faster discovery.
     */
    private static String[][] getPatternsForManufacturer(String manufacturer) {
        switch (manufacturer) {
            case "Hikvision":
                return HIKVISION_PATTERNS;
            case "Dahua":
            case "Amcrest":  // Amcrest uses Dahua patterns
                return DAHUA_PATTERNS;
            case "Axis":
                return AXIS_PATTERNS;
            case "TP-Link":
                return TPLINK_PATTERNS;
            case "Foscam":
                return FOSCAM_PATTERNS;
            case "Reolink":
                return REOLINK_PATTERNS;
            case "CP Plus":
                return CPPLUS_PATTERNS;
            default:
                return GENERIC_PATTERNS;
        }
    }
    
    /**
     * Try patterns in parallel for 5-10x faster discovery.
     */
    private static boolean tryPatternsParallel(Camera camera, String[][] patterns, List<Integer> ports, String cacheKey) {
        ExecutorService parallelExecutor = Executors.newFixedThreadPool(10);
        List<Future<PatternTestResult>> futures = new ArrayList<>();
        
        // Submit all pattern tests concurrently
        for (String[] pattern : patterns) {
            for (int port : ports) {
                final String[] currentPattern = pattern;
                final int currentPort = port;
                
                futures.add(parallelExecutor.submit(() -> {
                    String mainUrl = buildRtspUrl(camera, currentPattern[0], currentPort);
                    TestResult mainResult = testRtspUrl(mainUrl);
                    
                    if (mainResult.success) {
                        String subUrl = null;
                        if (currentPattern.length > 1) {
                            subUrl = buildRtspUrl(camera, currentPattern[1], currentPort);
                            if (!testRtspUrl(subUrl).success) {
                                subUrl = null;
                            }
                        }
                        return new PatternTestResult(true, mainUrl, subUrl, currentPattern);
                    } else if (mainResult.authFailed) {
                        return new PatternTestResult(false, null, null, null, true);
                    }
                    
                    return new PatternTestResult(false, null, null, null);
                }));
            }
        }
        
        // Wait for first success or all failures
        boolean success = false;
        try {
            for (Future<PatternTestResult> future : futures) {
                try {
                    PatternTestResult result = future.get(100, TimeUnit.MILLISECONDS);
                    
                    if (result.authFailed) {
                        Logger.info("Auth failed for " + camera.getIpAddress());
                        camera.setAuthFailed(true);
                        camera.setErrorMessage("RTSP Auth Failed: Invalid credentials");
                        parallelExecutor.shutdownNow();
                        return false;
                    }
                    
                    if (result.success) {
                        Logger.info("SUCCESS: Found working RTSP URLs");
                        
                        // Set streams
                        StreamInfo main = new StreamInfo();
                        main.setRtspUrl(result.mainUrl);
                        camera.setMainStream(main);
                        StreamProbe.probe(main);
                        
                        if (result.subUrl != null) {
                            StreamInfo sub = new StreamInfo();
                            sub.setRtspUrl(result.subUrl);
                            camera.setSubStream(sub);
                            StreamProbe.probe(sub);
                        }
                        
                        // Cache successful pattern
                        if (result.pattern != null) {
                            patternCache.put(cacheKey, result.pattern);
                            Logger.info("Cached successful pattern for " + cacheKey);
                        }
                        
                        success = true;
                        parallelExecutor.shutdownNow(); // Cancel remaining tests
                        break;
                    }
                } catch (TimeoutException e) {
                    // Test still running, continue
                } catch (Exception e) {
                    // Test failed, continue
                }
            }
        } finally {
            parallelExecutor.shutdownNow();
        }
        
        return success;
    }
    
    /**
     * Try a specific pattern on all ports.
     */
    private static boolean tryPattern(Camera camera, String[] pattern, List<Integer> ports) {
        for (int port : ports) {
            String mainUrl = buildRtspUrl(camera, pattern[0], port);
            TestResult mainResult = testRtspUrl(mainUrl);
            
            if (mainResult.success) {
                String subUrl = null;
                if (pattern.length > 1) {
                    subUrl = buildRtspUrl(camera, pattern[1], port);
                    if (!testRtspUrl(subUrl).success) {
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
                camera.setAuthFailed(true);
                camera.setErrorMessage("RTSP Auth Failed: Invalid credentials");
                return false;
            }
        }
        
        return false;
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
            grabber.setOption("stimeout", "2000000"); // 2 seconds in microseconds
            grabber.setOption("timeout", "2000000");
            grabber.setOption("reconnect", "0");
            grabber.setOption("reconnect_at_eof", "0");
            grabber.setOption("reconnect_streamed", "0");
            grabber.setOption("loglevel", "quiet"); // Suppress FFmpeg noise
            grabber.start();
            
            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            
            // Success - clean up and return
            return new TestResult(width > 0 && height > 0, false);
        } catch (Exception e) {
            String msg = e.getMessage();
            boolean authFailed = msg != null && (msg.contains("401") || msg.contains("Unauthorized") || msg.contains("403"));
            return new TestResult(false, authFailed);
        } finally {
            // CRITICAL FIX: Always release resources in finally block
            if (grabber != null) {
                try {
                    grabber.stop();
                } catch (Exception e) {
                    // Ignore stop errors
                }
                try {
                    grabber.release();
                } catch (Exception e) {
                    // Ignore release errors
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
    
    private static class PatternTestResult {
        boolean success;
        String mainUrl;
        String subUrl;
        String[] pattern;
        boolean authFailed;
        
        PatternTestResult(boolean success, String mainUrl, String subUrl, String[] pattern) {
            this(success, mainUrl, subUrl, pattern, false);
        }
        
        PatternTestResult(boolean success, String mainUrl, String subUrl, String[] pattern, boolean authFailed) {
            this.success = success;
            this.mainUrl = mainUrl;
            this.subUrl = subUrl;
            this.pattern = pattern;
            this.authFailed = authFailed;
        }
    }
}
