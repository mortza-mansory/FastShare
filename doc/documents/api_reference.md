# FastShare Plugin API Reference

Complete reference for the FastShare Flutter plugin methods and events.

## Methods

### Permission Management

#### `checkPermissions() -> Future<bool>`
Checks if necessary permissions are granted.
- **Returns**: `true` if permissions are granted, `false` otherwise

#### `requestPermissions() -> Future<void>`
Requests necessary permissions from the user.
- Location permission for hotspot scanning
- Nearby devices permission for WiFi operations

### Hotspot Management

#### `startHotspot() -> Future<void>`
Starts a local-only WiFi hotspot.
- **Events**: `hotspotStarted` with hotspot details
- **Throws**: Error if hotspot creation fails

#### `stopHotspot() -> Future<void>`
Stops the active hotspot.
- **Events**: `hotspotStopped`

#### `connectToHotspot(String ssid, String password) -> Future<void>`
Connects to a WiFi hotspot.
- **Parameters**:
  - `ssid`: Network name
  - `password`: Network password
- **Events**: `connectedToHotspot`

#### `disconnectFromHotspot() -> Future<void>`
Disconnects from the current hotspot.
- **Events**: `disconnectedFromHotspot`

#### `scanHotspots() -> Future<List<Map<String, dynamic>>>`
Scans for available WiFi hotspots.
- **Returns**: List of hotspots with `ssid`, `bssid`, `level`

### Server/Client Operations

#### `startServer({int port = 8080}) -> Future<void>`
Starts the TCP server for receiving files.
- **Parameters**:
  - `port`: Port to listen on (default: 8080)
- **Events**: `serverStarted`

#### `stopServer() -> Future<void>`
Stops the TCP server.
- **Events**: `serverStopped`

#### `startSending(String host, int port, List<String> filePaths) -> Future<void>`
Connects to server and sends files.
- **Parameters**:
  - `host`: Server IP address
  - `port`: Server port
  - `filePaths`: List of file paths to send
- **Events**: `sendingStarted`, `sendingProgress`, `sendingCompleted`

#### `startReceiving(String host, int port) -> Future<void>` (Legacy)
Legacy method for receiving files.
- **Note**: Use `startSending` for sending, server handles receiving

### File Management

#### `setFilesToSend(List<String> filePaths) -> Future<void>`
Sets the list of files to send (for server).
- **Parameters**:
  - `filePaths`: List of file paths

## Events

All events are received through the `FastSharePlugin.events` stream.

### Common Events

#### `pluginReady`
Plugin initialized and ready.

#### `log`
Log message with level, code, and colored message.

### Hotspot Events

#### `hotspotStarted`
Hotspot created successfully.
```json
{
  "event": "hotspotStarted",
  "data": {
    "ssid": "AndroidShare_1234",
    "password": "12345678",
    "ip": "192.168.43.1",
    "port": 8080
  }
}
```

#### `hotspotStopped`
Hotspot stopped.

#### `connectedToHotspot`
Connected to hotspot.
```json
{
  "event": "connectedToHotspot",
  "data": { "ssid": "AndroidShare_1234" }
}
```

#### `disconnectedFromHotspot`
Disconnected from hotspot.

### Server Events

#### `serverStarted`
Server started listening.

#### `serverStopped`
Server stopped.

#### `clientConnected`
New client connected.
```json
{
  "event": "clientConnected",
  "data": { "address": "192.168.43.2" }
}
```

### Transfer Events

#### `sendingStarted`
File sending started.
```json
{
  "event": "sendingStarted",
  "data": { "fileName": "document.pdf" }
}
```

#### `sendingProgress`
Sending progress update.
```json
{
  "event": "sendingProgress",
  "data": {
    "progress": 45,
    "speed": 2.5
  }
}
```

#### `sendingCompleted`
File sending completed.
```json
{
  "event": "sendingCompleted",
  "data": { "fileName": "document.pdf" }
}
```

#### `receivingStarted`
File receiving started.
```json
{
  "event": "receivingStarted",
  "data": { "fileName": "image.jpg" }
}
```

#### `receivingProgress`
Receiving progress update.
```json
{
  "event": "receivingProgress",
  "data": {
    "progress": 78,
    "speed": 3.1
  }
}
```

#### `receivingCompleted`
File receiving completed.
```json
{
  "event": "receivingCompleted",
  "data": { "fileName": "image.jpg" }
}
```

### Error Events

#### `errorOccurred`
An error occurred.
```json
{
  "event": "errorOccurred",
  "data": {
    "message": "Failed to start hotspot"
  }
}
```

## Usage Patterns

### As Receiver (Hotspot Owner)
```dart
// Setup
await FastSharePlugin.checkPermissions();
await FastSharePlugin.startHotspot();
await FastSharePlugin.startServer();

// Listen to events...

// Cleanup
await FastSharePlugin.stopServer();
await FastSharePlugin.stopHotspot();
```

### As Sender (Client)
```dart
// Setup
await FastSharePlugin.checkPermissions();
var hotspots = await FastSharePlugin.scanHotspots();
await FastSharePlugin.connectToHotspot(ssid, password);
await FastSharePlugin.startSending(host, port, files);

// Listen to events...

// Cleanup
await FastSharePlugin.disconnectFromHotspot();