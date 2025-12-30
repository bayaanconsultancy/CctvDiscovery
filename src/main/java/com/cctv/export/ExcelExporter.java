package com.cctv.export;

import com.cctv.model.Camera;
import com.cctv.model.StreamInfo;
import com.cctv.util.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ExcelExporter {

    /**
     * Export cameras to Excel with plaintext passwords.
     * @deprecated Use export(cameras, filePath, maskPasswords) for better security
     */
    public static void export(List<Camera> cameras, String filePath) {
        export(cameras, filePath, false);
    }
    
    /**
     * Export cameras to Excel with optional password masking.
     * 
     * @param cameras List of cameras to export
     * @param filePath Output file path
     * @param maskPasswords If true, passwords will be masked as "****"
     */
    public static void export(List<Camera> cameras, String filePath, boolean maskPasswords) {
        Logger.info("Exporting " + cameras.size() + " cameras to Excel (passwords masked: " + maskPasswords + ")");
        
        // Sort cameras by IP address
        Collections.sort(cameras, new Comparator<Camera>() {
            @Override
            public int compare(Camera c1, Camera c2) {
                return compareIpAddresses(c1.getIpAddress(), c2.getIpAddress());
            }
        });
        
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("CCTV Discovery");
            
            // Add security warning if passwords are not masked
            if (!maskPasswords) {
                Row warningRow = sheet.createRow(0);
                Cell warningCell = warningRow.createCell(0);
                warningCell.setCellValue("⚠️ WARNING: This file contains PLAINTEXT PASSWORDS. Store securely and delete when no longer needed.");
                
                CellStyle warningStyle = workbook.createCellStyle();
                Font warningFont = workbook.createFont();
                warningFont.setColor(IndexedColors.RED.getIndex());
                warningFont.setBold(true);
                warningStyle.setFont(warningFont);
                warningCell.setCellStyle(warningStyle);
                
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 10));
            }
            
            // Create styles
            Font monoFont = workbook.createFont();
            monoFont.setFontName("Consolas");
            monoFont.setFontHeightInPoints((short) 10);
            
            CellStyle defaultStyle = workbook.createCellStyle();
            defaultStyle.setFont(monoFont);
            
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setFontName("Consolas");
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            
            CellStyle redStyle = workbook.createCellStyle();
            redStyle.setFont(monoFont);
            Font redFont = workbook.createFont();
            redFont.setFontName("Consolas");
            redFont.setColor(IndexedColors.RED.getIndex());
            redStyle.setFont(redFont);
            
            int headerRowNum = maskPasswords ? 0 : 1;
            Row header = sheet.createRow(headerRowNum);
            String[] headers = {"IP Address", "Manufacturer", "Model", "Camera Name", "Serial Number", "Firmware", "Time Diff (sec)",
                "Username", "Password", 
                "Main RTSP URL", "Main Resolution", "Main Codec", "Main Bitrate (kbps)", "Main FPS",
                "Sub RTSP URL", "Sub Resolution", "Sub Codec", "Sub Bitrate (kbps)", "Sub FPS", "Error"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Freeze header row
            sheet.createFreezePane(0, headerRowNum + 1);
            
            int rowNum = headerRowNum + 1;
            for (Camera camera : cameras) {
                Row row = sheet.createRow(rowNum++);
                createStyledCell(row, 0, camera.getIpAddress(), defaultStyle);
                createStyledCell(row, 1, camera.getManufacturer() != null ? camera.getManufacturer() : "", defaultStyle);
                createStyledCell(row, 2, camera.getModel() != null ? camera.getModel() : "", defaultStyle);
                createStyledCell(row, 3, camera.getCameraName() != null ? camera.getCameraName() : "", defaultStyle);
                createStyledCell(row, 4, camera.getSerialNumber() != null ? camera.getSerialNumber() : "", defaultStyle);
                createStyledCell(row, 5, camera.getFirmwareVersion() != null ? camera.getFirmwareVersion() : "", defaultStyle);
                createStyledCell(row, 6, String.valueOf(camera.getTimeDifferenceMs() / 1000), defaultStyle);
                createStyledCell(row, 7, camera.getUsername() != null ? camera.getUsername() : "", defaultStyle);
                String password = camera.getPassword() != null ? camera.getPassword() : "";
                if (maskPasswords && !password.isEmpty()) {
                    password = "****";
                }
                createStyledCell(row, 8, password, defaultStyle);
                
                StreamInfo main = camera.getMainStream();
                if (main != null) {
                    createStyledCell(row, 9, main.getRtspUrl() != null ? main.getRtspUrl() : "", defaultStyle);
                    createStyledCell(row, 10, main.getResolution() != null ? main.getResolution() : "", defaultStyle);
                    createStyledCell(row, 11, main.getCodec() != null ? main.getCodec() : "", defaultStyle);
                    createStyledCell(row, 12, String.valueOf(main.getBitrate()), defaultStyle);
                    createStyledCell(row, 13, String.valueOf((int)main.getFps()), defaultStyle);
                    if (main.getError() != null) {
                        String currentError = camera.getErrorMessage();
                        camera.setErrorMessage(currentError != null ? currentError + "; Main: " + main.getError() : "Main: " + main.getError());
                    }
                }
                
                StreamInfo sub = camera.getSubStream();
                if (sub != null) {
                    createStyledCell(row, 14, sub.getRtspUrl() != null ? sub.getRtspUrl() : "", defaultStyle);
                    
                    Cell resCell = createStyledCell(row, 15, sub.getResolution() != null ? sub.getResolution() : "", defaultStyle);
                    if (!isValidSubResolution(sub.getResolution())) {
                        resCell.setCellStyle(redStyle);
                    }
                    
                    Cell codecCell = createStyledCell(row, 16, sub.getCodec() != null ? sub.getCodec() : "", defaultStyle);
                    if (!isH264(sub.getCodec())) {
                        codecCell.setCellStyle(redStyle);
                    }
                    
                    createStyledCell(row, 17, String.valueOf(sub.getBitrate()), defaultStyle);
                    createStyledCell(row, 18, String.valueOf((int)sub.getFps()), defaultStyle);
                    if (sub.getError() != null) {
                        String currentError = camera.getErrorMessage();
                        camera.setErrorMessage(currentError != null ? currentError + "; Sub: " + sub.getError() : "Sub: " + sub.getError());
                    }
                }
                
                createStyledCell(row, 19, camera.getErrorMessage() != null ? camera.getErrorMessage() : "", defaultStyle);
            }
            
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
            
            Logger.info("Excel export completed: " + filePath);
        } catch (Exception e) {
            Logger.error("Failed to export Excel", e);
            throw new RuntimeException("Export failed: " + e.getMessage());
        }
    }

    private static boolean isH264(String codec) {
        return codec != null && (codec.equalsIgnoreCase("h264") || codec.equalsIgnoreCase("avc"));
    }

    private static boolean isValidSubResolution(String resolution) {
        if (resolution == null) return false;
        try {
            String[] parts = resolution.split("x");
            int height = Integer.parseInt(parts[1]);
            return height >= 360 && height <= 480;
        } catch (Exception e) {
            return false;
        }
    }
    
    private static Cell createStyledCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
        return cell;
    }
    
    private static int compareIpAddresses(String ip1, String ip2) {
        try {
            byte[] addr1 = InetAddress.getByName(ip1).getAddress();
            byte[] addr2 = InetAddress.getByName(ip2).getAddress();
            
            for (int i = 0; i < Math.min(addr1.length, addr2.length); i++) {
                int octet1 = addr1[i] & 0xFF;
                int octet2 = addr2[i] & 0xFF;
                if (octet1 != octet2) {
                    return Integer.compare(octet1, octet2);
                }
            }
            return Integer.compare(addr1.length, addr2.length);
        } catch (Exception e) {
            return ip1.compareTo(ip2);
        }
    }
}
