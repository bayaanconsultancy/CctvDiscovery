package com.cctv.discovery;

import com.cctv.model.Camera;
import com.cctv.util.Logger;
import java.net.*;
import java.util.*;

public class OnvifDiscovery {
    private static final String MULTICAST_ADDRESS = "239.255.255.250";
    private static final int MULTICAST_PORT = 3702;
    private static final int TIMEOUT_MS = 3000;

    public static List<Camera> discover() {
        List<Camera> cameras = new ArrayList<>();
        Logger.info("Starting ONVIF WS-Discovery...");
        
        String probe = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<e:Envelope xmlns:e=\"http://www.w3.org/2003/05/soap-envelope\" " +
            "xmlns:w=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
            "xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" " +
            "xmlns:dn=\"http://www.onvif.org/ver10/network/wsdl\">" +
            "<e:Header><w:MessageID>uuid:" + UUID.randomUUID() + "</w:MessageID>" +
            "<w:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>" +
            "<w:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action>" +
            "</e:Header><e:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe>" +
            "</e:Body></e:Envelope>";

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            byte[] probeBytes = probe.getBytes("UTF-8");
            DatagramPacket packet = new DatagramPacket(probeBytes, probeBytes.length, group, MULTICAST_PORT);
            socket.send(packet);
            
            byte[] buffer = new byte[8192];
            Set<String> foundIps = new HashSet<>();
            long endTime = System.currentTimeMillis() + TIMEOUT_MS;
            
            while (System.currentTimeMillis() < endTime) {
                try {
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                    socket.receive(response);
                    String data = new String(response.getData(), 0, response.getLength(), "UTF-8");
                    
                    String xAddr = extractXAddr(data);
                    if (xAddr != null) {
                        String ip = extractIp(xAddr);
                        if (ip != null && !foundIps.contains(ip)) {
                            foundIps.add(ip);
                            Camera camera = new Camera(ip);
                            camera.setOnvifServiceUrl(xAddr);
                            cameras.add(camera);
                            Logger.info("Discovered camera: " + ip);
                        }
                    }
                } catch (SocketTimeoutException e) {
                    break;
                }
            }
        } catch (Exception e) {
            Logger.error("ONVIF discovery failed", e);
        }
        
        Logger.info("ONVIF discovery completed. Found " + cameras.size() + " cameras");
        return cameras;
    }

    private static String extractXAddr(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Try using SoapHelper's DOM-based extraction
            String xAddr = com.cctv.onvif.SoapHelper.extractValue(xml, "d:XAddrs");
            if (xAddr == null) {
                xAddr = com.cctv.onvif.SoapHelper.extractValue(xml, "XAddrs");
            }
            
            if (xAddr != null && !xAddr.trim().isEmpty()) {
                // Prefer IPv4 if multiple addresses
                if (xAddr.contains(" ")) {
                    String[] addrs = xAddr.split("\\s+");
                    for (String a : addrs) {
                        if (!a.contains("[")) return a;
                    }
                    return addrs[0];
                }
                return xAddr.contains("[") ? null : xAddr;
            }
        } catch (Exception e) {
            Logger.error("Failed to extract XAddr from XML", e);
        }
        
        return null;
    }

    private static String extractIp(String url) {
        try {
            URL u = new URL(url);
            return u.getHost();
        } catch (Exception e) {
            return null;
        }
    }
}
