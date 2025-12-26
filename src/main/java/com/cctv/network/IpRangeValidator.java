package com.cctv.network;

import java.util.ArrayList;
import java.util.List;

public class IpRangeValidator {
    
    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        try {
            for (String part : parts) {
                int val = Integer.parseInt(part);
                if (val < 0 || val > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static List<String> generateRange(String startIp, String endIp) {
        List<String> ips = new ArrayList<>();
        long start = ipToLong(startIp);
        long end = ipToLong(endIp);
        
        if (start > end) {
            long temp = start;
            start = end;
            end = temp;
        }
        
        for (long i = start; i <= end; i++) {
            ips.add(longToIp(i));
        }
        return ips;
    }

    private static long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        long result = 0;
        for (String part : parts) {
            result = (result << 8) | Integer.parseInt(part);
        }
        return result;
    }

    private static String longToIp(long ip) {
        return String.format("%d.%d.%d.%d",
            (ip >> 24) & 0xFF, (ip >> 16) & 0xFF,
            (ip >> 8) & 0xFF, ip & 0xFF);
    }
}
