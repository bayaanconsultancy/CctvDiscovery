package com.cctv.api;

public class CameraResult {
    private String ipAddress;
    private String manufacturer;
    private String model;
    private String cameraName;
    private String serialNumber;
    private String firmware;
    private Credential credentials;
    private StreamResult mainStream;
    private StreamResult subStream;
    private String discoveryMethod;
    private String authenticationMethod;
    private boolean isNvr;
    private int channelCount;
    private long timeDifferenceMs;
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    
    public String getCameraName() { return cameraName; }
    public void setCameraName(String cameraName) { this.cameraName = cameraName; }
    
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    
    public String getFirmware() { return firmware; }
    public void setFirmware(String firmware) { this.firmware = firmware; }
    
    public Credential getCredentials() { return credentials; }
    public void setCredentials(Credential credentials) { this.credentials = credentials; }
    
    public StreamResult getMainStream() { return mainStream; }
    public void setMainStream(StreamResult mainStream) { this.mainStream = mainStream; }
    
    public StreamResult getSubStream() { return subStream; }
    public void setSubStream(StreamResult subStream) { this.subStream = subStream; }
    
    public String getDiscoveryMethod() { return discoveryMethod; }
    public void setDiscoveryMethod(String discoveryMethod) { this.discoveryMethod = discoveryMethod; }
    
    public String getAuthenticationMethod() { return authenticationMethod; }
    public void setAuthenticationMethod(String authenticationMethod) { this.authenticationMethod = authenticationMethod; }
    
    public boolean isNvr() { return isNvr; }
    public void setNvr(boolean nvr) { isNvr = nvr; }
    
    public int getChannelCount() { return channelCount; }
    public void setChannelCount(int channelCount) { this.channelCount = channelCount; }
    
    public long getTimeDifferenceMs() { return timeDifferenceMs; }
    public void setTimeDifferenceMs(long timeDifferenceMs) { this.timeDifferenceMs = timeDifferenceMs; }
}