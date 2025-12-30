package com.cctv.discovery;

import com.cctv.model.Camera;
import com.cctv.model.StreamInfo;
import com.cctv.onvif.SoapHelper;
import com.cctv.util.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NvrDetector {
    
    public static List<Camera> detectAndExtractChannels(Camera device) {
        List<Camera> channels = new ArrayList<>();
        
        if (device.getUsername() == null || device.getPassword() == null) {
            return channels;
        }
        
        Logger.info("NVR/DVR Detection for " + device.getIpAddress());
        
        // Try ONVIF multi-profile detection first
        int channelCount = detectOnvifChannels(device);
        if (channelCount > 1) {
            Logger.info("Detected NVR/DVR with " + channelCount + " channels via ONVIF");
            channels = extractOnvifChannels(device, channelCount);
        }
        
        // Try HTTP API detection if ONVIF failed
        if (channels.isEmpty()) {
            channelCount = detectHttpApiChannels(device);
            if (channelCount > 1) {
                Logger.info("Detected NVR/DVR with " + channelCount + " channels via HTTP API");
                channels = extractApiChannels(device, channelCount);
            }
        }
        
        // Try pattern-based detection as fallback
        if (channels.isEmpty()) {
            channels = extractPatternChannels(device);
            if (!channels.isEmpty()) {
                Logger.info("Detected NVR/DVR with " + channels.size() + " channels via pattern matching");
            }
        }
        
        return channels;
    }
    
    private static int detectOnvifChannels(Camera device) {
        try {
            // CRITICAL FIX: Check for null ONVIF service URL
            if (device.getOnvifServiceUrl() == null) {
                return 0;
            }
            
            String profilesBody = SoapHelper.createSoapEnvelope(
                "<GetProfiles xmlns=\"http://www.onvif.org/ver10/media/wsdl\"/>",
                device.getUsername(), device.getPassword());
            
            String mediaUrl = device.getOnvifServiceUrl().replace("/onvif/device_service", "/onvif/media_service");
            String response = SoapHelper.sendSoapRequest(mediaUrl, "", profilesBody, null, null);
            
            String[] profiles = response.split("<trt:Profiles");
            if (profiles.length == 1) {
                profiles = response.split("<Profiles");
            }
            
            return Math.max(0, profiles.length - 1);
        } catch (Exception e) {
            Logger.info("ONVIF profile detection failed: " + e.getMessage());
            return 0;
        }
    }
    
    private static int detectHttpApiChannels(Camera device) {
        // Try Hikvision API
        try {
            String url = "http://" + device.getIpAddress() + "/ISAPI/System/deviceInfo";
            String response = SoapHelper.sendSoapRequest(url, "", "", device.getUsername(), device.getPassword());
            
            Pattern pattern = Pattern.compile("<videoInputPortNums>(\\d+)</videoInputPortNums>");
            Matcher matcher = pattern.matcher(response);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            Logger.info("Hikvision API detection failed");
        }
        
        // Try Dahua API
        try {
            String url = "http://" + device.getIpAddress() + "/cgi-bin/magicBox.cgi?action=getDeviceType";
            String response = SoapHelper.sendSoapRequest(url, "", "", device.getUsername(), device.getPassword());
            
            if (response.contains("NVR") || response.contains("DVR")) {
                // Try to get channel info
                url = "http://" + device.getIpAddress() + "/cgi-bin/configManager.cgi?action=getConfig&name=ChannelTitle";
                response = SoapHelper.sendSoapRequest(url, "", "", device.getUsername(), device.getPassword());
                
                Pattern pattern = Pattern.compile("table\\.ChannelTitle\\[(\\d+)\\]");
                Matcher matcher = pattern.matcher(response);
                int maxChannel = 0;
                while (matcher.find()) {
                    maxChannel = Math.max(maxChannel, Integer.parseInt(matcher.group(1)));
                }
                return maxChannel + 1;
            }
        } catch (Exception e) {
            Logger.info("Dahua API detection failed");
        }
        
        return 0;
    }
    
    private static List<Camera> extractOnvifChannels(Camera device, int channelCount) {
        List<Camera> channels = new ArrayList<>();
        
        try {
            if (device.getOnvifServiceUrl() == null) {
                return channels;
            }
            
            String profilesBody = SoapHelper.createSoapEnvelope(
                "<GetProfiles xmlns=\"http://www.onvif.org/ver10/media/wsdl\"/>",
                device.getUsername(), device.getPassword());
            
            String mediaUrl = device.getOnvifServiceUrl().replace("/onvif/device_service", "/onvif/media_service");
            String response = SoapHelper.sendSoapRequest(mediaUrl, "", profilesBody, null, null);
            
            String[] profiles = response.split("<trt:Profiles");
            if (profiles.length == 1) {
                profiles = response.split("<Profiles");
            }
            
            for (int i = 1; i < profiles.length && i <= channelCount; i++) {
                String profileToken = extractProfileToken(profiles[i]);
                if (profileToken != null) {
                    String rtspUrl = getStreamUri(mediaUrl, profileToken, device.getUsername(), device.getPassword());
                    if (rtspUrl != null) {
                        Camera channel = new Camera(device.getIpAddress() + "_ch" + i);
                        channel.setUsername(device.getUsername());
                        channel.setPassword(device.getPassword());
                        channel.setManufacturer(device.getManufacturer());
                        channel.setModel(device.getModel() + " Channel " + i);
                        
                        StreamInfo stream = new StreamInfo();
                        stream.setRtspUrl(rtspUrl);
                        channel.setMainStream(stream);
                        
                        // IMPROVEMENT: Probe stream for metadata
                        com.cctv.probe.StreamProbe.probe(stream);
                        
                        channels.add(channel);
                    }
                }
            }
        } catch (Exception e) {
            Logger.error("Failed to extract ONVIF channels", e);
        }
        
        return channels;
    }
    
    private static List<Camera> extractApiChannels(Camera device, int channelCount) {
        List<Camera> channels = new ArrayList<>();
        
        String[][] patterns = {
            // Hikvision NVR patterns
            {"/ISAPI/Streaming/channels/%d01", "/ISAPI/Streaming/channels/%d02"},
            {"/Streaming/channels/%d", "/Streaming/channels/%d"},
            // Dahua NVR patterns  
            {"/cam/realmonitor?channel=%d&subtype=0", "/cam/realmonitor?channel=%d&subtype=1"}
        };
        
        for (String[] pattern : patterns) {
            for (int ch = 1; ch <= Math.min(channelCount, 32); ch++) {
                String mainUrl = String.format("rtsp://%s:%s@%s:554" + pattern[0], 
                    device.getUsername(), device.getPassword(), device.getIpAddress(), ch);
                
                if (RtspUrlGuesser.testRtspUrl(mainUrl).success) {
                    Camera channel = new Camera(device.getIpAddress() + "_ch" + ch);
                    channel.setUsername(device.getUsername());
                    channel.setPassword(device.getPassword());
                    channel.setManufacturer(device.getManufacturer());
                    channel.setModel((device.getModel() != null ? device.getModel() : "NVR") + " Channel " + ch);
                    
                    StreamInfo main = new StreamInfo();
                    main.setRtspUrl(mainUrl);
                    channel.setMainStream(main);
                    
                    // Try sub stream
                    String subUrl = String.format("rtsp://%s:%s@%s:554" + pattern[1], 
                        device.getUsername(), device.getPassword(), device.getIpAddress(), ch);
                    if (RtspUrlGuesser.testRtspUrl(subUrl).success) {
                        StreamInfo sub = new StreamInfo();
                        sub.setRtspUrl(subUrl);
                        channel.setSubStream(sub);
                    }
                    
                    channels.add(channel);
                }
            }
            
            if (!channels.isEmpty()) break; // Found working pattern
        }
        
        return channels;
    }
    
    private static List<Camera> extractPatternChannels(Camera device) {
        List<Camera> channels = new ArrayList<>();
        
        String[][] patterns = {
            {"/ch%d/0", "/ch%d/1"},
            {"/stream%d", "/substream%d"},
            {"/live/ch%02d_0", "/live/ch%02d_1"},
            {"/cam%d", "/cam%d_sub"},
            {"/%d", "/%d_sub"}
        };
        
        for (String[] pattern : patterns) {
            int consecutiveFailures = 0;
            for (int ch = 1; ch <= 16; ch++) { // Test up to 16 channels
                String mainUrl = String.format("rtsp://%s:%s@%s:554" + pattern[0], 
                    device.getUsername(), device.getPassword(), device.getIpAddress(), ch);
                
                if (RtspUrlGuesser.testRtspUrl(mainUrl).success) {
                    consecutiveFailures = 0; // Reset on success
                    Camera channel = new Camera(device.getIpAddress() + "_ch" + ch);
                    channel.setUsername(device.getUsername());
                    channel.setPassword(device.getPassword());
                    channel.setModel("Channel " + ch);
                    
                    StreamInfo main = new StreamInfo();
                    main.setRtspUrl(mainUrl);
                    channel.setMainStream(main);
                    
                    // Try sub stream
                    String subUrl = String.format("rtsp://%s:%s@%s:554" + pattern[1], 
                        device.getUsername(), device.getPassword(), device.getIpAddress(), ch);
                    if (RtspUrlGuesser.testRtspUrl(subUrl).success) {
                        StreamInfo sub = new StreamInfo();
                        sub.setRtspUrl(subUrl);
                        channel.setSubStream(sub);
                    }
                    
                    channels.add(channel);
                } else {
                    consecutiveFailures++;
                    // IMPROVEMENT: Stop after 3 consecutive failures
                    if (consecutiveFailures >= 3) {
                        Logger.info("Stopping channel detection after 3 consecutive failures");
                        break;
                    }
                }
            }
            
            if (!channels.isEmpty()) break; // Found working pattern
        }
        
        return channels;
    }
    
    private static String extractProfileToken(String profileXml) {
        int tokenStart = profileXml.indexOf("token=\"");
        if (tokenStart == -1) return null;
        tokenStart += 7;
        int tokenEnd = profileXml.indexOf("\"", tokenStart);
        if (tokenEnd == -1) return null;
        return profileXml.substring(tokenStart, tokenEnd);
    }
    
    private static String getStreamUri(String mediaUrl, String profileToken, String username, String password) {
        try {
            String streamBody = SoapHelper.createSoapEnvelope(
                "<GetStreamUri xmlns=\"http://www.onvif.org/ver10/media/wsdl\">" +
                "<ProfileToken>" + profileToken + "</ProfileToken>" +
                "<StreamSetup><Stream xmlns=\"http://www.onvif.org/ver10/schema\">RTP-Unicast</Stream>" +
                "<Transport xmlns=\"http://www.onvif.org/ver10/schema\"><Protocol>RTSP</Protocol></Transport>" +
                "</StreamSetup></GetStreamUri>",
                username, password);
            
            String response = SoapHelper.sendSoapRequest(mediaUrl, "", streamBody, null, null);
            
            String uri = SoapHelper.extractValue(response, "Uri");
            if (uri == null) uri = SoapHelper.extractValue(response, "uri");
            if (uri != null && !uri.contains("@")) {
                uri = uri.replace("rtsp://", "rtsp://" + username + ":" + password + "@");
            }
            return uri;
        } catch (Exception e) {
            return null;
        }
    }
}