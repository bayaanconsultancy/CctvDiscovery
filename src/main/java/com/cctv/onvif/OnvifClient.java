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

    public static void fetchStreamUrls(Camera camera) {
        try {
            String profilesBody = SoapHelper.createSoapEnvelope(
                "<GetProfiles xmlns=\"http://www.onvif.org/ver10/media/wsdl\"/>",
                camera.getUsername(), camera.getPassword());
            
            String mediaUrl = camera.getOnvifServiceUrl().replace("/onvif/device_service", "/onvif/media_service");
            if (!mediaUrl.contains("/onvif/")) {
                mediaUrl = "http://" + camera.getIpAddress() + "/onvif/media_service";
            }
            
            Logger.info("=== GetProfiles Request for " + camera.getIpAddress() + " ===");
            Logger.info("URL: " + mediaUrl);
            Logger.info("Request Body: " + profilesBody);
            
            String response = SoapHelper.sendSoapRequest(mediaUrl, "", profilesBody, null, null);
            Logger.info("=== GetProfiles Response for " + camera.getIpAddress() + " ===");
            Logger.info(response);
            
            String[] profiles = response.split("<trt:Profiles");
            if (profiles.length == 1) {
                profiles = response.split("<Profiles");
            }
            if (profiles.length > 1) {
                String mainProfile = extractProfileToken(profiles[1]);
                if (mainProfile != null) {
                    String mainUrl = getStreamUri(mediaUrl, mainProfile, camera.getUsername(), camera.getPassword());
                    if (mainUrl != null) {
                        StreamInfo main = new StreamInfo();
                        main.setRtspUrl(mainUrl);
                        camera.setMainStream(main);
                    }
                }
                
                if (profiles.length > 2) {
                    String subProfile = extractProfileToken(profiles[2]);
                    if (subProfile != null) {
                        String subUrl = getStreamUri(mediaUrl, subProfile, camera.getUsername(), camera.getPassword());
                        if (subUrl != null) {
                            StreamInfo sub = new StreamInfo();
                            sub.setRtspUrl(subUrl);
                            camera.setSubStream(sub);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.error("Failed to fetch stream URLs for " + camera.getIpAddress() + ": " + e.getMessage(), e);
            String currentError = camera.getErrorMessage();
            String newError = "Stream fetch failed: " + e.getMessage();
            camera.setErrorMessage(currentError != null ? currentError + "; " + newError : newError);
        }
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
