# Troubleshooting Guide

Common issues and solutions when using the FastShare Plugin.

## Permission Issues

### Problem: `checkPermissions()` returns false
**Symptoms**: Hotspot operations fail with permission errors.

**Solutions**:
1. Ensure location permission is granted
2. For Android 13+, grant "Nearby devices" permission
3. Check app manifest includes required permissions:
   ```xml
   <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
   <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
   <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
   <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
   <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
   ```

### Problem: Permission request dialog doesn't appear
**Symptoms**: `requestPermissions()` doesn't show dialog.

**Solutions**:
1. Check if permissions are already denied permanently
2. Guide user to app settings to manually grant permissions
3. Ensure rationale is shown before requesting

## Hotspot Issues

### Problem: `startHotspot()` fails
**Symptoms**: `errorOccurred` event with hotspot failure message.

**Common Causes**:
1. **Android Version**: LocalOnlyHotspot requires Android 8.0+ (API 26)
2. **Hardware**: Device doesn't support hotspot
3. **Permissions**: Missing location permissions
4. **Concurrent Hotspots**: Another hotspot already active

**Solutions**:
1. Check `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O`
2. Verify device capabilities
3. Ensure no other hotspots are active
4. Try restarting device

### Problem: Hotspot created but not visible
**Symptoms**: `hotspotStarted` event fired, but hotspot not in WiFi list.

**Solutions**:
1. LocalOnlyHotspot is not visible to other devices by design
2. Use regular hotspot for visibility (requires additional permissions)
3. Ensure receiver and sender are on same network segment

## Connection Issues

### Problem: `scanHotspots()` returns empty list
**Symptoms**: No hotspots found during scanning.

**Solutions**:
1. Ensure receiver has started hotspot
2. Check WiFi scanning permissions
3. **Verify location services are enabled** (required for WiFi scanning on Android 6+)
4. Ensure location permission is granted
5. Try manual WiFi refresh
6. If location services are disabled, the plugin will automatically prompt to enable them

### Problem: `connectToHotspot()` fails
**Symptoms**: Connection timeout or authentication failure.

**Common Causes**:
1. **Wrong Credentials**: Incorrect SSID or password
2. **Range**: Devices too far apart
3. **Interference**: WiFi congestion
4. **Device Limits**: Too many connected devices

**Solutions**:
1. Verify hotspot credentials from receiver
2. Move devices closer
3. Change WiFi channel if possible
4. Disconnect other devices from hotspot

## Transfer Issues

### Problem: File transfer is slow
**Symptoms**: Low transfer speeds reported in progress events.

**Solutions**:
1. **Distance**: Move devices closer
2. **Interference**: Avoid crowded WiFi channels
3. **File Size**: Large files take longer
4. **Network Load**: Close other network-intensive apps

### Problem: Transfer fails midway
**Symptoms**: `errorOccurred` during transfer.

**Common Causes**:
1. **Connection Lost**: Hotspot disconnected
2. **File Access**: File moved or deleted during transfer
3. **Storage**: Insufficient space on receiver
4. **Timeout**: Transfer taking too long

**Solutions**:
1. Check connection stability
2. Ensure files remain accessible
3. Verify storage space
4. Increase timeout values if needed

### Problem: Files corrupted after transfer
**Symptoms**: Received files unreadable or different size.

**Solutions**:
1. Check available storage space before transfer
2. Ensure stable connection throughout transfer
3. Verify file permissions
4. Check for antivirus interference

## Event Issues

### Problem: No events received
**Symptoms**: Event stream appears empty.

**Solutions**:
1. Ensure `FastSharePlugin.events.listen()` is called
2. Check event handler is properly set up
3. Verify plugin is initialized
4. Check for exceptions in event processing

### Problem: Events received but data malformed
**Symptoms**: JSON parsing errors in event handling.

**Solutions**:
1. Check event data structure matches documentation
2. Handle null values appropriately
3. Use try-catch around JSON operations
4. Log raw event data for debugging

## Platform-Specific Issues

### Android 13+ Issues
- Requires `NEARBY_WIFI_DEVICES` permission
- LocalOnlyHotspot behavior changes
- Enhanced permission prompts

### Android 8.0-12 Issues
- May require legacy WiFi connection methods
- Different hotspot creation APIs

### iOS Issues
- iOS has limited hotspot APIs
- May require additional entitlements
- Background execution restrictions

## Debugging Tips

1. **Enable Logging**: Check log events for detailed error messages
2. **Test Incrementally**: Test each step separately
3. **Check Device Settings**: Verify WiFi, location, and hotspot settings
4. **Restart Devices**: Clear any stuck states
5. **Update Dependencies**: Ensure latest plugin version
6. **Check Network Tools**: Use network debugging tools if available

## Getting Help

If issues persist:
1. Check the example app for correct usage
2. Review API documentation
3. Search existing issues on GitHub
4. Create detailed bug report with:
   - Device models and Android versions
   - Complete error logs
   - Steps to reproduce
   - Expected vs actual behavior