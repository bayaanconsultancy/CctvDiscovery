package com.cctv.api;

import java.time.format.DateTimeFormatter;

public class JsonSerializer {
    
    public static String serialize(DiscoveryResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"discoveryTime\": \"").append(result.getDiscoveryTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
        json.append("  \"totalDevices\": ").append(result.getTotalDevices()).append(",\n");
        json.append("  \"successfulDevices\": ").append(result.getSuccessfulDevices()).append(",\n");
        json.append("  \"failedDevices\": ").append(result.getFailedDevices()).append(",\n");
        json.append("  \"durationMs\": ").append(result.getDurationMs()).append(",\n");
        
        // Cameras array
        json.append("  \"cameras\": [\n");
        for (int i = 0; i < result.getCameras().size(); i++) {
            if (i > 0) json.append(",\n");
            json.append(serializeCamera(result.getCameras().get(i), "    "));
        }
        json.append("\n  ],\n");
        
        // Errors array
        json.append("  \"errors\": [\n");
        for (int i = 0; i < result.getErrors().size(); i++) {
            if (i > 0) json.append(",\n");
            json.append(serializeError(result.getErrors().get(i), "    "));
        }
        json.append("\n  ]\n");
        
        json.append("}");
        return json.toString();
    }
    
    private static String serializeCamera(CameraResult camera, String indent) {
        StringBuilder json = new StringBuilder();
        json.append(indent).append("{\n");
        json.append(indent).append("  \"ipAddress\": ").append(quote(camera.getIpAddress())).append(",\n");
        json.append(indent).append("  \"manufacturer\": ").append(quote(camera.getManufacturer())).append(",\n");
        json.append(indent).append("  \"model\": ").append(quote(camera.getModel())).append(",\n");
        json.append(indent).append("  \"cameraName\": ").append(quote(camera.getCameraName())).append(",\n");
        json.append(indent).append("  \"serialNumber\": ").append(quote(camera.getSerialNumber())).append(",\n");
        json.append(indent).append("  \"firmware\": ").append(quote(camera.getFirmware())).append(",\n");
        json.append(indent).append("  \"timeDifferenceMs\": ").append(camera.getTimeDifferenceMs()).append(",\n");
        
        // Credentials
        if (camera.getCredentials() != null) {
            json.append(indent).append("  \"credentials\": {\n");
            json.append(indent).append("    \"username\": ").append(quote(camera.getCredentials().getUsername())).append(",\n");
            json.append(indent).append("    \"password\": ").append(quote(camera.getCredentials().getPassword())).append("\n");
            json.append(indent).append("  },\n");
        } else {
            json.append(indent).append("  \"credentials\": null,\n");
        }
        
        // Main stream
        if (camera.getMainStream() != null) {
            json.append(indent).append("  \"mainStream\": ").append(serializeStream(camera.getMainStream(), indent + "  ")).append(",\n");
        } else {
            json.append(indent).append("  \"mainStream\": null,\n");
        }
        
        // Sub stream
        if (camera.getSubStream() != null) {
            json.append(indent).append("  \"subStream\": ").append(serializeStream(camera.getSubStream(), indent + "  ")).append(",\n");
        } else {
            json.append(indent).append("  \"subStream\": null,\n");
        }
        
        json.append(indent).append("  \"discoveryMethod\": ").append(quote(camera.getDiscoveryMethod())).append(",\n");
        json.append(indent).append("  \"authenticationMethod\": ").append(quote(camera.getAuthenticationMethod())).append(",\n");
        json.append(indent).append("  \"isNvr\": ").append(camera.isNvr()).append(",\n");
        json.append(indent).append("  \"channelCount\": ").append(camera.getChannelCount()).append("\n");
        json.append(indent).append("}");
        
        return json.toString();
    }
    
    private static String serializeStream(StreamResult stream, String indent) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append(indent).append("  \"rtspUrl\": ").append(quote(stream.getRtspUrl())).append(",\n");
        json.append(indent).append("  \"resolution\": ").append(quote(stream.getResolution())).append(",\n");
        json.append(indent).append("  \"codec\": ").append(quote(stream.getCodec())).append(",\n");
        json.append(indent).append("  \"bitrate\": ").append(stream.getBitrate()).append(",\n");
        json.append(indent).append("  \"fps\": ").append(stream.getFps()).append("\n");
        json.append(indent).append("}");
        return json.toString();
    }
    
    private static String serializeError(DiscoveryError error, String indent) {
        StringBuilder json = new StringBuilder();
        json.append(indent).append("{\n");
        json.append(indent).append("  \"ipAddress\": ").append(quote(error.getIpAddress())).append(",\n");
        json.append(indent).append("  \"error\": ").append(quote(error.getError())).append(",\n");
        json.append(indent).append("  \"details\": ").append(quote(error.getDetails())).append("\n");
        json.append(indent).append("}");
        return json.toString();
    }
    
    private static String quote(String str) {
        if (str == null) return "null";
        return "\"" + str.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}