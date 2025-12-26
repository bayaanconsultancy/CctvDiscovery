package com.cctv.probe;

import com.cctv.model.StreamInfo;
import com.cctv.util.Logger;
import org.bytedeco.javacv.FFmpegFrameGrabber;

public class StreamProbe {
    private static final int TIMEOUT_MS = 10000;

    public static void probe(StreamInfo stream) {
        if (stream == null || stream.getRtspUrl() == null) {
            Logger.info("Skipping probe - stream or URL is null");
            return;
        }
        
        Logger.info("=== Starting Stream Probe ===");
        Logger.info("RTSP URL: " + stream.getRtspUrl());
        FFmpegFrameGrabber grabber = null;
        try {
            grabber = new FFmpegFrameGrabber(stream.getRtspUrl());
            grabber.setOption("rtsp_transport", "tcp");
            grabber.setOption("stimeout", String.valueOf(TIMEOUT_MS * 1000)); // 10 seconds in microseconds
            grabber.setOption("timeout", String.valueOf(TIMEOUT_MS * 1000));
            grabber.setTimeout(TIMEOUT_MS);
            Logger.info("Starting FFmpeg grabber with 10s timeout...");
            grabber.start();
            Logger.info("FFmpeg grabber started successfully");
            
            String codec = grabber.getVideoCodecName();
            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            int bitrate = grabber.getVideoBitrate();
            double fps = grabber.getFrameRate();
            
            if (bitrate <= 0) {
                bitrate = 0;
            }
            
            if (fps <= 0 || Double.isNaN(fps)) {
                fps = grabber.getVideoFrameRate();
            }
            
            Logger.info("Raw values - Codec: " + codec + ", Width: " + width + ", Height: " + height + ", Bitrate: " + bitrate + ", FPS: " + fps);
            
            stream.setCodec(codec);
            stream.setResolution(width + "x" + height);
            stream.setBitrate(bitrate / 1000);
            stream.setFps(fps > 0 && !Double.isNaN(fps) ? fps : 0);
            
            Logger.info("=== Stream Probe Success ===");
            Logger.info("Resolution: " + stream.getResolution() + ", Codec: " + stream.getCodec() + ", FPS: " + stream.getFps() + ", Bitrate: " + stream.getBitrate());
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            Logger.error("=== Stream Probe Failed ===");
            Logger.error("URL: " + stream.getRtspUrl());
            Logger.error("Error: " + errorMsg, e);
            
            if (errorMsg != null) {
                if (errorMsg.contains("401") || errorMsg.contains("Unauthorized")) {
                    stream.setError("RTSP Auth Failed: Invalid credentials");
                } else if (errorMsg.contains("403") || errorMsg.contains("Forbidden")) {
                    stream.setError("RTSP Auth Failed: Access forbidden");
                } else if (errorMsg.contains("404") || errorMsg.contains("Not Found")) {
                    stream.setError("RTSP Error: Stream not found");
                } else if (errorMsg.contains("Connection refused")) {
                    stream.setError("RTSP Error: Connection refused");
                } else if (errorMsg.contains("timed out") || errorMsg.contains("timeout")) {
                    stream.setError("RTSP Error: Connection timeout");
                } else {
                    stream.setError("RTSP Error: " + errorMsg.substring(0, Math.min(100, errorMsg.length())));
                }
            } else {
                stream.setError("RTSP Error: Unknown error");
            }
        } finally {
            if (grabber != null) {
                try {
                    grabber.stop();
                    grabber.release();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }
}
