# FastShare Plugin

**Version: 0.0.2**

A Flutter plugin for ultra-high-speed offline file sharing using Android Hotspot and TCP sockets.

## Features

- LocalOnlyHotspot creation
- Automatic receiver connection
- TCP socket-based file transfer
- Support for any file type and size
- Resume support (basic)
- CRC32 checksum verification
- Progress callbacks
- High-speed optimizations (large buffers, TCP_NODELAY, etc.)

## Integration

1. Add the plugin to your Flutter project:
   - Copy the `fastshare_plugin` directory to your project's root or plugins folder.

  ```yaml
  dependencies:
    fastshare_plugin: ^0.0.1
  ```

or use github repo and locally add to pat for lasted version:

  ```yaml
  dependencies:
    fastshare_plugin:
      path: ./fastshare_plugin
  ```

2. For Android:
   - Ensure your `android/app/src/main/AndroidManifest.xml` includes the permissions from `fastshare_plugin/android/src/main/AndroidManifest.xml`.
   - The plugin registers itself automatically.

3. Import in Dart:
   ```dart
   import 'package:fastshare_plugin/fastshare_plugin.dart';
   ```

4. Request permissions (use permission_handler package):
   ```dart
   import 'package:permission_handler/permission_handler.dart';

   await Permission.location.request();
   if (Platform.isAndroid && await Permission.nearbyWifiDevices.status.isDenied) {
     await Permission.nearbyWifiDevices.request();
   }
   ```

5. Usage example:
   See `example_usage.dart`

### For documents:
-Use doc folder you can see planty of charts diagrams, md files and other helpers witch already ready for your usage.
## API

### Methods

- `checkPermissions()`: Check if required permissions are granted.
- `requestPermissions()`: Request permissions (handled in Flutter).
- `startHotspot()`: Start local hotspot, returns Map with ssid, password, ip, port.
- `stopHotspot()`: Stop hotspot.
- `startServer()`: Start TCP server.
- `stopServer()`: Stop server.
- `connectToHotspot(ssid, password)`: Connect receiver to hotspot.
- `setFilesToSend(filePaths)`: Set files to send (on sender).
- `startReceiving(host, port)`: Start receiving files (on receiver).

### Events

Listen to `FastSharePlugin.events` Stream for:

#### Transfer Events
- `hotspotStarted`
- `hotspotFailed`
- `serverStarted`
- `clientConnected`
- `sendingStarted`
- `sendingProgress` (with progress, speed)
- `sendingCompleted`
- `receivingStarted`
- `receivingProgress`
- `receivingCompleted`
- `errorOccurred`

#### Log Events
- `log` - Colored log messages with the following data:
  - `level`: "ERROR", "SUCCESS", "WARN", "DEBUG", "VERBOSE"
  - `code`: Error code (e.g., "HOTSPOT_SUCCESS", "CRC_MISMATCH")
  - `coloredMessage`: ANSI-colored log message for terminal display
  - `rawMessage`: Plain text message for UI display

Log format: `[LEVEL-FastShare-CODE] \x1B[COLORmMESSAGE\x1B[0m`

## AndroidManifest Permissions

Add to your `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" android:minSdkVersion="33" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

## Gradle Notes

No special Gradle configuration needed. The plugin uses standard Android APIs.

## Logging System

The plugin includes comprehensive ANSI-colored logging with the following features:

### Log Levels & Colors
- **ERROR** (Dark Red `\x1B[31m`): Critical errors and failures
- **SUCCESS** (Dark Green `\x1B[32m`): Successful operations
- **WARNING** (White/Gray `\x1B[37m`): Warnings and non-critical issues
- **DEBUG** (Blue `\x1B[34m`): Detailed debugging information
- **VERBOSE** (Magenta `\x1B[35m`): Very detailed internal operations

### Log Format
```
[LEVEL-FastShare-ERROR_CODE] \x1B[COLORmMESSAGE\x1B[0m
```

### Examples
```
[ERROR-FastShare-CRC_FAIL] \x1B[31mCRC mismatch detected in chunk 42\x1B[0m
[SUCCESS-FastShare-SEND_OK] \x1B[32mFile sent successfully\x1B[0m
[WARN-FastShare-NET_RETRY] \x1B[37mRetrying socket connection...\x1B[0m
[DEBUG-FastShare-CHUNK_SEND] \x1B[34mSending chunk of size 512KB\x1B[0m
[VERBOSE-FastShare-TUNING] \x1B[35mAdaptive chunk tuning: +256KB\x1B[0m
```

### Log Events in Flutter
Logs are sent to Flutter via the event stream with `event: "log"` containing:
- `level`: Log level string
- `code`: Error/action code
- `coloredMessage`: Full ANSI-colored message
- `rawMessage`: Plain text for UI display

## Notes

- Tested on Android 8-14.
- Files are saved to `/sdcard/Download/fastshare/`.
- For high speeds, ensure devices are close and no interference.
- Resume is basic; full resume on disconnect not implemented.
- Multi-stream parallel sending can be added by modifying FileSender to use multiple sockets.


''**Creator:mortza manory**''