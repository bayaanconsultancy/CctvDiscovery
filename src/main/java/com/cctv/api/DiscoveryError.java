package com.cctv.api;

public class DiscoveryError {
    private final String ipAddress;
    private final String error;
    private final String details;
    
    public DiscoveryError(String ipAddress, String error, String details) {
        this.ipAddress = ipAddress;
        this.error = error;
        this.details = details;
    }
    
    public String getIpAddress() { return ipAddress; }
    public String getError() { return error; }
    public String getDetails() { return details; }
}