package com.cctv.discovery;

import com.cctv.discovery.NvrDetector;
import com.cctv.model.Camera;
import com.cctv.onvif.OnvifClient;
import com.cctv.probe.StreamProbe;
import com.cctv.util.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class DeviceProber {
    private static final int THREAD_POOL_SIZE = 10;

    public static void probeAll(List<Camera> cameras) {
        Logger.info("Starting device probing for " + cameras.size() + " cameras");
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<?>> futures = new ArrayList<>();
        final java.util.concurrent.atomic.AtomicInteger completed = new java.util.concurrent.atomic.AtomicInteger(0);
        
        for (Camera camera : cameras) {
            Future<?> future = executor.submit(() -> {
                try {
                    Logger.info("=== Processing camera: " + camera.getIpAddress() + " ===");
                    Logger.info("Username: " + camera.getUsername() + ", Has Password: " + (camera.getPassword() != null && !camera.getPassword().isEmpty()));
                    Logger.info("AuthFailed flag before: " + camera.isAuthFailed());
                    
                    if (camera.getUsername() != null && camera.getPassword() != null) {
                        boolean onvifSuccess = false;
                        
                        if (camera.getOnvifServiceUrl() != null) {
                            Logger.info("Attempting ONVIF authentication...");
                            if (OnvifClient.authenticate(camera)) {
                                Logger.info("ONVIF auth succeeded, fetching multi-channel streams...");
                                List<Camera> channelCameras = OnvifClient.fetchStreamUrlsMultiChannel(camera);
                                
                                if (channelCameras.size() > 1) {
                                    Logger.info("Found " + channelCameras.size() + " channels");
                                    // Replace single camera with multiple channels
                                    synchronized(cameras) {
                                        int index = cameras.indexOf(camera);
                                        if (index >= 0) {
                                            cameras.remove(index);
                                            cameras.addAll(index, channelCameras);
                                        }
                                    }
                                    onvifSuccess = true;
                                } else if (channelCameras.size() == 1) {
                                    // Single channel, probe streams
                                    Camera singleCamera = channelCameras.get(0);
                                    if (singleCamera.getMainStream() != null) {
                                        Logger.info("Probing main stream for " + singleCamera.getIpAddress());
                                        StreamProbe.probe(singleCamera.getMainStream());
                                        onvifSuccess = true;
                                    }
                                    if (singleCamera.getSubStream() != null) {
                                        Logger.info("Probing sub stream for " + singleCamera.getIpAddress());
                                        StreamProbe.probe(singleCamera.getSubStream());
                                    }
                                    // Update original camera with single channel data
                                    camera.setCameraName(singleCamera.getCameraName());
                                    camera.setMainStream(singleCamera.getMainStream());
                                    camera.setSubStream(singleCamera.getSubStream());
                                }
                            } else {
                                Logger.info("ONVIF auth failed");
                            }
                        }
                        
                        if (!onvifSuccess && !camera.isAuthFailed()) {
                            Logger.info("ONVIF unavailable (not auth issue), trying RTSP URL patterns for " + camera.getIpAddress());
                            RtspUrlGuesser.tryGuessUrls(camera);
                        }
                        
                        // Try NVR/DVR channel detection if single camera failed
                        if (camera.getMainStream() == null && !camera.isAuthFailed()) {
                            Logger.info("Trying NVR/DVR channel detection for " + camera.getIpAddress());
                            List<Camera> channels = NvrDetector.detectAndExtractChannels(camera);
                            if (!channels.isEmpty()) {
                                Logger.info("Found " + channels.size() + " channels in NVR/DVR");
                                // Replace single camera with multiple channels
                                synchronized(cameras) {
                                    int index = cameras.indexOf(camera);
                                    if (index >= 0) {
                                        cameras.remove(index);
                                        cameras.addAll(index, channels);
                                    }
                                }
                            }
                        }
                    }
                    Logger.info("=== Completed processing camera: " + camera.getIpAddress() + " ===");
                    Logger.info("AuthFailed flag after: " + camera.isAuthFailed());
                    Logger.info("Has MainStream: " + (camera.getMainStream() != null));
                    Logger.info("Has SubStream: " + (camera.getSubStream() != null));
                } catch (Exception e) {
                    Logger.error("Failed to probe camera " + camera.getIpAddress(), e);
                    camera.setErrorMessage(e.getMessage());
                } finally {
                    completed.incrementAndGet();
                }
            });
            futures.add(future);
        }
        
        executor.shutdown();
        
        for (Future<?> future : futures) {
            try {
                future.get(10, TimeUnit.MINUTES);
            } catch (Exception e) {
                Logger.error("Task execution failed", e);
            }
        }
        
        Logger.info("Device probing completed");
    }
}
