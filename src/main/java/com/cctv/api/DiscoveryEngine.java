package com.cctv.api;

import com.cctv.discovery.*;
import com.cctv.model.Camera;
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
                
                for (Camera cam : scannedCameras) {
                    if (!allCameras.contains(cam)) {
                        allCameras.add(cam);
                    }
                }
                reportProgress("Port Scanning", ipRange.size(), ipRange.size(), "Port scan completed");
            }
            
            // Assign credentials and probe
            if (!allCameras.isEmpty()) {
                assignCredentials(allCameras);
                reportProgress("Authentication", 0, allCameras.size(), "Testing credentials");
                
                DeviceProber.probeAll(allCameras);
                
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
                } else {
                    errors.add(new DiscoveryError(
                        camera.getIpAddress(),
                        camera.getErrorMessage() != null ? camera.getErrorMessage() : "No streams found",
                        "Failed to discover any RTSP streams"
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
    
    private void assignCredentials(List<Camera> cameras) {
        for (Camera camera : cameras) {
            for (Credential cred : config.getCredentials()) {
                camera.setUsername(cred.getUsername());
                camera.setPassword(cred.getPassword());
                break; // Use first credential, DeviceProber will try others if this fails
            }
        }
    }
    
    private void detectNvrChannels(List<Camera> cameras) {
        List<Camera> nvrChannels = new ArrayList<>();
        List<Camera> toRemove = new ArrayList<>();
        
        for (Camera camera : cameras) {
            if (camera.getMainStream() == null) {
                List<Camera> channels = NvrDetector.detectAndExtractChannels(camera);
                if (!channels.isEmpty()) {
                    nvrChannels.addAll(channels);
                    toRemove.add(camera);
                }
            }
        }
        
        cameras.removeAll(toRemove);
        cameras.addAll(nvrChannels);
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
            StreamResult mainStream = new StreamResult();
            mainStream.setRtspUrl(camera.getMainStream().getRtspUrl());
            mainStream.setResolution(camera.getMainStream().getResolution());
            mainStream.setCodec(camera.getMainStream().getCodec());
            mainStream.setBitrate(camera.getMainStream().getBitrate());
            mainStream.setFps((int)camera.getMainStream().getFps());
            result.setMainStream(mainStream);
        }
        
        if (camera.getSubStream() != null) {
            StreamResult subStream = new StreamResult();
            subStream.setRtspUrl(camera.getSubStream().getRtspUrl());
            subStream.setResolution(camera.getSubStream().getResolution());
            subStream.setCodec(camera.getSubStream().getCodec());
            subStream.setBitrate(camera.getSubStream().getBitrate());
            subStream.setFps((int)camera.getSubStream().getFps());
            result.setSubStream(subStream);
        }
        
        result.setDiscoveryMethod(camera.getOnvifServiceUrl() != null ? "ONVIF" : "Port Scan");
        result.setNvr(camera.getIpAddress().contains("_ch"));
        
        return result;
    }
    
    private void reportProgress(String phase, int current, int total, String message) {
        if (config.getProgressCallback() != null) {
            config.getProgressCallback().accept(new DiscoveryProgress(phase, current, total, message));
        }
    }
}