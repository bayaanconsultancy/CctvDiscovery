package com.cctv.api;

public class StreamResult {
    private String rtspUrl;
    private String resolution;
    private String codec;
    private int bitrate;
    private int fps;
    
    public String getRtspUrl() { return rtspUrl; }
    public void setRtspUrl(String rtspUrl) { this.rtspUrl = rtspUrl; }
    
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    
    public String getCodec() { return codec; }
    public void setCodec(String codec) { this.codec = codec; }
    
    public int getBitrate() { return bitrate; }
    public void setBitrate(int bitrate) { this.bitrate = bitrate; }
    
    public int getFps() { return fps; }
    public void setFps(int fps) { this.fps = fps; }
}