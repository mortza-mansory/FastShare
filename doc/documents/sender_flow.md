# Sender Flow (Client Device)

The sender device scans for available hotspots, connects to the receiver's hotspot, and sends files.

## ASCII Flow Diagram

```
Start
  │
  ├── Check Permissions
  │     ├── Granted? ── No ── Request Permissions ── Loop back
  │     └── Yes
  │
  ├── Scan Hotspots
  │     └── Returns: List of hotspots (ssid, bssid, level)
  │
  ├── Hotspots Available?
  │     ├── No ── Error: No hotspots found
  │     └── Yes
  │
  ├── Select Hotspot
  │     └── User/Logic selects target hotspot
  │
  ├── Connect to Hotspot
  │     └── Event: connectedToHotspot (ssid)
  │
  ├── Start Sending Files
  │     └── TCP Connection to receiver IP:port
  │
  ├── Send Files
  │     ├── Event: sendingStarted (fileName)
  │     ├── Loop: Send File Chunks
  │     │     └── Event: sendingProgress (progress %, speed MB/s)
  │     └── Event: sendingCompleted (fileName)
  │
  └── Disconnect from Hotspot
        └── Event: disconnectedFromHotspot
```

## Step-by-Step Process

1. **Permission Check**: Verify location and nearby devices permissions
2. **Hotspot Discovery**: Scan for available WiFi hotspots
3. **Hotspot Selection**: Choose the receiver's hotspot (typically named "AndroidShare_*")
4. **Connection**: Connect to selected hotspot using SSID and password
5. **File Transfer**: Establish TCP connection and send files with progress
6. **Disconnection**: Disconnect from hotspot when done

## Events Emitted

- `connectedToHotspot`: Successfully connected to hotspot
- `sendingStarted`: File transfer began
- `sendingProgress`: Transfer progress updates
- `sendingCompleted`: File transfer finished
- `disconnectedFromHotspot`: Disconnected from hotspot

## Code Example

```dart
await FastSharePlugin.checkPermissions();
// ... request if needed

var hotspots = await FastSharePlugin.scanHotspots();
if (hotspots.isNotEmpty) {
  var hotspot = hotspots[0]; // Select first
  await FastSharePlugin.connectToHotspot(hotspot['ssid'], hotspot['password']);
  await FastSharePlugin.startSending('192.168.43.1', 8080, filePaths);
}

// Later:
await FastSharePlugin.disconnectFromHotspot();
```

## Important Notes

- The receiver must share the hotspot password (e.g., via QR code)
- Default receiver IP is 192.168.43.1, port 8080
- Multiple files can be sent in one session
- Progress events provide real-time transfer status