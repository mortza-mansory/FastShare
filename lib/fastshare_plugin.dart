import 'dart:async';
import 'dart:convert';
import 'package:flutter/services.dart';

class FastSharePlugin {
  static const MethodChannel _channel = MethodChannel('fastshare_plugin');
  static const EventChannel _eventChannel = EventChannel('fastshare_events');

  static Stream<Map<String, dynamic>>? _eventStream;

  static Stream<Map<String, dynamic>> get events {
    _eventStream ??= _eventChannel
        .receiveBroadcastStream()
        .map((raw) {
      try {
        if (raw is String) {
          return jsonDecode(raw) as Map<String, dynamic>;
        }
        return <String, dynamic>{};
      } catch (e) {
        print('FastSharePlugin JSON decode error: $e');
        return <String, dynamic>{};
      }
    });

    return _eventStream!;
  }

  // ===================== METHOD CALLS =====================

  static Future<bool> checkPermissions() async {
    final result = await _channel.invokeMethod('checkPermissions');
    return result ?? false;
  }

  static Future<void> requestPermissions() async {
    await _channel.invokeMethod('requestPermissions');
  }

  static Future<void> startHotspot() async {
    await _channel.invokeMethod('startHotspot');
  }

  static Future<void> stopHotspot() async {
    await _channel.invokeMethod('stopHotspot');
  }

  static Future<void> disconnectFromHotspot() async {
    await _channel.invokeMethod('disconnectFromHotspot');
  }

  static Future<void> startServer({int port = 8080}) async {
    await _channel.invokeMethod('startServer', {"port": port});
  }

  static Future<void> stopServer() async {
    await _channel.invokeMethod('stopServer');
  }

  static Future<void> connectToHotspot(String ssid, String password) async {
    await _channel.invokeMethod('connectToHotspot', {
      'ssid': ssid,
      'password': password,
    });
  }

  static Future<void> setFilesToSend(List<String> filePaths) async {
    await _channel.invokeMethod('setFilesToSend', {
      'filePaths': filePaths,
    });
  }

  static Future<void> startReceiving(String host, int port) async {
    await _channel.invokeMethod('startReceiving', {
      'host': host,
      'port': port,
    });
  }

  static Future<void> startSending(String host, int port, List<String> filePaths) async {
    await _channel.invokeMethod('startSending', {
      'host': host,
      'port': port,
      'filePaths': filePaths,
    });
  }

  static Future<List<Map<String, dynamic>>> scanHotspots() async {
    final result = await _channel.invokeMethod('scanHotspots');
    return (result as List<dynamic>).map((e) => e as Map<String, dynamic>).toList();
  }
}

// ===========================================================
// OPTIONAL – IF YOU USE FastShareEvent
// ===========================================================

class FastShareEvent {
  final String event;
  final Map<String, dynamic> data;
  final String? coloredMessage;
  final String? rawMessage;

  FastShareEvent(this.event, this.data, {this.coloredMessage, this.rawMessage});

  factory FastShareEvent.fromMap(Map<String, dynamic> map) {
    final copy = Map<String, dynamic>.from(map);

    final event = copy.remove('event') ?? 'unknown';
    final colored = copy.remove('coloredMessage');
    final raw = copy.remove('rawMessage');

    return FastShareEvent(
      event,
      copy,
      coloredMessage: colored,
      rawMessage: raw,
    );
  }
}
