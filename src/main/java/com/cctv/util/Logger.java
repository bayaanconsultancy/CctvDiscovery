package com.cctv.util;

import org.slf4j.LoggerFactory;

public class Logger {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger("CctvDiscovery");

    /**
     * Get a logger for a specific class with class name in logs.
     */
    public static org.slf4j.Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }

    public static void info(String message) {
        log.info(message);
    }

    public static void error(String message, Exception e) {
        log.error(message, e);
    }

    public static void error(String message) {
        log.error(message);
    }
    
    public static void debug(String message) {
        log.debug(message);
    }
    
    public static void warn(String message) {
        log.warn(message);
    }

    // Class-specific logging methods
    public static void info(Class<?> clazz, String message) {
        LoggerFactory.getLogger(clazz).info(message);
    }

    public static void error(Class<?> clazz, String message, Exception e) {
        LoggerFactory.getLogger(clazz).error(message, e);
    }

    public static void error(Class<?> clazz, String message) {
        LoggerFactory.getLogger(clazz).error(message);
    }
}
