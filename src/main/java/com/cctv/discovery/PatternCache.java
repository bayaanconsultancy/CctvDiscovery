package com.cctv.discovery;

import com.cctv.model.Camera;
import com.cctv.util.Logger;
import java.util.concurrent.ConcurrentHashMap;

public class PatternCache {
    private static final ConcurrentHashMap<String, String[]> cache = new ConcurrentHashMap<>();

    public static String generateCacheKey(Camera camera, String manufacturer) {
        String ipPrefix = camera.getIpAddress().substring(0, camera.getIpAddress().lastIndexOf('.'));
        String model = camera.getModel() != null ? camera.getModel() : "";
        String macPrefix = camera.getMacAddress() != null ? 
            camera.getMacAddress().substring(0, 8) : "";
        
        return manufacturer + ":" + model + ":" + ipPrefix + ":" + macPrefix;
    }

    public static String[] getCachedPattern(String cacheKey) {
        return cache.get(cacheKey);
    }

    public static void cachePattern(String cacheKey, String[] pattern) {
        cache.put(cacheKey, pattern);
        Logger.info("Cached successful pattern for " + cacheKey);
    }

    public static boolean hasCachedPattern(String cacheKey) {
        return cache.containsKey(cacheKey);
    }

    public static void clearCache() {
        cache.clear();
    }

    public static int getCacheSize() {
        return cache.size();
    }
}