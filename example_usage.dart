/*
  FastShare Plugin Example Usage

  This example demonstrates how to use the FastSharePlugin to send and receive files
  over a local hotspot connection. It includes initialization, event handling, and
  starting/stopping sender and receiver modes.

  Note: This is a simplified example for demonstration purposes. In a real application,
  you would implement proper UI and error handling.
  You can use documents such as diagrams, charts, markdown files as helper this is a very basic usage of the fastshare_plugin.

  github: https://github.com/mortza-mansory/FastShare
  Version: 0.0.1
  Creator: mortza mansory
*/

import 'package:fastshare_plugin/fastshare_plugin.dart';
import 'dart:async';

void main() async {
  final manager = FileSharingManager();
  manager.init();

  // Example as sender (client that sends files)
  // await manager.startAsSender(['/path/to/file1.txt', '/path/to/file2.jpg']);

  // Example as receiver (hotspot owner that receives files)
  // await manager.startAsReceiver();

  // Keep the app running to listen to events
  await Future.delayed(Duration(seconds: 30));
  manager.dispose();
}

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
        await FastSharePlugin.requestPermissions();
      }

      // Scan for available hotspots
      var hotspots = await FastSharePlugin.scanHotspots();
      if (hotspots.isEmpty) {
        print('No hotspots found');
        return;
      }

      // Connect to the first hotspot (in real app, let user choose)
      var hotspot = hotspots[0];
      await FastSharePlugin.connectToHotspot(hotspot['ssid'], hotspot['password'] ?? '');

      // Start sending files to the hotspot IP (assume default)
      await FastSharePlugin.startSending('192.168.43.1', 8080, filePaths);

      print('Sending started.');
    } catch (e) {
      print('Error starting sender: $e');
    }
  }

  Future<void> startAsReceiver() async {
    try {
      // Check and request permissions
      if (!await FastSharePlugin.checkPermissions()) {
        await FastSharePlugin.requestPermissions();
      }

      // Start hotspot
      await FastSharePlugin.startHotspot();

      // Start server to receive files
      await FastSharePlugin.startServer();

      print('Receiver started. Hotspot is active, waiting for connections.');
    } catch (e) {
      print('Error starting receiver: $e');
    }
  }

  Future<void> stopAsReceiver() async {
    try {
      // Stop server
      await FastSharePlugin.stopServer();

      // Stop hotspot
      await FastSharePlugin.stopHotspot();

      print('Receiver stopped.');
    } catch (e) {
      print('Error stopping receiver: $e');
    }
  }

  Future<void> stopAsSender() async {
    try {
      // Disconnect from hotspot
      await FastSharePlugin.disconnectFromHotspot();

      print('Sender stopped.');
    } catch (e) {
      print('Error stopping sender: $e');
    }
  }
}