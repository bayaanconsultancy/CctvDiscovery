package com.cctv.util;

import org.slf4j.LoggerFactory;

public class Logger {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger("CctvDiscovery");

    public static void info(String message) {
        log.info(message);
    }

    public static void error(String message, Exception e) {
        log.error(message, e);
    }

    public static void error(String message) {
        log.error(message);
    }
}
