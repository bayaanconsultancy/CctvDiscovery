package com.cctv.onvif;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SoapHelperTest {

    @Test
    void testExtractValue_SimpleTag() {
        String xml = "<?xml version=\"1.0\"?><root><Manufacturer>Hikvision</Manufacturer></root>";
        String result = SoapHelper.extractValue(xml, "Manufacturer");
        assertEquals("Hikvision", result);
    }

    @Test
    void testExtractValue_WithNamespace() {
        String xml = "<?xml version=\"1.0\"?><root xmlns:tds=\"http://example.com\"><tds:Model>DS-2CD2142FWD</tds:Model></root>";
        String result = SoapHelper.extractValue(xml, "tds:Model");
        assertEquals("DS-2CD2142FWD", result);
    }

    @Test
    void testExtractValue_WithoutNamespacePrefix() {
        String xml = "<?xml version=\"1.0\"?><root xmlns:tds=\"http://example.com\"><tds:SerialNumber>12345</tds:SerialNumber></root>";
        String result = SoapHelper.extractValue(xml, "SerialNumber");
        assertEquals("12345", result);
    }

    @Test
    void testExtractValue_MultipleOccurrences() {
        String xml = "<?xml version=\"1.0\"?><root><Name>First</Name><Name>Second</Name></root>";
        String result = SoapHelper.extractValue(xml, "Name");
        assertEquals("First", result); // Should return first occurrence
    }

    @Test
    void testExtractValue_TagNotFound() {
        String xml = "<?xml version=\"1.0\"?><root><Manufacturer>Hikvision</Manufacturer></root>";
        String result = SoapHelper.extractValue(xml, "Model");
        assertNull(result);
    }

    @Test
    void testExtractValue_EmptyTag() {
        String xml = "<?xml version=\"1.0\"?><root><Manufacturer></Manufacturer></root>";
        String result = SoapHelper.extractValue(xml, "Manufacturer");
        assertEquals("", result);
    }

    @Test
    void testExtractValue_NullXml() {
        String result = SoapHelper.extractValue(null, "Manufacturer");
        assertNull(result);
    }

    @Test
    void testExtractValue_EmptyXml() {
        String result = SoapHelper.extractValue("", "Manufacturer");
        assertNull(result);
    }

    @Test
    void testExtractValue_NullTagName() {
        String xml = "<?xml version=\"1.0\"?><root><Manufacturer>Hikvision</Manufacturer></root>";
        String result = SoapHelper.extractValue(xml, null);
        assertNull(result);
    }

    @Test
    void testExtractValue_ComplexNamespace() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\">" +
            "<s:Body><tds:GetDeviceInformationResponse xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\">" +
            "<tds:Manufacturer>Dahua</tds:Manufacturer>" +
            "</tds:GetDeviceInformationResponse></s:Body></s:Envelope>";
        String result = SoapHelper.extractValue(xml, "tds:Manufacturer");
        assertEquals("Dahua", result);
    }

    @Test
    void testExtractValue_WithAttributes() {
        String xml = "<?xml version=\"1.0\"?><root><Name type=\"camera\">TestCamera</Name></root>";
        String result = SoapHelper.extractValue(xml, "Name");
        assertEquals("TestCamera", result);
    }

    @Test
    void testExtractValue_NestedTags() {
        String xml = "<?xml version=\"1.0\"?><root><Device><Model>IPC-HDW</Model></Device></root>";
        String result = SoapHelper.extractValue(xml, "Model");
        assertEquals("IPC-HDW", result);
    }

    @Test
    void testExtractValue_WithWhitespace() {
        String xml = "<?xml version=\"1.0\"?><root><Manufacturer>  Hikvision  </Manufacturer></root>";
        String result = SoapHelper.extractValue(xml, "Manufacturer");
        assertEquals("Hikvision", result); // Should be trimmed
    }

    @Test
    void testCreateSoapEnvelope_WithDigestAuth() {
        String body = "<GetDeviceInformation xmlns=\"http://www.onvif.org/ver10/device/wsdl\"/>";
        String envelope = SoapHelper.createSoapEnvelope(body, "admin", "12345", "digest");
        
        assertNotNull(envelope);
        assertTrue(envelope.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(envelope.contains("<s:Envelope"));
        assertTrue(envelope.contains("<s:Header>"));
        assertTrue(envelope.contains("<wsse:Security"));
        assertTrue(envelope.contains("<wsse:Username>admin</wsse:Username>"));
        assertTrue(envelope.contains("<wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">"));
        assertTrue(envelope.contains("<s:Body>"));
        assertTrue(envelope.contains("</s:Envelope>"));
    }

    @Test
    void testCreateSoapEnvelope_WithPlainTextAuth() {
        String body = "<GetDeviceInformation xmlns=\"http://www.onvif.org/ver10/device/wsdl\"/>";
        String envelope = SoapHelper.createSoapEnvelope(body, "admin", "12345", "plaintext");
        
        assertNotNull(envelope);
        assertTrue(envelope.contains("<wsse:Username>admin</wsse:Username>"));
        assertTrue(envelope.contains("<wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText\">"));
    }

    @Test
    void testCreateSoapEnvelope_NoAuth() {
        String body = "<GetDeviceInformation xmlns=\"http://www.onvif.org/ver10/device/wsdl\"/>";
        String envelope = SoapHelper.createSoapEnvelope(body, null, null);
        
        assertNotNull(envelope);
        assertTrue(envelope.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(envelope.contains("<s:Envelope"));
        assertFalse(envelope.contains("<s:Header>"));
        assertFalse(envelope.contains("<wsse:Security"));
        assertTrue(envelope.contains("<s:Body>"));
    }

    @Test
    void testCreateSoapEnvelope_NullBody() {
        assertThrows(IllegalArgumentException.class, () -> {
            SoapHelper.createSoapEnvelope(null, "admin", "12345");
        });
    }

    @Test
    void testCreateSoapEnvelope_XmlEscaping() {
        String body = "<Test>Value with <special> & \"characters\"</Test>";
        String envelope = SoapHelper.createSoapEnvelope(body, null, null);
        
        // Body should be escaped
        assertTrue(envelope.contains("&lt;") || envelope.contains("&amp;"));
    }

    @Test
    void testSendSoapRequest_NullUrl() {
        assertThrows(IllegalArgumentException.class, () -> {
            SoapHelper.sendSoapRequest(null, "", "body", null, null);
        });
    }

    @Test
    void testSendSoapRequest_EmptyUrl() {
        assertThrows(IllegalArgumentException.class, () -> {
            SoapHelper.sendSoapRequest("", "", "body", null, null);
        });
    }

    @Test
    void testExtractValue_MalformedXml_Fallback() {
        // Test that fallback method handles malformed XML
        String xml = "<root><Manufacturer>Hikvision</Manufacturer>"; // Missing closing tag
        String result = SoapHelper.extractValue(xml, "Manufacturer");
        // Should still work with fallback
        assertNotNull(result);
    }

    @Test
    void testExtractValue_RealOnvifResponse() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://www.w3.org/2003/05/soap-envelope\">" +
            "<SOAP-ENV:Body>" +
            "<tds:GetDeviceInformationResponse xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\">" +
            "<tds:Manufacturer>Hikvision</tds:Manufacturer>" +
            "<tds:Model>DS-2CD2142FWD-I</tds:Model>" +
            "<tds:FirmwareVersion>V5.5.0</tds:FirmwareVersion>" +
            "<tds:SerialNumber>DS-2CD2142FWD-I20170101AAWRJ12345678</tds:SerialNumber>" +
            "</tds:GetDeviceInformationResponse>" +
            "</SOAP-ENV:Body>" +
            "</SOAP-ENV:Envelope>";

        assertEquals("Hikvision", SoapHelper.extractValue(xml, "tds:Manufacturer"));
        assertEquals("DS-2CD2142FWD-I", SoapHelper.extractValue(xml, "tds:Model"));
        assertEquals("V5.5.0", SoapHelper.extractValue(xml, "tds:FirmwareVersion"));
        assertEquals("DS-2CD2142FWD-I20170101AAWRJ12345678", SoapHelper.extractValue(xml, "tds:SerialNumber"));
    }
}
