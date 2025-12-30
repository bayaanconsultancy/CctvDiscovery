package com.cctv.discovery;

import com.cctv.api.Credential;
import com.cctv.discovery.NvrDetector;
import com.cctv.model.Camera;
import com.cctv.onvif.OnvifClient;
import com.cctv.probe.StreamProbe;
import com.cctv.util.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class DeviceProber {
    // Dynamic thread pool size based on CPU cores
    private static final int THREAD_POOL_SIZE = Math.max(4, Math.min(Runtime.getRuntime().availableProcessors() * 2, 20));
    
    private static volatile boolean cancelled = false;

    /**
     * Progress listener interface for real-time UI updates.
     */
    public interface ProgressListener {
        void onProgress(String camera, int current, int total, String status);
        void onComplete();
        void onCancelled();
    }

    /**
     * Cancel ongoing discovery operation.
     */
    public static void cancel() {
        cancelled = true;
        Logger.info("Discovery cancellation requested");
    }

    /**
     * Probe all cameras with credential rotation support and progress updates.
     */
    public static void probeAll(List<Camera> cameras, List<Credential> credentials, ProgressListener listener) {
        cancelled = false; // Reset cancellation flag
        Logger.info("Starting device probing for " + cameras.size() + " cameras with " + THREAD_POOL_SIZE + " threads");
        Logger.info("Will try " + credentials.size() + " credential(s) per camera");
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        
        // Use ConcurrentHashMap to track cameras to add/remove
        ConcurrentHashMap<Camera, List<Camera>> cameraReplacements = new ConcurrentHashMap<>();
        List<Future<?>> futures = new ArrayList<>();
        final java.util.concurrent.atomic.AtomicInteger completed = new java.util.concurrent.atomic.AtomicInteger(0);
        
        for (Camera camera : cameras) {
            Future<?> future = executor.submit(() -> {
                try {
                    // Check for cancellation
                    if (cancelled) {
                        Logger.info("Skipping " + camera.getIpAddress() + " - discovery cancelled");
                        return;
                    }
                    
                    int currentCount = completed.incrementAndGet();
                    
                    // Update progress
                    if (listener != null) {
                        listener.onProgress(camera.getIpAddress(), currentCount, cameras.size(), "Authenticating...");
                    }
                    
                    Logger.info("Processing camera: " + camera.getIpAddress() + " (" + currentCount + "/" + cameras.size() + ")");
                    
                    // Try all credentials until one works
                    boolean success = tryAllCredentials(camera, credentials, listener);
                    
                    if (success) {
                        // Try NVR/DVR channel detection if single camera succeeded
                        if (camera.getMainStream() != null) {
                            if (listener != null) {
                                listener.onProgress(camera.getIpAddress(), currentCount, cameras.size(), "Detecting NVR channels...");
                            }
                            
                            Logger.info("Trying NVR/DVR channel detection for " + camera.getIpAddress());
                            List<Camera> channels = NvrDetector.detectAndExtractChannels(camera);
                            if (!channels.isEmpty()) {
                                Logger.info("Found " + channels.size() + " channels in NVR/DVR");
                                // Store replacement for later processing
                                cameraReplacements.put(camera, channels);
                            }
                        }
                    }
                    
                    Logger.info("Completed processing camera: " + camera.getIpAddress());
                } catch (Exception e) {
                    Logger.error("Failed to probe camera " + camera.getIpAddress(), e);
                    camera.setErrorMessage(e.getMessage());
                }
            });
            futures.add(future);
        }
        
        executor.shutdown();
        
        // Wait for all tasks to complete or cancellation
        for (Future<?> future : futures) {
            try {
                future.get(10, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                Logger.error("Task execution timeout", e);
                future.cancel(true);
            } catch (Exception e) {
                if (!cancelled) {
                    Logger.error("Task execution failed", e);
                }
            }
        }
        
        // Now safely replace cameras with their channel expansions
        if (!cameraReplacements.isEmpty() && !cancelled) {
            Logger.info("Replacing " + cameraReplacements.size() + " cameras with multi-channel expansions");
            List<Camera> newCameraList = new ArrayList<>();
            for (Camera camera : cameras) {
                if (cameraReplacements.containsKey(camera)) {
                    newCameraList.addAll(cameraReplacements.get(camera));
                } else {
                    newCameraList.add(camera);
                }
            }
            cameras.clear();
            cameras.addAll(newCameraList);
        }
        
        Logger.info("Device probing completed. Total cameras: " + cameras.size());
        
        // Notify completion or cancellation
        if (listener != null) {
            if (cancelled) {
                listener.onCancelled();
            } else {
                listener.onComplete();
            }
        }
    }
    
    /**
     * Probe all cameras without progress listener (backward compatibility).
     */
    public static void probeAll(List<Camera> cameras, List<Credential> credentials) {
        probeAll(cameras, credentials, null);
    }
    
    /**
     * Try all provided credentials until one works.
     */
    private static boolean tryAllCredentials(Camera camera, List<Credential> credentials, ProgressListener listener) {
        // Validate credentials before attempting authentication
        if (credentials == null || credentials.isEmpty()) {
            Logger.info("Skipping camera - no credentials provided");
            camera.setErrorMessage("No credentials provided");
            return false;
        }
        
        for (int i = 0; i < credentials.size(); i++) {
            // Check for cancellation
            if (cancelled) {
                return false;
            }
            
            Credential cred = credentials.get(i);
            
            // Validate individual credential
            if (cred.getUsername() == null || cred.getUsername().trim().isEmpty() ||
                cred.getPassword() == null) {
                Logger.info("Skipping invalid credential #" + (i + 1));
                continue;
            }
            
            Logger.info("Trying credential #" + (i + 1) + ": " + cred.getUsername());
            
            // Update progress
            if (listener != null) {
                listener.onProgress(camera.getIpAddress(), 0, 0, "Trying credential " + (i + 1) + "/" + credentials.size());
            }
            
            camera.setUsername(cred.getUsername());
            camera.setPassword(cred.getPassword());
            
            boolean onvifSuccess = false;
            boolean onvifAttempted = false;
            
            if (camera.getOnvifServiceUrl() != null) {
                onvifAttempted = true;
                Logger.info("Attempting ONVIF authentication...");
                if (OnvifClient.authenticate(camera)) {
                    Logger.info("ONVIF auth succeeded with credential #" + (i + 1));
                    List<Camera> channelCameras = OnvifClient.fetchStreamUrlsMultiChannel(camera);
                    
                    if (channelCameras.size() > 1) {
                        Logger.info("Found " + channelCameras.size() + " channels");
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
                    
                    if (onvifSuccess) {
                        Logger.info("SUCCESS: Credential #" + (i + 1) + " worked via ONVIF");
                        return true;
                    }
                } else {
                    Logger.info("ONVIF auth failed with credential #" + (i + 1));
                }
            }
            
            // Only try RTSP URL guessing if ONVIF was not attempted or failed without auth error
            if (!onvifAttempted && !camera.isAuthFailed()) {
                Logger.info("ONVIF not available, trying RTSP URL patterns");
                if (RtspUrlGuesser.tryGuessUrls(camera)) {
                    Logger.info("SUCCESS: Credential #" + (i + 1) + " worked via RTSP");
                    return true;
                }
            } else if (onvifAttempted && !onvifSuccess && !camera.isAuthFailed()) {
                Logger.info("ONVIF available but returned no streams, trying RTSP URL patterns");
                if (RtspUrlGuesser.tryGuessUrls(camera)) {
                    Logger.info("SUCCESS: Credential #" + (i + 1) + " worked via RTSP");
                    return true;
                }
            }
            
            // If auth failed, don't try more credentials
            if (camera.isAuthFailed()) {
                Logger.info("Authentication failed - stopping credential rotation");
                break;
            }
        }
        
        // All credentials failed
        if (!camera.isAuthFailed()) {
            camera.setErrorMessage("All " + credentials.size() + " credential(s) failed");
        }
        return false;
    }
}
