package com.cctv.onvif;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

public class SoapHelper {
    
    private static final DocumentBuilderFactory documentBuilderFactory;
    
    static {
        documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        try {
            // Security: Disable external entities to prevent XXE attacks
            documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception e) {
            // If features not supported, continue with defaults
        }
    }
    
    public static String sendSoapRequest(String serviceUrl, String soapAction, String soapBody, String username, String password) throws Exception {
        if (serviceUrl == null || serviceUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Service URL cannot be null or empty");
        }
        
        URL url = new URL(serviceUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8");
        conn.setRequestProperty("SOAPAction", soapAction);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setDoOutput(true);
        
        if (username != null && password != null) {
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
        }
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(soapBody.getBytes(StandardCharsets.UTF_8));
        }
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 401) {
            throw new Exception("Authentication failed");
        }
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    public static String createSoapEnvelope(String body, String username, String password) {
        return createSoapEnvelope(body, username, password, "digest");
    }
    
    public static String createSoapEnvelope(String body, String username, String password, String authType) {
        if (body == null) {
            throw new IllegalArgumentException("SOAP body cannot be null");
        }
        
        String securityHeader = "";
        if (username != null && password != null) {
            if ("plaintext".equals(authType)) {
                securityHeader = createWsSecurityHeaderPlainText(username, password);
            } else {
                securityHeader = createWsSecurityHeader(username, password);
            }
        }
        
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" " +
            "xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" " +
            "xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">" +
            securityHeader +
            "<s:Body>" + escapeXml(body) + "</s:Body>" +
            "</s:Envelope>";
    }
    
    private static String createWsSecurityHeader(String username, String password) {
        try {
            String nonce = UUID.randomUUID().toString();
            byte[] nonceBytes = nonce.getBytes(StandardCharsets.UTF_8);
            String nonceBase64 = Base64.getEncoder().encodeToString(nonceBytes);
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            String created = sdf.format(new Date());
            
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(nonceBytes);
            md.update(created.getBytes(StandardCharsets.UTF_8));
            md.update(password.getBytes(StandardCharsets.UTF_8));
            String passwordDigest = Base64.getEncoder().encodeToString(md.digest());
            
            return "<s:Header>" +
                "<wsse:Security s:mustUnderstand=\"1\">" +
                "<wsse:UsernameToken>" +
                "<wsse:Username>" + escapeXml(username) + "</wsse:Username>" +
                "<wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">" + passwordDigest + "</wsse:Password>" +
                "<wsse:Nonce EncodingType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary\">" + nonceBase64 + "</wsse:Nonce>" +
                "<wsu:Created>" + created + "</wsu:Created>" +
                "</wsse:UsernameToken>" +
                "</wsse:Security>" +
                "</s:Header>";
        } catch (Exception e) {
            return "";
        }
    }
    
    private static String createWsSecurityHeaderPlainText(String username, String password) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            String created = sdf.format(new Date());
            
            return "<s:Header>" +
                "<wsse:Security s:mustUnderstand=\"1\">" +
                "<wsse:UsernameToken>" +
                "<wsse:Username>" + escapeXml(username) + "</wsse:Username>" +
                "<wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText\">" + escapeXml(password) + "</wsse:Password>" +
                "<wsu:Created>" + created + "</wsu:Created>" +
                "</wsse:UsernameToken>" +
                "</wsse:Security>" +
                "</s:Header>";
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Extracts a value from XML using proper DOM parsing instead of string manipulation.
     * Handles various namespace prefixes and formats.
     * 
     * @param xml The XML string to parse
     * @param tagName The tag name to search for (with or without namespace prefix)
     * @return The text content of the tag, or null if not found
     */
    public static String extractValue(String xml, String tagName) {
        if (xml == null || xml.trim().isEmpty() || tagName == null) {
            return null;
        }
        
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            
            // Try with namespace prefix first
            NodeList nodes = doc.getElementsByTagName(tagName);
            if (nodes.getLength() > 0) {
                return getTextContent(nodes.item(0));
            }
            
            // Try without namespace prefix (local name)
            String localName = tagName.contains(":") ? tagName.substring(tagName.indexOf(":") + 1) : tagName;
            nodes = doc.getElementsByTagNameNS("*", localName);
            if (nodes.getLength() > 0) {
                return getTextContent(nodes.item(0));
            }
            
            // Fallback: search all elements
            return searchElementByLocalName(doc.getDocumentElement(), localName);
            
        } catch (Exception e) {
            // If DOM parsing fails, fall back to simple string search for backward compatibility
            return extractValueFallback(xml, tagName);
        }
    }
    
    private static String getTextContent(Node node) {
        if (node == null) {
            return null;
        }
        String text = node.getTextContent();
        return text != null ? text.trim() : null;
    }
    
    private static String searchElementByLocalName(Element element, String localName) {
        if (element.getLocalName() != null && element.getLocalName().equals(localName)) {
            return getTextContent(element);
        }
        
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                String result = searchElementByLocalName((Element) child, localName);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
    
    /**
     * Fallback method using string manipulation for cases where DOM parsing fails.
     * Kept for backward compatibility with malformed XML.
     */
    private static String extractValueFallback(String xml, String tagName) {
        String[] variations = {tagName, tagName.replace(":", "")};
        
        for (String tag : variations) {
            String startTag = "<" + tag + ">";
            String endTag = "</" + tag + ">";
            int start = xml.indexOf(startTag);
            
            if (start == -1) {
                startTag = "<" + tag + " ";
                start = xml.indexOf(startTag);
                if (start != -1) {
                    start = xml.indexOf(">", start) + 1;
                }
            } else {
                start += startTag.length();
            }
            
            if (start != -1) {
                int end = xml.indexOf(endTag, start);
                if (end == -1) {
                    end = xml.indexOf("</", start);
                }
                if (end != -1) {
                    return xml.substring(start, end).trim();
                }
            }
        }
        return null;
    }
    
    /**
     * Escapes special XML characters to prevent injection attacks.
     * 
     * @param text The text to escape
     * @return The escaped text safe for XML
     */
    private static String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
