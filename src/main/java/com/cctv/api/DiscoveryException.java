package com.cctv.api;

public class DiscoveryException extends RuntimeException {
    public DiscoveryException(String message) {
        super(message);
    }

    public DiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
