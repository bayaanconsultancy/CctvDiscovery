package com.cctv.api;

import java.time.LocalDateTime;
import java.util.List;

public class DiscoveryResult {
    private final LocalDateTime discoveryTime;
    private final int totalDevices;
    private final int successfulDevices;
    private final int failedDevices;
    private final List<CameraResult> cameras;
    private final List<DiscoveryError> errors;
    private final long durationMs;
    
    public DiscoveryResult(LocalDateTime discoveryTime, List<CameraResult> cameras, 
                          List<DiscoveryError> errors, long durationMs) {
        this.discoveryTime = discoveryTime;
        this.cameras = cameras;
        this.errors = errors;
        this.durationMs = durationMs;
        this.totalDevices = cameras.size() + errors.size();
        this.successfulDevices = cameras.size();
        this.failedDevices = errors.size();
    }
    
    public LocalDateTime getDiscoveryTime() { return discoveryTime; }
    public int getTotalDevices() { return totalDevices; }
    public int getSuccessfulDevices() { return successfulDevices; }
    public int getFailedDevices() { return failedDevices; }
    public List<CameraResult> getCameras() { return cameras; }
    public List<DiscoveryError> getErrors() { return errors; }
    public long getDurationMs() { return durationMs; }
    
    public String toJson() {
        return JsonSerializer.serialize(this);
    }
}