package com.cctv.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class CctvDiscovery {
    
    private String ipRange;
    private List<Credential> credentials = new ArrayList<>();
    private boolean onvifEnabled = true;
    private boolean portScanEnabled = true;
    private boolean rtspGuessingEnabled = true;
    private boolean nvrDetectionEnabled = true;
    private int threadCount = Runtime.getRuntime().availableProcessors();
    private int timeoutSeconds = 30;
    private Consumer<DiscoveryProgress> progressCallback;
    
    private CctvDiscovery() {}
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private CctvDiscovery discovery = new CctvDiscovery();
        
        public Builder ipRange(String ipRange) {
            discovery.ipRange = ipRange;
            return this;
        }
        
        public Builder credentials(String username, String password) {
            discovery.credentials.add(new Credential(username, password));
            return this;
        }
        
        public Builder credentials(List<Credential> credentials) {
            discovery.credentials.addAll(credentials);
            return this;
        }
        
        public Builder enableOnvif(boolean enabled) {
            discovery.onvifEnabled = enabled;
            return this;
        }
        
        public Builder enablePortScan(boolean enabled) {
            discovery.portScanEnabled = enabled;
            return this;
        }
        
        public Builder enableRtspGuessing(boolean enabled) {
            discovery.rtspGuessingEnabled = enabled;
            return this;
        }
        
        public Builder enableNvrDetection(boolean enabled) {
            discovery.nvrDetectionEnabled = enabled;
            return this;
        }
        
        public Builder threadCount(int threadCount) {
            discovery.threadCount = threadCount;
            return this;
        }
        
        public Builder timeout(int seconds) {
            discovery.timeoutSeconds = seconds;
            return this;
        }
        
        public Builder onProgress(Consumer<DiscoveryProgress> callback) {
            discovery.progressCallback = callback;
            return this;
        }
        
        public CctvDiscovery build() {
            if (discovery.ipRange == null || discovery.ipRange.isEmpty()) {
                throw new IllegalArgumentException("IP range is required");
            }
            if (discovery.credentials.isEmpty()) {
                throw new IllegalArgumentException("At least one credential is required");
            }
            return discovery;
        }
    }
    
    public DiscoveryResult discover() {
        return discoverAsync().join();
    }
    
    public CompletableFuture<DiscoveryResult> discoverAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return performDiscovery();
            } catch (Exception e) {
                throw new RuntimeException("Discovery failed", e);
            }
        });
    }
    
    private DiscoveryResult performDiscovery() {
        DiscoveryEngine engine = new DiscoveryEngine(this);
        return engine.execute();
    }
    
    public String getIpRange() { return ipRange; }
    public List<Credential> getCredentials() { return credentials; }
    public boolean isOnvifEnabled() { return onvifEnabled; }
    public boolean isPortScanEnabled() { return portScanEnabled; }
    public boolean isRtspGuessingEnabled() { return rtspGuessingEnabled; }
    public boolean isNvrDetectionEnabled() { return nvrDetectionEnabled; }
    public int getThreadCount() { return threadCount; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public Consumer<DiscoveryProgress> getProgressCallback() { return progressCallback; }
}