package com.cctv.discovery;

import com.cctv.util.Logger;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import java.util.concurrent.*;

public class RtspTester {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(8);
    private static final int TEST_TIMEOUT_MS = 2000;

    public static class TestResult {
        public final boolean success;
        public final boolean authFailed;
        public final String errorMessage;
        public final String resolution;

        public TestResult(boolean success, boolean authFailed, String errorMessage, String resolution) {
            this.success = success;
            this.authFailed = authFailed;
            this.errorMessage = errorMessage;
            this.resolution = resolution;
        }
    }

    public static CompletableFuture<TestResult> testUrlAsync(String url) {
        return CompletableFuture.supplyAsync(() -> testUrl(url), EXECUTOR);
    }

    public static TestResult testUrl(String url) {
        FFmpegFrameGrabber grabber = null;
        try {
            Logger.info("Testing RTSP URL: " + url);
            grabber = new FFmpegFrameGrabber(url);
            configureGrabber(grabber);
            grabber.start();

            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            String resolution = width + "x" + height;

            Logger.info("RTSP Success: " + resolution + " for " + url);
            return new TestResult(true, false, null, resolution);

        } catch (Exception e) {
            String msg = e.getMessage();
            Logger.error("RTSP Error: " + (msg != null ? msg : "Unknown") + " for " + url);

            boolean authFailed = isAuthError(msg);
            String errorType = classifyError(msg, authFailed);

            Logger.info("RTSP Failed: " + errorType + " - " + url);
            return new TestResult(false, authFailed, msg, null);

        } finally {
            closeGrabber(grabber);
        }
    }

    private static void configureGrabber(FFmpegFrameGrabber grabber) {
        grabber.setOption("rtsp_transport", "tcp");
        grabber.setOption("stimeout", String.valueOf(TEST_TIMEOUT_MS * 1000));
        grabber.setOption("timeout", String.valueOf(TEST_TIMEOUT_MS * 1000));
        grabber.setOption("reconnect", "0");
        grabber.setOption("loglevel", "error");
    }

    private static boolean isAuthError(String msg) {
        return msg != null && (msg.contains("401") || msg.contains("Unauthorized") || 
                              msg.contains("403") || msg.contains("Authentication failed"));
    }

    private static String classifyError(String msg, boolean authFailed) {
        if (authFailed) return "Authentication Failed";
        if (msg == null) return "Unknown Error";
        
        if (msg.contains("404")) return "Path Not Found";
        if (msg.contains("Connection refused")) return "Connection Refused";
        if (msg.contains("timeout") || msg.contains("timed out")) return "Timeout";
        if (msg.contains("Could not open input")) return "Invalid Stream Path";
        if (msg.contains("avformat_open_input")) return "Stream Format Error";
        
        return "Connection Failed";
    }

    private static void closeGrabber(FFmpegFrameGrabber grabber) {
        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
            } catch (Exception e) {
                Logger.error("Error closing grabber: " + e.getMessage());
            }
        }
    }

    public static void shutdown() {
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}