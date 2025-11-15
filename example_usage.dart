import 'dart:async';
import 'lib/fastshare_plugin.dart';
import 'package:permission_handler/permission_handler.dart';

class FileSharingManager {
  StreamSubscription? _subscription;

  void init() {
    _subscription = FastSharePlugin.events.listen((event) {
      handleEvent(FastShareEvent.fromMap(event));
    });
  }

  void dispose() {
    _subscription?.cancel();
  }

  Future<void> requestPermissions() async {
    // Request location for hotspot
    var status = await Permission.location.request();
    if (status.isGranted) {
      // For Android 13+
      if (await Permission.nearbyWifiDevices.isGranted == false) {
        await Permission.nearbyWifiDevices.request();
      }
    }
  }

  void handleEvent(FastShareEvent event) {
    switch (event.event) {
      case 'log':
        // Handle colored log messages
        if (event.coloredMessage != null) {
          print('LOG [${event.data['level']}-${event.data['code']}] ${event.rawMessage}');
        }
        break;
      case 'hotspotStarted':
        print('Hotspot started');
        break;
      case 'clientConnected':
        print('Client connected');
        break;
      case 'sendingStarted':
        print('Sending ${event.data['fileName']}');
        break;
      case 'sendingProgress':
        print('Progress: ${event.data['progress']}% at ${event.data['speed']} MB/s');
        break;
      case 'sendingCompleted':
        print('Sent ${event.data['fileName']}');
        break;
      case 'receivingStarted':
        print('Receiving ${event.data['fileName']}');
        break;
      case 'receivingProgress':
        print('Receiving progress: ${event.data['progress']}% at ${event.data['speed']} MB/s');
        break;
      case 'receivingCompleted':
        print('Received ${event.data['fileName']}');
        break;
      case 'errorOccurred':
        print('Error: ${event.data['error']}');
        break;
    }
  }

  Future<void> startAsSender(List<String> filePaths) async {
    try {
      // Check and request permissions
      if (!await FastSharePlugin.checkPermissions()) {
        await requestPermissions();
      }

      // Set files to send
      await FastSharePlugin.setFilesToSend(filePaths);

      // Start hotspot
      var hotspotInfo = await FastSharePlugin.startHotspot();
      print('Hotspot started: ${hotspotInfo['ssid']} / ${hotspotInfo['password']}');

      // Start server
      await FastSharePlugin.startServer();

      // Share hotspotInfo with receiver (e.g., via QR code)
    } catch (e) {
      print('Error starting sender: $e');
    }
  }

  Future<void> startAsReceiver(Map<String, dynamic> hotspotInfo) async {
    try {
      // Connect to hotspot
      await FastSharePlugin.connectToHotspot(hotspotInfo['ssid'], hotspotInfo['password']);

      // Start receiving
      await FastSharePlugin.startReceiving(hotspotInfo['ip'], hotspotInfo['port']);
    } catch (e) {
      print('Error starting receiver: $e');
    }
  }
}