package com.cctv.discovery;

import com.cctv.model.Camera;
import com.cctv.util.Logger;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

public class PatternManager {
    private static final Map<String, List<String[]>> HARDCODED_PATTERNS = new HashMap<>();
    private static final Map<String, List<String[]>> FILE_PATTERNS = new HashMap<>();
    private static boolean fileLoaded = false;

    static {
        initializeHardcodedPatterns();
        loadExternalPatterns();
    }

    private static void initializeHardcodedPatterns() {
        // Hikvision / Prama
        List<String[]> hik = Arrays.asList(
            new String[] { "/Streaming/Channels/101", "/Streaming/Channels/102" },
            new String[] { "/ISAPI/Streaming/channels/101", "/ISAPI/Streaming/channels/102" },
            new String[] { "/h264/ch1/main/av_stream", "/h264/ch1/sub/av_stream" },
            new String[] { "/live/channel0", "/live/channel1" },
            new String[] { "/media/video1", "/media/video2" },
            new String[] { "/stream1", "/stream2" }
        );
        HARDCODED_PATTERNS.put("Hikvision", hik);
        HARDCODED_PATTERNS.put("Prama", hik);

        // Dahua / CP Plus
        List<String[]> dahua = Arrays.asList(
            new String[] { "/cam/realmonitor?channel=1&subtype=0", "/cam/realmonitor?channel=1&subtype=1" },
            new String[] { "/live/main", "/live/sub" },
            new String[] { "/live/ch1", "/live/ch2" }
        );
        HARDCODED_PATTERNS.put("Dahua", dahua);
        HARDCODED_PATTERNS.put("CP Plus", dahua);
        HARDCODED_PATTERNS.put("Amcrest", dahua);

        // Generic
        List<String[]> generic = Arrays.asList(
            new String[] { "/live/main", "/live/sub" },
            new String[] { "/stream1", "/stream2" },
            new String[] { "/live/channel0", "/live/channel1" },
            new String[] { "/0", "/1" }
        );
        HARDCODED_PATTERNS.put("Generic", generic);
    }

    private static void loadExternalPatterns() {
        try {
            File file = new File("rtsp-urls.txt");
            if (!file.exists()) {
                file = new File("dist/rtsp-urls.txt");
            }

            if (file.exists()) {
                Logger.info("Loading RTSP patterns from " + file.getAbsolutePath());
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    String currentManufacturer = "Generic";

                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            if (line.startsWith("# ")) {
                                currentManufacturer = extractManufacturer(line.substring(2).trim());
                            }
                            continue;
                        }

                        String[] parts = line.split(",", 2);
                        String main = parts[0].trim();
                        String sub = parts.length > 1 ? parts[1].trim() : null;

                        FILE_PATTERNS.computeIfAbsent(currentManufacturer, k -> new ArrayList<>())
                                .add(new String[] { main, sub });
                    }
                }
                fileLoaded = true;
                Logger.info("Loaded RTSP patterns from file");
            }
        } catch (Exception e) {
            Logger.error("Error loading rtsp-urls.txt", e);
        }
    }

    private static String extractManufacturer(String section) {
        if (section.contains("Hikvision")) return "Hikvision";
        if (section.contains("Dahua")) return "Dahua";
        if (section.contains("CP Plus")) return "CP Plus";
        if (section.contains("UNV")) return "UNV";
        return section.split(" ")[0];
    }

    public static List<String[]> getPatternsForCamera(Camera camera, String manufacturer) {
        Set<String> uniquePatterns = new LinkedHashSet<>();
        List<String[]> result = new ArrayList<>();

        // MAC-detected: hardcoded + file patterns
        if (isMacDetected(camera, manufacturer)) {
            Logger.info("MAC-detected manufacturer: " + manufacturer);
            addPatterns(result, uniquePatterns, HARDCODED_PATTERNS.get(manufacturer));
            if (fileLoaded) {
                addPatterns(result, uniquePatterns, FILE_PATTERNS.get(manufacturer));
            }
        } else {
            // Non-MAC: file -> hardcoded -> generic
            if (fileLoaded && FILE_PATTERNS.containsKey(manufacturer)) {
                addPatterns(result, uniquePatterns, FILE_PATTERNS.get(manufacturer));
            } else if (HARDCODED_PATTERNS.containsKey(manufacturer)) {
                addPatterns(result, uniquePatterns, HARDCODED_PATTERNS.get(manufacturer));
            }
        }

        // Fallback to generic
        if (result.isEmpty()) {
            addPatterns(result, uniquePatterns, HARDCODED_PATTERNS.get("Generic"));
        }

        return result;
    }

    private static boolean isMacDetected(Camera camera, String manufacturer) {
        if (camera.getMacAddress() == null) return false;
        String macManufacturer = ManufacturerDetector.getManufacturerFromMac(camera.getMacAddress());
        return macManufacturer != null && macManufacturer.equals(manufacturer);
    }

    private static void addPatterns(List<String[]> result, Set<String> uniquePatterns, List<String[]> patterns) {
        if (patterns == null) return;
        for (String[] pattern : patterns) {
            String key = pattern[0] + "|" + (pattern.length > 1 ? pattern[1] : "");
            if (uniquePatterns.add(key)) {
                result.add(pattern);
            }
        }
    }
}