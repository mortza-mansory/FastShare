# Overall File Sharing Flow

This document describes the complete flow of file sharing using the FastShare Plugin, where the receiver creates the hotspot and the sender connects to it.

## ASCII Flow Diagram

```
Receiver Device                    Sender Device
     |                                |
     | 1. Check Permissions           |
     |    ├── Granted? ── No ── Request Permissions
     |    └── Yes                     |
     |                                |
     | 2. Start Hotspot               |
     |    └── Emits: hotspotStarted   |
     |                                |
     | 3. Start Server                |
     |    └── Emits: serverStarted    |
     |                                |
     |                                | 1. Check Permissions
     |                                |    ├── Granted? ── No ── Request Permissions
     |                                |    └── Yes
     |                                |
     |                                | 2. Scan Hotspots
     |                                |    └── Returns list of hotspots
     |                                |
     |                                | 3. Connect to Hotspot
     |                                |    └── Emits: connectedToHotspot
     |                                |
     |                                | 4. Start Sending Files
     |                                |    └── TCP Connection Established
     |                                |
     | 4. Client Connected            |
     |    └── Emits: clientConnected  |
     |                                |
     | 5. Receive Files               |
     |    ├── Emits: receivingStarted |
     |    ├── Emits: receivingProgress|
     |    └── Emits: receivingCompleted
     |                                |
     |                                | 5. Files Sent
     |                                |    ├── Emits: sendingStarted
     |                                |    ├── Emits: sendingProgress
     |                                |    └── Emits: sendingCompleted
     |                                |
     | 6. Stop Server                 |
     |    └── Emits: serverStopped    |
     |                                |
     | 7. Stop Hotspot                |
     |    └── Emits: hotspotStopped   |
     |                                |
     |                                | 6. Disconnect from Hotspot
     |                                |    └── Emits: disconnectedFromHotspot
```

## Key Points

- **Receiver** (hotspot owner): Starts hotspot and server, waits for connections, receives files
- **Sender** (client): Scans for hotspots, connects, sends files
- All operations are asynchronous and emit events for progress tracking
- Permissions must be granted for hotspot functionality
- TCP connection uses port 8080 by default
- Hotspot IP is typically 192.168.43.1

## Event Flow

1. **Setup Phase**: Both devices check/request permissions
2. **Connection Phase**: Receiver starts hotspot/server, sender scans/connects
3. **Transfer Phase**: TCP connection established, files transferred with progress updates
4. **Cleanup Phase**: Connections closed, resources freed