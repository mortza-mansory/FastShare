## 0.0.2

### Bug Fixes and Improvements

- **Fixed SSID Generation**: Corrected hotspot SSID generation to use Android's default "AndroidShare_" prefix instead of custom names
- **Added Location Services Check**: WiFi scanning now validates that location services are enabled (required for Android 6+)
- **Improved WiFi Enabling**: Added fallback to open WiFi settings when programmatic WiFi enable fails
- **Enhanced Error Handling**: Better user guidance when permissions or services are unavailable

### Technical Details
- Hotspot SSID now uses Android's system-generated "AndroidShare_XXXX" format
- Scan filter updated to match correct SSID prefix
- Location services validation prevents scanning failures
- WiFi settings prompt for manual enable when programmatic enable fails

## 0.0.1

### Initial Release

- Android implementation for high-speed offline file sharing using Hotspot + TCP sockets.
- Local-only hotspot creation, connection management, and IP detection.
- Custom file transfer protocol with headers, chunking, CRC32 validation, and progress reporting.
- TCP server and client for sending and receiving files with event-based updates.
- Logging system with structured and colored log events forwarded to Flutter.
