package com.cctv.discovery;

/**
 * Progress listener interface for real-time UI updates.
 */
public interface ProgressListener {
    void onProgress(String camera, int current, int total, String status);

    void onComplete();

    void onCancelled();
}
