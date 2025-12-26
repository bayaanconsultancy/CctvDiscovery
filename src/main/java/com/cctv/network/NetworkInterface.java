package com.cctv.network;

import com.cctv.util.Logger;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NetworkInterface {
    private String name;
    private String subnet;
    private int hostCount;

    public NetworkInterface(String name, String subnet, int hostCount) {
        this.name = name;
        this.subnet = subnet;
        this.hostCount = hostCount;
    }

    public String getName() { return name; }
    public String getSubnet() { return subnet; }
    public int getHostCount() { return hostCount; }

    @Override
    public String toString() {
        return String.format("%s - %s (%d hosts)", name, subnet, hostCount);
    }

    public static List<NetworkInterface> getAvailableInterfaces() {
        List<NetworkInterface> interfaces = new ArrayList<>();
        try {
            for (java.net.NetworkInterface ni : Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (ni.isLoopback() || !ni.isUp()) continue;
                
                for (InterfaceAddress addr : ni.getInterfaceAddresses()) {
                    InetAddress inetAddr = addr.getAddress();
                    if (inetAddr.isLoopbackAddress() || inetAddr instanceof java.net.Inet6Address) continue;
                    
                    short prefix = addr.getNetworkPrefixLength();
                    if (prefix < 8 || prefix > 30) continue;
                    
                    String subnet = getSubnet(inetAddr.getHostAddress(), prefix);
                    int hosts = (1 << (32 - prefix)) - 2;
                    interfaces.add(new NetworkInterface(ni.getDisplayName(), subnet, hosts));
                }
            }
        } catch (Exception e) {
            Logger.error("Failed to enumerate network interfaces", e);
        }
        return interfaces;
    }

    private static String getSubnet(String ip, short prefix) {
        try {
            String[] parts = ip.split("\\.");
            long ipLong = 0;
            for (String part : parts) {
                ipLong = (ipLong << 8) | Integer.parseInt(part);
            }
            long mask = (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
            long network = ipLong & mask;
            return String.format("%d.%d.%d.%d/%d",
                (network >> 24) & 0xFF, (network >> 16) & 0xFF,
                (network >> 8) & 0xFF, network & 0xFF, prefix);
        } catch (Exception e) {
            return ip + "/" + prefix;
        }
    }

    public List<String> getIpRange() {
        List<String> ips = new ArrayList<>();
        try {
            String[] parts = subnet.split("/");
            String[] octets = parts[0].split("\\.");
            int prefix = Integer.parseInt(parts[1]);
            
            long network = 0;
            for (String octet : octets) {
                network = (network << 8) | Integer.parseInt(octet);
            }
            
            int hosts = (1 << (32 - prefix)) - 2;
            for (int i = 1; i <= hosts; i++) {
                long ip = network + i;
                ips.add(String.format("%d.%d.%d.%d",
                    (ip >> 24) & 0xFF, (ip >> 16) & 0xFF,
                    (ip >> 8) & 0xFF, ip & 0xFF));
            }
        } catch (Exception e) {
            Logger.error("Failed to generate IP range", e);
        }
        return ips;
    }
}
