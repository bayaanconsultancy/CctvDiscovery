package com.cctv.discovery;

import com.cctv.model.Camera;
import com.cctv.util.Logger;
import java.util.List;
import java.util.concurrent.*;

/**
 * Simplified RtspUrlGuesser that delegates to the new architecture
 */
public class RtspUrlGuesser {
    private static ExecutorService executor;
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();

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
        RtspTester.shutdown();
    }

    public static void processDevices(List<Camera> cameras) {
        List<Future<Void>> futures = new java.util.ArrayList<>();
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
        return RtspDiscoveryEngine.discoverStreams(camera);
    }

    // Legacy compatibility classes
    public static class TestResult {
        public final boolean success;
        public final boolean authFailed;
        public final String errorMessage;

        public TestResult(boolean success, boolean authFailed, String errorMessage) {
            this.success = success;
            this.authFailed = authFailed;
            this.errorMessage = errorMessage;
        }
    }

    public static TestResult testRtspUrl(String url) {
        RtspTester.TestResult result = RtspTester.testUrl(url);
        return new TestResult(result.success, result.authFailed, result.errorMessage);
    }
}