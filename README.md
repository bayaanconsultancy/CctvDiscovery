# CCTV Discovery Tool

A Java-based application for discovering and analyzing IP cameras on local networks.

## Features

- ONVIF WS-Discovery for fast camera detection
- Port scanning for additional device discovery
- Credential management with bulk assignment
- Stream analysis using FFmpeg (resolution, codec, bitrate, FPS)
- Excel export with conditional formatting

## Requirements

- Java 1.8 or higher
- Maven 3.x
- Launch4j (for creating Windows executable)

## Build Instructions

1. Build the JAR with dependencies:
```
mvn clean package
```

2. Create Windows executable using Launch4j:
```
launch4jc launch4j-config.xml
```

The executable will be created at `target\CctvDiscovery.exe`

## Usage

1. Run the application
2. Select a network interface or enter an IP range
3. Wait for ONVIF discovery to complete
4. Optionally perform port scan for additional devices
5. Assign credentials to discovered cameras
6. Wait for stream analysis to complete
7. Export results to Excel

## Log File

Application logs are written to `cctv-discovery.log` in the application directory.

## Excel Output

The exported Excel file contains:
- IP Address
- Username/Password
- Main stream details (URL, resolution, codec, bitrate, FPS)
- Sub stream details (URL, resolution, codec, bitrate, FPS)
- Error messages (if any)

Sub-stream cells are highlighted in red if:
- Codec is not H.264
- Resolution is not between 360p and 480p
