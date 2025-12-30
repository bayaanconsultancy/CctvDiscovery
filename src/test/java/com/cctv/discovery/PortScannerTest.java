package com.cctv.discovery;

import com.cctv.model.Camera;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PortScannerTest {

    @Test
    void testScan_EmptyList() {
        List<String> emptyList = Arrays.asList();
        List<Camera> cameras = PortScanner.scan(emptyList);
        assertNotNull(cameras);
        assertTrue(cameras.isEmpty());
    }

    @Test
    void testScan_SingleInvalidIp() {
        // Use an IP that's unlikely to exist
        List<String> ips = Arrays.asList("192.0.2.1"); // TEST-NET-1 (RFC 5737)
        List<Camera> cameras = PortScanner.scan(ips);
        assertNotNull(cameras);
        // Should complete without crashing
    }

    @Test
    void testScan_MultipleInvalidIps() {
        List<String> ips = Arrays.asList("192.0.2.1", "192.0.2.2", "192.0.2.3");
        List<Camera> cameras = PortScanner.scan(ips);
        assertNotNull(cameras);
        assertTrue(cameras instanceof List);
    }

    @Test
    void testScan_LocalhostScan() {
        // Scan localhost - might find something if services are running
        List<String> ips = Arrays.asList("127.0.0.1");
        List<Camera> cameras = PortScanner.scan(ips);
        assertNotNull(cameras);
        // Don't assert on size as it depends on what's running locally
    }

    @Test
    void testScan_ReturnsNonNull() {
        List<String> ips = Arrays.asList("192.168.1.1");
        List<Camera> cameras = PortScanner.scan(ips);
        assertNotNull(cameras);
    }

    // Note: Full port scanner tests would require a test network environment
    // These tests verify the scanner doesn't crash and handles edge cases
}
