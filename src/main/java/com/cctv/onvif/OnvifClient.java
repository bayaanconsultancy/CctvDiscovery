package com.cctv.onvif;

import com.cctv.model.Camera;
import com.cctv.model.StreamInfo;
import com.cctv.util.Logger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class OnvifClient {
    
    public static boolean authenticate(Camera camera) {
        Logger.info("=== Authenticating " + camera.getIpAddress() + " with username: " + camera.getUsername() + " ===");
        camera.setAuthFailed(false);
        camera.setErrorMessage(null);
        
        // Try different authentication methods
        String[] authMethods = {"digest", "plaintext", "none"};
        
        for (String authMethod : authMethods) {
            try {
                Logger.info("Trying " + authMethod + " authentication");
                if ("none".equals(authMethod)) {
                    fetchDeviceInformation(camera, null, null);
                } else {
                    fetchDeviceInformation(camera, camera.getUsername(), camera.getPassword(), authMethod);
                }
                Logger.info("=== Authentication SUCCESS for " + camera.getIpAddress() + " using " + authMethod + " ===");
                camera.setAuthenticationMethod(authMethod);
                return true;
            } catch (Exception e) {
                Logger.info(authMethod + " authentication failed: " + e.getMessage());
                String errorMsg = e.getMessage();
                String exceptionType = e.getClass().getSimpleName();
                
                if (errorMsg != null && (errorMsg.contains("401") || errorMsg.contains("400") || 
                    errorMsg.contains("Authentication failed") || errorMsg.contains("Unauthorized")) ||
                    (exceptionType.equals("IOException") && errorMsg != null && errorMsg.contains("400"))) {
                    // Continue to next auth method
                    continue;
                }
            }
        }
        
        Logger.error("=== Authentication FAILED for " + camera.getIpAddress() + " - all methods failed ===");
        camera.setAuthFailed(true);
        camera.setErrorMessage("ONVIF Auth Failed: Invalid credentials");
        return false;
    }
    
    private static void fetchDeviceInformation(Camera camera) throws Exception {
        fetchDeviceInformation(camera, camera.getUsername(), camera.getPassword(), "digest");
    }
    
    private static void fetchDeviceInformation(Camera camera, String username, String password) throws Exception {
        fetchDeviceInformation(camera, username, password, "digest");
    }
    
    private static void fetchDeviceInformation(Camera camera, String username, String password, String authType) throws Exception {
        String soapBody;
        if (username != null && password != null) {
            soapBody = SoapHelper.createSoapEnvelope(
                "<GetDeviceInformation xmlns=\"http://www.onvif.org/ver10/device/wsdl\"/>",
                username, password, authType);
        } else {
            soapBody = SoapHelper.createSoapEnvelope(
                "<GetDeviceInformation xmlns=\"http://www.onvif.org/ver10/device/wsdl\"/>",
                null, null);
        }
        
        String response = null;
        Exception lastException = null;
        String[] ports = {extractPort(camera.getOnvifServiceUrl()), "80", "8000", "8080", "8899"};
        
        for (String port : ports) {
            if (port == null) continue;
            String testUrl = "http://" + camera.getIpAddress() + ":" + port + "/onvif/device_service";
            
            try {
                Logger.info("Trying ONVIF on port " + port + ": " + testUrl);
                response = SoapHelper.sendSoapRequest(testUrl, "", soapBody, username, password);
                camera.setOnvifServiceUrl(testUrl);
                Logger.info("=== GetDeviceInformation SUCCESS on port " + port + " ===");
                break;
            } catch (Exception e) {
                Logger.info("Port " + port + " failed: " + e.getMessage());
                lastException = e;
            }
        }
        
        if (response == null) {
            throw lastException != null ? lastException : new Exception("All ONVIF ports failed");
        }
        
        Logger.info(response);
        
        String manufacturer = SoapHelper.extractValue(response, "Manufacturer");
        if (manufacturer == null) manufacturer = SoapHelper.extractValue(response, "tds:Manufacturer");
        camera.setManufacturer(manufacturer);
        
        String model = SoapHelper.extractValue(response, "Model");
        if (model == null) model = SoapHelper.extractValue(response, "tds:Model");
        camera.setModel(model);
        
        String serial = SoapHelper.extractValue(response, "SerialNumber");
        if (serial == null) serial = SoapHelper.extractValue(response, "tds:SerialNumber");
        camera.setSerialNumber(serial);
        
        String firmware = SoapHelper.extractValue(response, "FirmwareVersion");
        if (firmware == null) firmware = SoapHelper.extractValue(response, "tds:FirmwareVersion");
        camera.setFirmwareVersion(firmware);
        
        // Try to get camera name
        fetchCameraName(camera);
        
        fetchSystemDateTime(camera);
    }
    
    private static void fetchSystemDateTime(Camera camera) {
        try {
            String soapBody = SoapHelper.createSoapEnvelope(
                "<GetSystemDateAndTime xmlns=\"http://www.onvif.org/ver10/device/wsdl\"/>",
                camera.getUsername(), camera.getPassword());
            
            String response = SoapHelper.sendSoapRequest(camera.getOnvifServiceUrl(), "", soapBody, null, null);
            
            String year = SoapHelper.extractValue(response, "Year");
            String month = SoapHelper.extractValue(response, "Month");
            String day = SoapHelper.extractValue(response, "Day");
            String hour = SoapHelper.extractValue(response, "Hour");
            String minute = SoapHelper.extractValue(response, "Minute");
            String second = SoapHelper.extractValue(response, "Second");
            
            if (year != null && month != null && day != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                String cameraTime = String.format("%s-%02d-%02d %02d:%02d:%02d",
                    year, Integer.parseInt(month), Integer.parseInt(day),
                    Integer.parseInt(hour != null ? hour : "0"),
                    Integer.parseInt(minute != null ? minute : "0"),
                    Integer.parseInt(second != null ? second : "0"));
                
                Date cameraDate = sdf.parse(cameraTime);
                Date systemDate = new Date();
                camera.setTimeDifferenceMs(systemDate.getTime() - cameraDate.getTime());
            }
        } catch (Exception e) {
            Logger.error("Failed to fetch system date/time for " + camera.getIpAddress(), e);
        }
    }
    
    private static void fetchCameraName(Camera camera) {
        // Try GetHostname first
        try {
            String soapBody = SoapHelper.createSoapEnvelope(
                "<GetHostname xmlns=\"http://www.onvif.org/ver10/device/wsdl\"/>",
                camera.getUsername(), camera.getPassword());
            
            String response = SoapHelper.sendSoapRequest(camera.getOnvifServiceUrl(), "", soapBody, null, null);
            String hostname = SoapHelper.extractValue(response, "Name");
            if (hostname == null) hostname = SoapHelper.extractValue(response, "tds:Name");
            
            if (hostname != null && !hostname.trim().isEmpty()) {
                camera.setCameraName(hostname);
                Logger.info("Found camera hostname: " + hostname);
                return;
            }
        } catch (Exception e) {
            Logger.info("GetHostname failed: " + e.getMessage());
        }
        
        // Try GetScopes for location/name info
        try {
            String soapBody = SoapHelper.createSoapEnvelope(
                "<GetScopes xmlns=\"http://www.onvif.org/ver10/device/wsdl\"/>",
                camera.getUsername(), camera.getPassword());
            
            String response = SoapHelper.sendSoapRequest(camera.getOnvifServiceUrl(), "", soapBody, null, null);
            
            // Look for location or name scopes
            String[] scopeTypes = {"location/name", "location/city", "location/building", "name"};
            for (String scopeType : scopeTypes) {
                if (response.contains(scopeType)) {
                    int start = response.indexOf(scopeType);
                    int end = response.indexOf("</", start);
                    if (end > start) {
                        String scopeValue = response.substring(start + scopeType.length() + 1, end).trim();
                        if (!scopeValue.isEmpty()) {
                            camera.setCameraName(scopeValue);
                            Logger.info("Found camera name from scopes: " + scopeValue);
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.info("GetScopes failed: " + e.getMessage());
        }
    }

    public static java.util.List<Camera> fetchStreamUrlsMultiChannel(Camera baseCamera) {
        java.util.List<Camera> cameras = new java.util.ArrayList<>();
        
        try {
            String profilesBody = SoapHelper.createSoapEnvelope(
                "<GetProfiles xmlns=\"http://www.onvif.org/ver10/media/wsdl\"/>",
                baseCamera.getUsername(), baseCamera.getPassword());
            
            String mediaUrl = baseCamera.getOnvifServiceUrl().replace("/onvif/device_service", "/onvif/media_service");
            if (!mediaUrl.contains("/onvif/")) {
                mediaUrl = "http://" + baseCamera.getIpAddress() + "/onvif/media_service";
            }
            
            Logger.info("=== GetProfiles Request for " + baseCamera.getIpAddress() + " ===");
            String response = SoapHelper.sendSoapRequest(mediaUrl, "", profilesBody, null, null);
            Logger.info("=== GetProfiles Response for " + baseCamera.getIpAddress() + " ===");
            Logger.info(response);
            
            // Extract all profiles
            String[] profiles = response.split("<trt:Profiles");
            if (profiles.length == 1) {
                profiles = response.split("<Profiles");
            }
            
            if (profiles.length > 1) {
                // Process profiles in pairs (main/sub)
                for (int i = 1; i < profiles.length; i += 2) {
                    String mainProfileToken = extractProfileToken(profiles[i]);
                    String mainProfileName = extractProfileName(profiles[i]);
                    
                    if (mainProfileToken != null) {
                        // Create new camera for this channel
                        Camera channelCamera = new Camera(baseCamera);
                        
                        String mainUrl = getStreamUri(mediaUrl, mainProfileToken, baseCamera.getUsername(), baseCamera.getPassword());
                        
                        // Look for sub stream (next profile)
                        String subUrl = null;
                        if (i + 1 < profiles.length) {
                            String subProfileToken = extractProfileToken(profiles[i + 1]);
                            if (subProfileToken != null) {
                                subUrl = getStreamUri(mediaUrl, subProfileToken, baseCamera.getUsername(), baseCamera.getPassword());
                            }
                        }
                        
                        // Create camera name with channel info
                        String channelName = extractChannelFromProfile(mainProfileName);
                        if (channelName == null) channelName = mainProfileName;
                        if (channelName == null) channelName = "Ch" + ((i + 1) / 2);
                        
                        String baseCameraName = baseCamera.getCameraName();
                        if (baseCameraName == null) baseCameraName = baseCamera.getManufacturer();
                        if (baseCameraName == null) baseCameraName = "Camera";
                        
                        channelCamera.setCameraName(baseCameraName + "_" + channelName);
                        
                        // Set streams
                        if (mainUrl != null) {
                            StreamInfo main = new StreamInfo();
                            main.setRtspUrl(mainUrl);
                            channelCamera.setMainStream(main);
                        }
                        
                        if (subUrl != null) {
                            StreamInfo sub = new StreamInfo();
                            sub.setRtspUrl(subUrl);
                            channelCamera.setSubStream(sub);
                        }
                        
                        cameras.add(channelCamera);
                    }
                }
            }
            
            // If no channels found, return original camera
            if (cameras.isEmpty()) {
                cameras.add(baseCamera);
            }
            
        } catch (Exception e) {
            Logger.error("Failed to fetch multi-channel streams for " + baseCamera.getIpAddress() + ": " + e.getMessage(), e);
            baseCamera.setErrorMessage("Multi-channel fetch failed: " + e.getMessage());
            cameras.add(baseCamera);
        }
        
        return cameras;
    }

    private static String extractPort(String url) {
        if (url == null) return "80";
        try {
            java.net.URL u = new java.net.URL(url);
            int port = u.getPort();
            return port == -1 ? "80" : String.valueOf(port);
        } catch (Exception e) {
            return "80";
        }
    }
    
    private static String extractProfileToken(String profileXml) {
        int tokenStart = profileXml.indexOf("token=\"");
        if (tokenStart == -1) return null;
        tokenStart += 7;
        int tokenEnd = profileXml.indexOf("\"", tokenStart);
        if (tokenEnd == -1) return null;
        return profileXml.substring(tokenStart, tokenEnd);
    }
    
    private static String extractProfileName(String profileXml) {
        // Try to extract profile name from <tt:Name> or <Name>
        String name = SoapHelper.extractValue(profileXml, "tt:Name");
        if (name == null) name = SoapHelper.extractValue(profileXml, "Name");
        return name;
    }
    
    private static String extractChannelFromProfile(String profileName) {
        if (profileName == null) return null;
        
        // Flexible patterns: PROFILE_1, PROFILE1, PROFILE-1, Channel_01, Channel01, Ch-1, etc.
        String[] patterns = {
            "PROFILE[_-]?([0-9]+)",
            "Channel[_-]?([0-9]+)", 
            "Ch[_-]?([0-9]+)",
            "Stream[_-]?([0-9]+)",
            "([0-9]+)"  // Just numbers as fallback
        };
        
        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(profileName);
            if (m.find()) {
                return "Ch" + m.group(1);
            }
        }
        
        return profileName; // Fallback to full profile name
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
            
            Logger.info("=== GetStreamUri Request (Profile: " + profileToken + ") ===");
            Logger.info("URL: " + mediaUrl);
            Logger.info("Request Body: " + streamBody);
            
            String response = SoapHelper.sendSoapRequest(mediaUrl, "", streamBody, null, null);
            Logger.info("=== GetStreamUri Response ===");
            Logger.info(response);
            
            String uri = SoapHelper.extractValue(response, "Uri");
            if (uri == null) uri = SoapHelper.extractValue(response, "uri");
            if (uri == null) uri = SoapHelper.extractValue(response, "tt:Uri");
            if (uri == null) uri = SoapHelper.extractValue(response, "trt:Uri");
            if (uri == null) {
                int uriStart = response.indexOf("<Uri>");
                if (uriStart == -1) uriStart = response.indexOf("<uri>");
                if (uriStart != -1) {
                    int uriEnd = response.indexOf("</", uriStart);
                    if (uriEnd != -1) {
                        uri = response.substring(response.indexOf(">", uriStart) + 1, uriEnd);
                    }
                }
            }
            if (uri != null && username != null && password != null) {
                if (!uri.contains("@")) {
                    uri = uri.replace("rtsp://", "rtsp://" + username + ":" + password + "@");
                }
            }
            Logger.info("Extracted RTSP URL: " + uri);
            return uri;
        } catch (Exception e) {
            Logger.error("Failed to get stream URI", e);
            return null;
        }
    }
}
