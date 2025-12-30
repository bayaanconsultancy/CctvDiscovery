package com.cctv.api;

import com.cctv.discovery.*;
import com.cctv.model.Camera;
import com.cctv.model.StreamInfo;
import com.cctv.network.IpRangeValidator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DiscoveryEngine {
    private final CctvDiscovery config;
    
    public DiscoveryEngine(CctvDiscovery config) {
        this.config = config;
    }
    
    public DiscoveryResult execute() {
        long startTime = System.currentTimeMillis();
        LocalDateTime discoveryTime = LocalDateTime.now();
        List<Camera> allCameras = new ArrayList<>();
        List<DiscoveryError> errors = new ArrayList<>();
        
        try {
            // Generate IP range
            String ipRangeStr = config.getIpRange();
            List<String> ipRange;
            
            if (ipRangeStr.contains("-")) {
                String[] parts = ipRangeStr.split("-", 2);
                ipRange = IpRangeValidator.generateRange(parts[0].trim(), parts[1].trim());
            } else {
                // Single IP
                ipRange = new ArrayList<>();
                ipRange.add(ipRangeStr.trim());
            }
            
            reportProgress("ONVIF Discovery", 0, 1, "Starting ONVIF discovery");
            
            // ONVIF Discovery
            if (config.isOnvifEnabled()) {
                List<Camera> onvifCameras = OnvifDiscovery.discover();
                allCameras.addAll(onvifCameras);
                reportProgress("ONVIF Discovery", 1, 1, "Found " + onvifCameras.size() + " ONVIF devices");
            }
            
            // Port Scanning
            if (config.isPortScanEnabled()) {
                reportProgress("Port Scanning", 0, ipRange.size(), "Starting port scan");
                List<Camera> scannedCameras = PortScanner.scan(ipRange);
                
                // Merge camera data instead of simple add
                mergeCameras(allCameras, scannedCameras);
                
                reportProgress("Port Scanning", ipRange.size(), ipRange.size(), "Port scan completed");
            }
            
            // Assign credentials and probe
            if (!allCameras.isEmpty()) {
                reportProgress("Authentication", 0, allCameras.size(), "Testing credentials");
                
                // Pass all credentials to DeviceProber for rotation
                DeviceProber.probeAll(allCameras, config.getCredentials());
                
                // NVR Detection
                if (config.isNvrDetectionEnabled()) {
                    detectNvrChannels(allCameras);
                }
            }
            
            // Convert results
            List<CameraResult> cameraResults = new ArrayList<>();
            for (Camera camera : allCameras) {
                if (camera.getMainStream() != null || camera.getSubStream() != null) {
                    cameraResults.add(convertToResult(camera));
                } else if (!camera.isNvr()) {
                    // Only add error for non-NVR devices without streams
                    errors.add(new DiscoveryError(
                        camera.getIpAddress(),
                        camera.getErrorMessage() != null ? camera.getErrorMessage() : "No streams found",
                        buildErrorDetails(camera)
                    ));
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            reportProgress("Completed", cameraResults.size(), cameraResults.size(), "Discovery completed");
            
            return new DiscoveryResult(discoveryTime, cameraResults, errors, duration);
            
        } catch (Exception e) {
            errors.add(new DiscoveryError("", "Discovery failed", e.getMessage()));
            return new DiscoveryResult(discoveryTime, new ArrayList<>(), errors, System.currentTimeMillis() - startTime);
        }
    }
    
    /**
     * Merge camera data from different discovery methods.
     */
    private void mergeCameras(List<Camera> allCameras, List<Camera> newCameras) {
        for (Camera newCam : newCameras) {
            Camera existing = findCameraByIp(allCameras, newCam.getIpAddress());
            
            if (existing != null) {
                // Merge data - keep ONVIF data, add port scan data
                if (existing.getManufacturer() == null && newCam.getManufacturer() != null) {
                    existing.setManufacturer(newCam.getManufacturer());
                }
                if (existing.getModel() == null && newCam.getModel() != null) {
                    existing.setModel(newCam.getModel());
                }
                if (existing.getOpenRtspPorts().isEmpty() && !newCam.getOpenRtspPorts().isEmpty()) {
                    existing.setOpenRtspPorts(newCam.getOpenRtspPorts());
                }
            } else {
                allCameras.add(newCam);
            }
        }
    }
    
    /**
     * Find camera by IP address.
     */
    private Camera findCameraByIp(List<Camera> cameras, String ipAddress) {
        for (Camera camera : cameras) {
            if (camera.getIpAddress().equals(ipAddress)) {
                return camera;
            }
        }
        return null;
    }
    
    private void assignCredentials(List<Camera> cameras) {
        for (Camera camera : cameras) {
            for (Credential cred : config.getCredentials()) {
                camera.setUsername(cred.getUsername());
                camera.setPassword(cred.getPassword());
                break; // Use first credential, DeviceProber will try others if this fails
            }
        }
    }
    
    /**
     * Detect NVR channels and keep parent NVR device.
     */
    private void detectNvrChannels(List<Camera> cameras) {
        List<Camera> nvrChannels = new ArrayList<>();
        
        for (Camera camera : cameras) {
            if (camera.getMainStream() == null) {
                List<Camera> channels = NvrDetector.detectAndExtractChannels(camera);
                if (!channels.isEmpty()) {
                    // Mark parent as NVR and keep it
                    camera.setIsNvr(true);
                    camera.setChannelCount(channels.size());
                    nvrChannels.addAll(channels);
                }
            }
        }
        
        // Add channels without removing parent
        cameras.addAll(nvrChannels);
    }
    
    /**
     * Build detailed error context.
     */
    private String buildErrorDetails(Camera camera) {
        StringBuilder details = new StringBuilder();
        details.append("Discovery attempts: ");
        
        if (camera.getOnvifServiceUrl() != null) {
            details.append("ONVIF available, ");
        } else {
            details.append("ONVIF not found, ");
        }
        
        if (!camera.getOpenRtspPorts().isEmpty()) {
            details.append("Ports open: ").append(camera.getOpenRtspPorts()).append(", ");
        } else {
            details.append("No RTSP ports open, ");
        }
        
        if (camera.isAuthFailed()) {
            details.append("Authentication failed");
        } else {
            details.append("Authentication not attempted or no credentials");
        }
        
        return details.toString();
    }
    
    private CameraResult convertToResult(Camera camera) {
        CameraResult result = new CameraResult();
        result.setIpAddress(camera.getIpAddress());
        result.setManufacturer(camera.getManufacturer());
        result.setModel(camera.getModel());
        result.setCameraName(camera.getCameraName());
        result.setSerialNumber(camera.getSerialNumber());
        result.setFirmware(camera.getFirmwareVersion());
        result.setTimeDifferenceMs(camera.getTimeDifferenceMs());
        
        if (camera.getUsername() != null) {
            result.setCredentials(new Credential(camera.getUsername(), camera.getPassword()));
        }
        
        if (camera.getMainStream() != null) {
            result.setMainStream(convertStream(camera.getMainStream()));
        }
        
        if (camera.getSubStream() != null) {
            result.setSubStream(convertStream(camera.getSubStream()));
        }
        
        result.setDiscoveryMethod(camera.getOnvifServiceUrl() != null ? "ONVIF" : "Port Scan");
        result.setAuthenticationMethod(camera.getAuthenticationMethod());
        result.setNvr(camera.isNvr() || camera.getIpAddress().contains("_ch"));
        
        return result;
    }
    
    /**
     * Convert and validate stream data.
     */
    private StreamResult convertStream(StreamInfo stream) {
        StreamResult result = new StreamResult();
        
        // Validate RTSP URL
        if (stream.getRtspUrl() != null && stream.getRtspUrl().startsWith("rtsp://")) {
            result.setRtspUrl(stream.getRtspUrl());
        }
        
        // Validate resolution
        if (stream.getResolution() != null && !stream.getResolution().isEmpty() && !stream.getResolution().equals("0x0")) {
            result.setResolution(stream.getResolution());
        } else {
            result.setResolution("Unknown");
        }
        
        // Validate codec
        result.setCodec(stream.getCodec() != null && !stream.getCodec().isEmpty() ? stream.getCodec() : "Unknown");
        
        // Validate bitrate and FPS
        result.setBitrate(stream.getBitrate() > 0 ? stream.getBitrate() : 0);
        result.setFps(stream.getFps() > 0 ? (int)stream.getFps() : 0);
        
        return result;
    }
    
    private void reportProgress(String phase, int current, int total, String message) {
        if (config.getProgressCallback() != null) {
            config.getProgressCallback().accept(new DiscoveryProgress(phase, current, total, message));
        }
    }
}