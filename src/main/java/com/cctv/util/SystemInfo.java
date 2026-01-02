package com.cctv.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class SystemInfo {
    
    public static class HostInfo {
        public String computerName;
        public String domain;
        public String workgroup;
        public String username;
        public String osName;
        public String osVersion;
        public String osArchitecture;
        public String osBuild;
        public String cpuName;
        public String cpuCores;
        public String cpuThreads;
        public String cpuSpeed;
        public String totalMemory;
        public String availableMemory;
        public String memoryUsage;
        public String diskInfo;
        public String networkAdapters;
        public String currentTime;
        public String timeZone;
        public String timeServerDiff;
        public String systemUptime;
        public String biosInfo;
        public String motherboardInfo;
    }
    
    public static HostInfo getHostInfo() {
        HostInfo info = new HostInfo();
        
        try {
            // Computer name
            info.computerName = InetAddress.getLocalHost().getHostName();
            
            // Username
            info.username = System.getProperty("user.name");
            
            // OS info
            info.osName = System.getProperty("os.name");
            info.osVersion = System.getProperty("os.version");
            
            // Current time and timezone
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            info.currentTime = sdf.format(new Date());
            info.timeZone = TimeZone.getDefault().getDisplayName();
            
            // Windows-specific info
            info.domain = getWindowsInfo("(Get-CimInstance -ClassName CIM_ComputerSystem).Domain");
            info.workgroup = getWindowsInfo("(Get-CimInstance -ClassName CIM_ComputerSystem).Workgroup");
            info.osArchitecture = getWindowsInfo("(Get-CimInstance -ClassName CIM_OperatingSystem).OSArchitecture");
            info.osBuild = getWindowsInfo("(Get-CimInstance -ClassName CIM_OperatingSystem).BuildNumber");
            getCpuInfo(info);
            getMemoryInfo(info);
            info.diskInfo = getDiskInfo();
            info.networkAdapters = getNetworkInfo();
            info.timeServerDiff = getTimeServerDiff();
            info.systemUptime = getSystemUptime();
            info.biosInfo = getBiosInfo();
            info.motherboardInfo = getMotherboardInfo();
            
        } catch (Exception e) {
            Logger.error("Failed to get system info", e);
        }
        
        return info;
    }
    
    private static String getWindowsInfo(String command) {
        try {
            String psCommand = "powershell.exe -ExecutionPolicy Bypass -Command \"" + command + "\"";
            Process process = Runtime.getRuntime().exec(psCommand);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    return line;
                }
            }
        } catch (Exception e) {
            Logger.error("Failed to get Windows info: " + command, e);
        }
        return "Unknown";
    }
    
    private static void getCpuInfo(HostInfo info) {
        try {
            info.cpuName = getWindowsInfo("(Get-CimInstance -ClassName CIM_Processor).Name");
            info.cpuCores = getWindowsInfo("(Get-CimInstance -ClassName CIM_Processor).NumberOfCores");
            info.cpuThreads = getWindowsInfo("(Get-CimInstance -ClassName CIM_Processor).NumberOfLogicalProcessors");
            info.cpuSpeed = getWindowsInfo("[math]::Round((Get-CimInstance -ClassName CIM_Processor).MaxClockSpeed/1000,2)") + " GHz";
        } catch (Exception e) {
            Logger.error("Failed to get CPU info", e);
        }
    }
    
    private static void getMemoryInfo(HostInfo info) {
        try {
            String totalMem = getWindowsInfo("[math]::Round((Get-CimInstance -ClassName CIM_ComputerSystem).TotalPhysicalMemory/1GB,2)");
            String availMem = getWindowsInfo("[math]::Round((Get-CimInstance -ClassName CIM_OperatingSystem).FreePhysicalMemory/1MB,2)");
            info.totalMemory = totalMem + " GB";
            info.availableMemory = availMem + " MB";
            
            try {
                double total = Double.parseDouble(totalMem) * 1024;
                double available = Double.parseDouble(availMem);
                double used = total - available;
                double usagePercent = (used / total) * 100;
                info.memoryUsage = String.format("%.1f%% (%.2f GB used)", usagePercent, used/1024);
            } catch (Exception e) {
                info.memoryUsage = "Unknown";
            }
        } catch (Exception e) {
            Logger.error("Failed to get memory info", e);
            info.totalMemory = "Unknown";
            info.availableMemory = "Unknown";
            info.memoryUsage = "Unknown";
        }
    }
    
    private static String getDiskInfo() {
        try {
            String psCommand = "powershell.exe -ExecutionPolicy Bypass -Command \"Get-CimInstance -ClassName CIM_LogicalDisk | Where-Object {$_.DriveType -eq 3} | ForEach-Object { $_.DeviceID + ' (' + $_.VolumeName + '): ' + [math]::Round($_.Size/1GB,1) + ' GB total, ' + [math]::Round($_.FreeSpace/1GB,1) + ' GB free (' + [math]::Round(($_.FreeSpace/$_.Size)*100,1) + '% free)' }\"";
            Process process = Runtime.getRuntime().exec(psCommand);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    if (result.length() > 0) result.append("; ");
                    result.append(line);
                }
            }
            return result.toString();
        } catch (Exception e) {
            Logger.error("Failed to get disk info", e);
        }
        return "Unknown";
    }
    
    private static String getNetworkInfo() {
        try {
            String psCommand = "powershell.exe -ExecutionPolicy Bypass -Command \"Get-CimInstance -ClassName CIM_NetworkAdapter | Where-Object {$_.NetConnectionStatus -eq 2} | ForEach-Object { $_.Name + ' (' + $_.MACAddress + ')' }\"";
            Process process = Runtime.getRuntime().exec(psCommand);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    if (result.length() > 0) result.append("; ");
                    result.append(line);
                }
            }
            return result.toString();
        } catch (Exception e) {
            Logger.error("Failed to get network info", e);
        }
        return "Unknown";
    }
    
    private static String getSystemUptime() {
        try {
            String psCommand = "powershell.exe -ExecutionPolicy Bypass -Command \"$uptime = (Get-CimInstance -ClassName CIM_OperatingSystem).LastBootUpTime; $span = (Get-Date) - $uptime; '{0} days, {1} hours, {2} minutes' -f $span.Days, $span.Hours, $span.Minutes\"";
            Process process = Runtime.getRuntime().exec(psCommand);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            if (line != null && !line.trim().isEmpty()) {
                return line.trim();
            }
        } catch (Exception e) {
            Logger.error("Failed to get system uptime", e);
        }
        return "Unknown";
    }
    
    private static String getBiosInfo() {
        try {
            String psCommand = "powershell.exe -ExecutionPolicy Bypass -Command \"$bios = Get-CimInstance -ClassName CIM_BIOSElement; $bios.Manufacturer + ' ' + $bios.Name + ' v' + $bios.Version + ' (' + $bios.ReleaseDate.ToString('yyyy-MM-dd') + ')'\"";
            Process process = Runtime.getRuntime().exec(psCommand);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            if (line != null && !line.trim().isEmpty()) {
                return line.trim();
            }
        } catch (Exception e) {
            Logger.error("Failed to get BIOS info", e);
        }
        return "Unknown";
    }
    
    private static String getMotherboardInfo() {
        try {
            String psCommand = "powershell.exe -ExecutionPolicy Bypass -Command \"$mb = Get-CimInstance -ClassName CIM_BaseBoard; $mb.Manufacturer + ' ' + $mb.Product + ' (' + $mb.SerialNumber + ')'\"";
            Process process = Runtime.getRuntime().exec(psCommand);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            if (line != null && !line.trim().isEmpty()) {
                return line.trim();
            }
        } catch (Exception e) {
            Logger.error("Failed to get motherboard info", e);
        }
        return "Unknown";
    }
    
    private static String getTimeServerDiff() {
        try {
            Process process = Runtime.getRuntime().exec("w32tm /stripchart /computer:time.windows.com /samples:1 /dataonly");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("s")) {
                    String[] parts = line.split(",");
                    for (String part : parts) {
                        if (part.contains("s") && (part.contains("+") || part.contains("-"))) {
                            String timeStr = part.trim().replace("s", "");
                            try {
                                double diff = Double.parseDouble(timeStr);
                                return String.format("%.2f seconds", diff);
                            } catch (NumberFormatException e) {
                                // Continue searching
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.error("Failed to get time server diff", e);
        }
        return "Unable to check";
    }
}