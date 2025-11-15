import 'dart:async';
import 'package:flutter/services.dart';

class FastSharePlugin {
  static const MethodChannel _channel = MethodChannel('fastshare_plugin');
  static const EventChannel _eventChannel = EventChannel('fastshare_events');

  static Stream<Map<String, dynamic>>? _eventStream;
  static Stream<Map<String, dynamic>> get events {
    _eventStream ??= _eventChannel.receiveBroadcastStream().map((event) => Map<String, dynamic>.from(event));
    return _eventStream!;
  }

  static Future<bool> checkPermissions() async {
    final result = await _channel.invokeMethod('checkPermissions');
    return result ?? false;
  }

  static Future<void> requestPermissions() async {
    await _channel.invokeMethod('requestPermissions');
  }

  static Future<Map<String, dynamic>> startHotspot() async {
    final result = await _channel.invokeMethod('startHotspot');
    return Map<String, dynamic>.from(result);
  }

  static Future<void> stopHotspot() async {
    await _channel.invokeMethod('stopHotspot');
  }

  static Future<void> startServer() async {
    await _channel.invokeMethod('startServer');
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
}

class FastShareEvent {
  final String event;
  final Map<String, dynamic> data;
  final String? coloredMessage;
  final String? rawMessage;

  FastShareEvent(this.event, this.data, {this.coloredMessage, this.rawMessage});

  factory FastShareEvent.fromMap(Map<String, dynamic> map) {
    return FastShareEvent(
      map['event'],
      map..remove('event')..remove('coloredMessage')..remove('rawMessage'),
      coloredMessage: map['coloredMessage'],
      rawMessage: map['rawMessage'],
    );
  }
}