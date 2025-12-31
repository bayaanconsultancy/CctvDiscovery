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

    // Hardcoded patterns (Indian Market Focused) as Fallback
    private static final Map<String, List<String[]>> HARDCODED_PATTERNS = new HashMap<>();

    static {
        // Hikvision / Prama
        List<String[]> hik = new ArrayList<>();
        hik.add(new String[] { "/Streaming/Channels/101", "/Streaming/Channels/102" });
        hik.add(new String[] { "/Streaming/Channels/1", "/Streaming/Channels/2" });
        hik.add(new String[] { "/ISAPI/Streaming/channels/101", "/ISAPI/Streaming/channels/102" });
        hik.add(new String[] { "/h264/ch1/main/av_stream", "/h264/ch1/sub/av_stream" });
        HARDCODED_PATTERNS.put("Hikvision", hik);
        HARDCODED_PATTERNS.put("Prama", hik); // Prama uses Hik firmware

        // Dahua / CP Plus / Godrej
        List<String[]> dahua = new ArrayList<>();
        dahua.add(new String[] { "/cam/realmonitor?channel=1&subtype=0", "/cam/realmonitor?channel=1&subtype=1" });
        dahua.add(new String[] { "/live/main", "/live/sub" });
        dahua.add(new String[] { "/live/ch1", "/live/ch2" });
        HARDCODED_PATTERNS.put("Dahua", dahua);
        HARDCODED_PATTERNS.put("CP Plus", dahua);
        HARDCODED_PATTERNS.put("Amcrest", dahua);
        HARDCODED_PATTERNS.put("Godrej", dahua); // Often Dahua OEM

        // UNV (Uniview)
        List<String[]> unv = new ArrayList<>();
        unv.add(new String[] { "/live/main", "/live/sub" });
        unv.add(new String[] { "/media/video1", "/media/video2" });
        unv.add(new String[] { "/uni/stream1", "/uni/stream2" });
        HARDCODED_PATTERNS.put("UNV", unv);
        HARDCODED_PATTERNS.put("Uniview", unv);

        // Tiandy
        List<String[]> tiandy = new ArrayList<>();
        tiandy.add(new String[] { "/1/1", "/1/2" });
        tiandy.add(new String[] { "/live/main", "/live/sub" });
        HARDCODED_PATTERNS.put("Tiandy", tiandy);

        // TVT
        List<String[]> tvt = new ArrayList<>();
        tvt.add(new String[] { "/stream1", "/stream2" });
        tvt.add(new String[] { "/live/main", "/live/sub" });
        HARDCODED_PATTERNS.put("TVT", tvt);

        // Axis
        List<String[]> axis = new ArrayList<>();
        axis.add(new String[] { "/axis-media/media.amp?videocodec=h264",
                "/axis-media/media.amp?videocodec=h264&resolution=320x240" });
        HARDCODED_PATTERNS.put("Axis", axis);

        // Generic
        List<String[]> generic = new ArrayList<>();
        generic.add(new String[] { "/live/channel0", "/live/channel1" });
        generic.add(new String[] { "/live/main", "/live/sub" });
        generic.add(new String[] { "/stream", "/substream" });
        generic.add(new String[] { "/stream1", "/stream2" });
        generic.add(new String[] { "/ch0", "/ch1" });
        generic.add(new String[] { "/0", "/1" });
        generic.add(new String[] { "/video", "/video2" });
        generic.add(new String[] { "/main", "/sub" });
        HARDCODED_PATTERNS.put("Generic", generic);
    }

    // Loaded patterns from file
    private static final Map<String, List<String[]>> FILE_PATTERNS = new HashMap<>();
    private static boolean fileLoaded = false;

    // Pattern cache: manufacturer:model -> successful pattern
    private static final Map<String, String[]> patternCache = new ConcurrentHashMap<>();

    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static ExecutorService executor;

    // Static initializer to try loading the file
    static {
        loadExternalPatterns();
    }

    private static void loadExternalPatterns() {
        try {
            File file = new File("rtsp-patterns.txt");
            if (!file.exists()) {
                // Try checks in dist folder if running from IDE or built structure
                File distFile = new File("dist/rtsp-patterns.txt");
                if (distFile.exists())
                    file = distFile;
            }

            if (file.exists()) {
                Logger.info("Loading RTSP patterns from " + file.getAbsolutePath());
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    String currentManufacturer = "Generic";

                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("# ")) {
                            // Check if it's a section header line like "# Hikvision"
                            if (line.startsWith("# ")) {
                                String section = line.substring(2).trim();
                                if (!section.isEmpty()) {
                                    // Clean up manufacturer name (take first word or familiar brand)
                                    if (section.contains("Hikvision"))
                                        currentManufacturer = "Hikvision";
                                    else if (section.contains("Dahua"))
                                        currentManufacturer = "Dahua";
                                    else if (section.contains("TP-Link"))
                                        currentManufacturer = "TP-Link";
                                    else if (section.contains("UNV"))
                                        currentManufacturer = "UNV";
                                    else if (section.contains("Tiandy"))
                                        currentManufacturer = "Tiandy";
                                    else if (section.contains("TVT"))
                                        currentManufacturer = "TVT";
                                    else if (section.contains("Godrej"))
                                        currentManufacturer = "Godrej";
                                    else if (section.contains("Axis"))
                                        currentManufacturer = "Axis";
                                    else if (section.contains("Generic"))
                                        currentManufacturer = "Generic";
                                    else
                                        currentManufacturer = section.split(" ")[0]; // Fallback
                                }
                            }
                            continue;
                        }

                        // Parse pattern line: "main, sub"
                        String[] parts = line.split(",");
                        String main = parts[0].trim();
                        String sub = (parts.length > 1) ? parts[1].trim() : null;

                        FILE_PATTERNS.computeIfAbsent(currentManufacturer, k -> new ArrayList<>())
                                .add(new String[] { main, sub });
                    }
                }
                fileLoaded = true;
                Logger.info("Successfully loaded RTSP patterns from file.");
            } else {
                Logger.info("rtsp-patterns.txt not found, using hardcoded defaults.");
            }
        } catch (Exception e) {
            Logger.error("Error loading rtsp-patterns.txt", e);
        }
    }

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

        // Get patterns (File > Hardcoded)
        List<String[]> patterns = getPatternsForManufacturer(manufacturer);

        Logger.info("Testing " + patterns.size() + " patterns for " + manufacturer + " on ports: " + ports);

        // Try parallel pattern testing
        if (tryPatternsParallel(camera, patterns, ports, cacheKey)) {
            return true;
        }

        // Fallback to Generic if specific failed
        if (!manufacturer.equals("Generic")) {
            Logger.info("Manufacturer patterns failed, trying generic patterns");
            List<String[]> genericPatterns = getPatternsForManufacturer("Generic");
            if (tryPatternsParallel(camera, genericPatterns, ports, cacheKey)) {
                return true;
            }
        }

        Logger.info("No working RTSP URLs found for " + camera.getIpAddress());
        camera.setErrorMessage("RTSP URL guessing failed");
        return false;
    }

    /**
     * Get patterns: Prefer File, then Hardcoded.
     */
    private static List<String[]> getPatternsForManufacturer(String manufacturer) {
        // 1. Try File Patterns
        if (fileLoaded && FILE_PATTERNS.containsKey(manufacturer)) {
            return FILE_PATTERNS.get(manufacturer);
        }
        // 1b. Try File Patterns for similar keys (e.g., "CP Plus" vs "Dahua") if mapped
        if (fileLoaded) {
            // Basic aliasing for file lookup
            if (manufacturer.equals("CP Plus") && FILE_PATTERNS.containsKey("Dahua"))
                return FILE_PATTERNS.get("Dahua");
            if (manufacturer.equals("Amcrest") && FILE_PATTERNS.containsKey("Dahua"))
                return FILE_PATTERNS.get("Dahua");
            if (manufacturer.equals("Prama") && FILE_PATTERNS.containsKey("Hikvision"))
                return FILE_PATTERNS.get("Hikvision");
        }

        // 2. Try Hardcoded Patterns
        if (HARDCODED_PATTERNS.containsKey(manufacturer)) {
            return HARDCODED_PATTERNS.get(manufacturer);
        }

        // 3. Fallback Alias Checking for Hardcoded
        if (manufacturer.equals("Reolink"))
            return HARDCODED_PATTERNS.get("Generic"); // Or add specific if known

        return HARDCODED_PATTERNS.get("Generic");
    }

    /**
     * Try patterns in parallel for 5-10x faster discovery.
     */
    private static boolean tryPatternsParallel(Camera camera, List<String[]> patterns, List<Integer> ports,
            String cacheKey) {
        if (patterns == null || patterns.isEmpty())
            return false;

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
                        if (currentPattern.length > 1 && currentPattern[1] != null) {
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
                    PatternTestResult result = future.get(300, TimeUnit.MILLISECONDS); // 300ms per pattern guess check

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
                if (pattern.length > 1 && pattern[1] != null) {
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
            Logger.info("Testing RTSP URL: " + url);
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

            Logger.info("RTSP Success! Resolution: " + width + "x" + height + " for " + url);
            return new TestResult(width > 0 && height > 0, false, null);
        } catch (Exception e) {
            String msg = e.getMessage();
            boolean authFailed = msg != null
                    && (msg.contains("401") || msg.contains("Unauthorized") || msg.contains("403"));

            String errorType = "Unknown Error";
            if (authFailed)
                errorType = "Authentication Failed (401/403)";
            else if (msg != null && msg.contains("404"))
                errorType = "Path Not Found (404)";
            else if (msg != null && (msg.contains("Connection refused") || msg.contains("10061")))
                errorType = "Connection Refused";
            else if (msg != null && (msg.contains("timeout") || msg.contains("10060")))
                errorType = "Timeout";

            Logger.info("RTSP Failed: " + errorType + " - " + url + " [" + (msg != null ? msg : "No details") + "]");
            return new TestResult(false, authFailed, msg);
        } finally {
            if (grabber != null) {
                try {
                    grabber.stop();
                } catch (Exception e) {
                    /* Ignore */ }
                try {
                    grabber.release();
                } catch (Exception e) {
                    /* Ignore */ }
            }
        }
    }

    public static class TestResult {
        boolean success;
        boolean authFailed;
        String errorMessage;

        TestResult(boolean success, boolean authFailed, String errorMessage) {
            this.success = success;
            this.authFailed = authFailed;
            this.errorMessage = errorMessage;
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
