package com.cctv.onvif;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
    
    public static String sendSoapRequest(String serviceUrl, String soapAction, String soapBody, String username, String password) throws Exception {
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
            "<s:Body>" + body + "</s:Body>" +
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
                "<wsse:Username>" + username + "</wsse:Username>" +
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
                "<wsse:Username>" + username + "</wsse:Username>" +
                "<wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText\">" + password + "</wsse:Password>" +
                "<wsu:Created>" + created + "</wsu:Created>" +
                "</wsse:UsernameToken>" +
                "</wsse:Security>" +
                "</s:Header>";
        } catch (Exception e) {
            return "";
        }
    }
    
    public static String extractValue(String xml, String tagName) {
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
}
