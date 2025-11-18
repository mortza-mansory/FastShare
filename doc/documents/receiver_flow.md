# Receiver Flow (Hotspot Owner)

The receiver device creates the WiFi hotspot and TCP server to receive files from sender devices.

## ASCII Flow Diagram

```
Start
  │
  ├── Check Permissions
  │     ├── Granted? ── No ── Request Permissions ── Loop back
  │     └── Yes
  │
  ├── Start Hotspot
  │     └── Event: hotspotStarted (ssid, password, ip, port)
  │
  ├── Start Server (Port 8080)
  │     └── Event: serverStarted
  │
  ├── Wait for Client Connection
  │     └── Event: clientConnected (client IP)
  │
  ├── Handle Client Connection
  │     ├── Event: receivingStarted (fileName)
  │     ├── Loop: Receive File Chunks
  │     │     └── Event: receivingProgress (progress %, speed MB/s)
  │     └── Event: receivingCompleted (fileName)
  │
  ├── More Clients? ── Yes ── Wait for Next Client
  │
  └── Stop Server
        └── Event: serverStopped
        │
        └── Stop Hotspot
              └── Event: hotspotStopped
```

## Step-by-Step Process

1. **Permission Check**: Verify location and nearby devices permissions
2. **Hotspot Creation**: Create local-only hotspot with generated SSID/password
3. **Server Startup**: Start TCP server on port 8080
4. **Connection Waiting**: Listen for incoming client connections
5. **File Reception**: Handle multiple file transfers with progress updates
6. **Cleanup**: Stop server and hotspot when done

## Events Emitted

- `hotspotStarted`: Hotspot created with credentials
- `serverStarted`: Server listening on port
- `clientConnected`: New client connected
- `receivingStarted`: File transfer began
- `receivingProgress`: Transfer progress updates
- `receivingCompleted`: File transfer finished
- `serverStopped`: Server shut down
- `hotspotStopped`: Hotspot disabled

## Code Example

```dart
await FastSharePlugin.checkPermissions();
// ... request if needed
await FastSharePlugin.startHotspot();
await FastSharePlugin.startServer();

// Listen for events...
// Later:
await FastSharePlugin.stopServer();
await FastSharePlugin.stopHotspot();