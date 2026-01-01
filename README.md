# CCTV Discovery Tool

A comprehensive Java-based application for discovering and analyzing IP cameras and NVR/DVR systems on local networks with advanced ONVIF support and multi-channel detection.

## Features

### Discovery & Detection
- **ONVIF WS-Discovery** for fast camera detection with multiple authentication methods
- **Port scanning** for additional device discovery (554, 8554, 80, 8080)
- **NVR/DVR detection** with automatic multi-channel extraction (up to 32 channels)
- **Thread-per-device processing** with CPU core-based thread pool optimization

### Authentication & Security
- **Multiple ONVIF authentication methods**: Digest, Plain Text, Basic, Anonymous
- **Credential management** with bulk assignment and retry functionality
- **Automatic fallback** between authentication methods
- **Thread-safe operations** with proper resource management

### Stream Analysis
- **RTSP URL pattern matching** with 60+ built-in patterns for major manufacturers
- **Custom pattern support** via rtsp-patterns.txt configuration file
- **Stream analysis using FFmpeg** (resolution, codec, bitrate, FPS)
- **Automatic stream probing** for both main and sub streams

### User Interface
- **Wizard-based GUI** with progress tracking and real-time counters
- **Input validation** for IP ranges and credentials
- **Error handling** with comprehensive retry mechanisms
- **Professional styling** with gradient buttons and consistent fonts

### Export & Reporting
- **Excel export** with conditional formatting and IP address sorting
- **Consolas monospaced font** for technical data readability
- **Frozen header rows** and bold formatting
- **Comprehensive camera information** including names, models, and stream details

## System Requirements

- **Java Runtime Environment (JRE) 1.8 or higher**
- **Windows Operating System** (x64)
- **Network access** to camera devices
- **Minimum 4GB RAM** recommended for large network scans

## Installation & Usage

### Quick Start
1. Download and extract `CctvDiscovery-x.x.x-windows-x64.zip`
2. Run `CctvDiscovery.exe`
3. Select network interface or enter IP range
4. Start discovery and follow the wizard

### Build from Source

#### Prerequisites
- **Java Development Kit (JDK) 1.8+**
- **Apache Maven 3.x**
- **Windows SDK** (for code signing)

#### Build Commands
```bash
# Clone repository
git clone <repository-url>
cd CctvDiscovery

# Build executable with bundled JRE
mvn clean package

# Output files:
# target/CctvDiscovery.exe                           - Signed executable
# target/CctvDiscovery-x.x.x-windows-x64.zip       - Distribution package
# target/dist/                                      - Complete distribution folder
```

## Configuration

### Custom RTSP Patterns
Create `rtsp-urls.txt` in the application directory to add custom URL patterns:

```
# Custom RTSP patterns
# Format: main_stream_path, sub_stream_path

# Hikvision NVR channels
/ISAPI/Streaming/channels/%d01, /ISAPI/Streaming/channels/%d02

# Custom manufacturer
/custom/stream/%d, /custom/substream/%d

# Single stream only
/live/channel%d
```

### Supported Manufacturers

#### IP Cameras
- **Hikvision**: DS-2CD series, multiple firmware versions
- **Dahua**: IPC-HDW, IPC-HFW series
- **Axis**: M-series, P-series cameras
- **TP-Link/Tapo**: C100, C200, C310 series
- **Foscam**: FI, R2, R4 series
- **Generic ONVIF**: Any ONVIF Profile S compliant camera

#### NVR/DVR Systems
- **Hikvision NVRs**: DS-7xxx, DS-9xxx series (up to 32 channels)
- **Dahua NVRs**: NVR4xxx, NVR5xxx series (up to 32 channels)
- **Generic ONVIF NVRs**: Multi-profile ONVIF systems
- **Pattern-based NVRs**: Standard RTSP URL patterns

## Application Workflow

### 1. Network Discovery
- **ONVIF Discovery**: Broadcasts UDP multicast to discover ONVIF devices
- **Port Scanning**: TCP connection tests on common camera ports
- **Device Detection**: Identifies cameras vs NVR/DVR systems

### 2. Credential Assignment
- **Individual Setup**: Right-click cameras to set credentials
- **Bulk Assignment**: Apply same credentials to multiple devices
- **Validation**: Username/password format checking

### 3. Authentication & Analysis
- **Multi-method Authentication**: Tries Digest → Plain Text → Anonymous
- **Stream Discovery**: Tests RTSP patterns on discovered ports only
- **NVR Channel Extraction**: Automatically detects and extracts all channels
- **Stream Analysis**: Probes video properties using FFmpeg

### 4. Results & Export
- **Retry Mechanism**: Handles failed authentications with retry options
- **Excel Export**: Generates formatted spreadsheet with all camera data
- **Error Reporting**: Comprehensive error messages and logging

## Excel Export Details

The exported Excel file contains:

| Column | Description |
|--------|-------------|
| IP Address | Device IP address (sorted numerically) |
| Manufacturer | Camera/NVR manufacturer |
| Model | Device model number |
| Camera Name | Custom camera name from ONVIF |
| Serial Number | Device serial number |
| Firmware | Firmware version |
| Time Diff (sec) | Time difference from system clock |
| Username/Password | Authentication credentials |
| Main RTSP URL | Primary stream URL |
| Main Resolution | Video resolution (e.g., 1920x1080) |
| Main Codec | Video codec (H.264, H.265, etc.) |
| Main Bitrate (kbps) | Stream bitrate |
| Main FPS | Frame rate (integer) |
| Sub RTSP URL | Secondary stream URL |
| Sub Resolution | Sub-stream resolution |
| Sub Codec | Sub-stream codec |
| Sub Bitrate (kbps) | Sub-stream bitrate |
| Sub FPS | Sub-stream frame rate (integer) |
| Error | Error messages if any |

### Conditional Formatting
- **Red highlighting** for sub-streams with:
  - Non-H.264 codecs
  - Resolution not between 360p-480p

## Logging

Application logs are written to `cctv-discovery.log` with detailed information:
- Discovery events and results
- Authentication attempts and outcomes
- RTSP URL testing progress
- Stream analysis results
- Error messages with stack traces

## Advanced Features

### Thread Management
- **CPU core-based threading**: Automatically uses available processor cores
- **Graceful shutdown**: Proper resource cleanup on application exit
- **Thread-safe operations**: Synchronized camera object access

### Error Handling
- **Comprehensive validation**: IP ranges, credentials, network interfaces
- **Retry mechanisms**: Multiple attempts for failed operations
- **Fallback strategies**: Alternative authentication and discovery methods
- **User-friendly messages**: Clear error reporting without technical jargon

### Performance Optimization
- **Port-specific testing**: Only tests RTSP URLs on discovered open ports
- **Early termination**: Stops pattern testing once streams are found
- **Progress tracking**: Real-time progress bars with completion counters
- **Memory management**: Efficient resource usage for large networks

## Troubleshooting

### Common Issues

**No cameras found:**
- Verify cameras are powered and network-connected
- Check firewall settings (UDP 3702, TCP 554/8554)
- Try port scanning as fallback method
- Ensure correct network interface selection

**Authentication failures:**
- Verify username/password combinations
- Try common defaults: admin/admin, admin/12345, admin/[blank]
- Check if ONVIF authentication is enabled on cameras
- Review camera documentation for default credentials

**Stream analysis failures:**
- Ensure RTSP ports are not blocked
- Verify camera supports RTSP streaming
- Check camera connection limits
- Add custom patterns for uncommon camera models

**Application performance:**
- Reduce IP range for large networks
- Close other applications to free memory
- Check system resources during scanning
- Review log file for detailed error information

## Security Considerations

⚠️ **Important Security Notes:**
- Exported Excel files contain **plaintext passwords**
- Store exported files securely
- Use strong, unique passwords for cameras
- Change default camera passwords immediately
- Limit network access to camera management interfaces

## Technical Architecture

### Core Components
- **ONVIF Client**: Multi-authentication SOAP client
- **Port Scanner**: Multi-threaded network port detection
- **RTSP URL Guesser**: Pattern-based stream URL discovery
- **NVR Detector**: Multi-channel system detection and extraction
- **Stream Probe**: FFmpeg-based video analysis
- **Excel Exporter**: Formatted spreadsheet generation

### Dependencies
- **JavaCV/FFmpeg**: Video stream analysis
- **Apache POI**: Excel file generation
- **SLF4J**: Logging framework
- **Swing**: User interface framework

## License

This project is proprietary software. All rights reserved.

## Support

For technical support or feature requests:
- Check application logs: `cctv-discovery.log`
- Review this documentation
- Consult camera manufacturer documentation

---

**CCTV Discovery Tool** - Professional IP Camera Discovery and Analysis Solution
