package com.cctv.api;

public class DiscoveryProgress {
    private final String phase;
    private final int current;
    private final int total;
    private final String message;
    
    public DiscoveryProgress(String phase, int current, int total, String message) {
        this.phase = phase;
        this.current = current;
        this.total = total;
        this.message = message;
    }
    
    public String getPhase() { return phase; }
    public int getCurrent() { return current; }
    public int getTotal() { return total; }
    public String getMessage() { return message; }
    public double getPercentage() { return total > 0 ? (double) current / total * 100 : 0; }
    
    @Override
    public String toString() {
        return String.format("%s: %d/%d (%.1f%%) - %s", phase, current, total, getPercentage(), message);
    }
}