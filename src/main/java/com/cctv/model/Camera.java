package com.cctv.model;

import java.util.ArrayList;
import java.util.List;

public class Camera {
    private String ipAddress;
    private String onvifServiceUrl;
    private String username;
    private String password;
    private StreamInfo mainStream;
    private StreamInfo subStream;
    private volatile boolean authFailed;
    private volatile String errorMessage;
    private String manufacturer;
    private String model;
    private String cameraName;
    private String serialNumber;
    private String firmwareVersion;
    private volatile long timeDifferenceMs;
    private String authenticationMethod;
    private List<Integer> openRtspPorts = new ArrayList<>();
    private final Object lock = new Object();

    public Camera(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    // Copy constructor
    public Camera(Camera other) {
        this.ipAddress = other.ipAddress;
        this.onvifServiceUrl = other.onvifServiceUrl;
        this.username = other.username;
        this.password = other.password;
        this.authFailed = other.authFailed;
        this.errorMessage = other.errorMessage;
        this.manufacturer = other.manufacturer;
        this.model = other.model;
        this.cameraName = other.cameraName;
        this.serialNumber = other.serialNumber;
        this.firmwareVersion = other.firmwareVersion;
        this.timeDifferenceMs = other.timeDifferenceMs;
        this.authenticationMethod = other.authenticationMethod;
        this.openRtspPorts = new ArrayList<>(other.openRtspPorts);
        // Note: streams are not copied as they will be set individually per channel
    }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getOnvifServiceUrl() { return onvifServiceUrl; }
    public void setOnvifServiceUrl(String onvifServiceUrl) { this.onvifServiceUrl = onvifServiceUrl; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public StreamInfo getMainStream() { 
        synchronized(lock) { return mainStream; }
    }
    public void setMainStream(StreamInfo mainStream) { 
        synchronized(lock) { this.mainStream = mainStream; }
    }

    public StreamInfo getSubStream() { 
        synchronized(lock) { return subStream; }
    }
    public void setSubStream(StreamInfo subStream) { 
        synchronized(lock) { this.subStream = subStream; }
    }

    public boolean isAuthFailed() { return authFailed; }
    public void setAuthFailed(boolean authFailed) { this.authFailed = authFailed; }

    public String getErrorMessage() { 
        synchronized(lock) { return errorMessage; }
    }
    public void setErrorMessage(String errorMessage) { 
        synchronized(lock) { this.errorMessage = errorMessage; }
    }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getCameraName() { return cameraName; }
    public void setCameraName(String cameraName) { this.cameraName = cameraName; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }

    public long getTimeDifferenceMs() { return timeDifferenceMs; }
    public void setTimeDifferenceMs(long timeDifferenceMs) { this.timeDifferenceMs = timeDifferenceMs; }

    public String getAuthenticationMethod() { return authenticationMethod; }
    public void setAuthenticationMethod(String authenticationMethod) { this.authenticationMethod = authenticationMethod; }

    public List<Integer> getOpenRtspPorts() { return openRtspPorts; }
    public void setOpenRtspPorts(List<Integer> openRtspPorts) { this.openRtspPorts = openRtspPorts; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Camera camera = (Camera) o;
        return ipAddress.equals(camera.ipAddress);
    }

    @Override
    public int hashCode() {
        return ipAddress.hashCode();
    }
}
