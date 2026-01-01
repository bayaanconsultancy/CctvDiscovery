package com.cctv.discovery;

import com.cctv.model.Camera;
import com.cctv.model.StreamInfo;
import com.cctv.probe.StreamProbe;
import com.cctv.util.Logger;
import java.util.List;
import java.util.concurrent.*;

public class RtspDiscoveryEngine {
    private static final int PATTERN_TIMEOUT_MS = 1500;
    private static final int MAX_CONCURRENT_TESTS = 6;

    public static class DiscoveryResult {
        public final boolean success;
        public final String mainUrl;
        public final String subUrl;
        public final String[] pattern;

        public DiscoveryResult(boolean success, String mainUrl, String subUrl, String[] pattern) {
            this.success = success;
            this.mainUrl = mainUrl;
            this.subUrl = subUrl;
            this.pattern = pattern;
        }
    }

    public static boolean discoverStreams(Camera camera) {
        if (!validateCamera(camera)) {
            return false;
        }

        String manufacturer = ManufacturerDetector.detect(camera);
        String cacheKey = PatternCache.generateCacheKey(camera, manufacturer);

        Logger.info("RTSP discovery for " + camera.getIpAddress() + " (Manufacturer: " + manufacturer + ")");

        // Try cached pattern first
        if (tryCachedPattern(camera, cacheKey)) {
            return true;
        }

        // Get patterns and test them
        List<String[]> patterns = PatternManager.getPatternsForCamera(camera, manufacturer);
        Logger.info("Testing " + patterns.size() + " patterns on ports: " + camera.getOpenRtspPorts());

        DiscoveryResult result = testPatternsParallel(camera, patterns);
        if (result.success) {
            applySuccessfulResult(camera, result, cacheKey);
            return true;
        }

        // Fallback to generic patterns
        if (!manufacturer.equals("Generic")) {
            Logger.info("Trying generic patterns as fallback");
            List<String[]> genericPatterns = PatternManager.getPatternsForCamera(camera, "Generic");
            result = testPatternsParallel(camera, genericPatterns);
            if (result.success) {
                applySuccessfulResult(camera, result, cacheKey);
                return true;
            }
        }

        Logger.info("No working RTSP URLs found for " + camera.getIpAddress());
        camera.setErrorMessage("RTSP URL discovery failed");
        return false;
    }

    private static boolean validateCamera(Camera camera) {
        if (camera.getUsername() == null || camera.getPassword() == null) {
            Logger.info("Skipping RTSP discovery - no credentials");
            return false;
        }
        if (camera.getOpenRtspPorts().isEmpty()) {
            Logger.info("Skipping RTSP discovery - no RTSP ports open");
            return false;
        }
        return true;
    }

    private static boolean tryCachedPattern(Camera camera, String cacheKey) {
        if (PatternCache.hasCachedPattern(cacheKey)) {
            Logger.info("Trying cached pattern for " + cacheKey);
            String[] cachedPattern = PatternCache.getCachedPattern(cacheKey);
            if (testSinglePattern(camera, cachedPattern)) {
                Logger.info("SUCCESS: Cached pattern worked!");
                return true;
            }
        }
        return false;
    }

    private static DiscoveryResult testPatternsParallel(Camera camera, List<String[]> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return new DiscoveryResult(false, null, null, null);
        }

        ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_TESTS);
        CompletionService<DiscoveryResult> completionService = new ExecutorCompletionService<>(executor);

        int submitted = 0;
        for (String[] pattern : patterns) {
            for (int port : camera.getOpenRtspPorts()) {
                completionService.submit(() -> testPatternOnPort(camera, pattern, port));
                submitted++;
            }
        }

        try {
            for (int i = 0; i < submitted; i++) {
                try {
                    Future<DiscoveryResult> future = completionService.poll(PATTERN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (future != null) {
                        DiscoveryResult result = future.get();
                        if (result.success) {
                            return result;
                        }
                    }
                } catch (Exception e) {
                    // Continue with next result
                }
            }
        } finally {
            executor.shutdownNow();
        }

        return new DiscoveryResult(false, null, null, null);
    }

    private static DiscoveryResult testPatternOnPort(Camera camera, String[] pattern, int port) {
        String mainUrl = buildRtspUrl(camera, pattern[0], port);
        RtspTester.TestResult mainResult = RtspTester.testUrl(mainUrl);

        if (mainResult.success) {
            String subUrl = null;
            if (pattern.length > 1 && pattern[1] != null) {
                subUrl = buildRtspUrl(camera, pattern[1], port);
                RtspTester.TestResult subResult = RtspTester.testUrl(subUrl);
                if (!subResult.success) {
                    subUrl = null;
                }
            }
            return new DiscoveryResult(true, mainUrl, subUrl, pattern);
        } else if (mainResult.authFailed) {
            camera.setAuthFailed(true);
            camera.setErrorMessage("RTSP Auth Failed: Invalid credentials");
        }

        return new DiscoveryResult(false, null, null, null);
    }

    private static boolean testSinglePattern(Camera camera, String[] pattern) {
        for (int port : camera.getOpenRtspPorts()) {
            DiscoveryResult result = testPatternOnPort(camera, pattern, port);
            if (result.success) {
                applySuccessfulResult(camera, result, null);
                return true;
            }
        }
        return false;
    }

    private static void applySuccessfulResult(Camera camera, DiscoveryResult result, String cacheKey) {
        Logger.info("SUCCESS: Found working RTSP URLs");

        // Set main stream
        StreamInfo main = new StreamInfo();
        main.setRtspUrl(result.mainUrl);
        camera.setMainStream(main);
        StreamProbe.probe(main);

        // Set sub stream if available
        if (result.subUrl != null) {
            StreamInfo sub = new StreamInfo();
            sub.setRtspUrl(result.subUrl);
            camera.setSubStream(sub);
            StreamProbe.probe(sub);
        }

        // Cache successful pattern
        if (cacheKey != null && result.pattern != null) {
            PatternCache.cachePattern(cacheKey, result.pattern);
        }
    }

    private static String buildRtspUrl(Camera camera, String path, int port) {
        return String.format("rtsp://%s:%s@%s:%d%s",
                camera.getUsername(), camera.getPassword(), camera.getIpAddress(), port, path);
    }
}