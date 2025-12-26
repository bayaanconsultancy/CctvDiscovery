package com.cctv.model;

public class StreamInfo {
    private String rtspUrl;
    private String resolution;
    private String codec;
    private int bitrate;
    private double fps;
    private String error;

    public String getRtspUrl() { return rtspUrl; }
    public void setRtspUrl(String rtspUrl) { this.rtspUrl = rtspUrl; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public String getCodec() { return codec; }
    public void setCodec(String codec) { this.codec = codec; }

    public int getBitrate() { return bitrate; }
    public void setBitrate(int bitrate) { this.bitrate = bitrate; }

    public double getFps() { return fps; }
    public void setFps(double fps) { this.fps = fps; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
