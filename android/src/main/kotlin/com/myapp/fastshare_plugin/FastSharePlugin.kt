package com.myapp.fastshare_plugin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class FastSharePlugin : FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler {
    private lateinit var channel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private lateinit var context: Context
    private var eventSink: EventChannel.EventSink? = null

    private lateinit var hotspotManager: HotspotManager
    private lateinit var socketServer: SocketServer
    private lateinit var socketClient: SocketClient
    private var filesToSend = mutableListOf<String>()

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "fastshare_plugin")
        channel.setMethodCallHandler(this)

        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "fastshare_events")
        eventChannel.setStreamHandler(this)

        // Set up logging callback to send logs to Flutter
        Logger.logEventCallback = { logEvent -> sendLogEvent(logEvent) }

        hotspotManager = HotspotManager(context)
        socketServer = SocketServer(eventSink)
        socketClient = SocketClient(eventSink)

        Logger.success("PLUGIN_INIT", "FastShare plugin initialized successfully")
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        Logger.debug("PLUGIN_DETACH", "Detaching FastShare plugin from engine")
        channel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        socketServer.stop()
        hotspotManager.stopHotspot()
        Logger.logEventCallback = null
        Logger.success("PLUGIN_DETACHED", "FastShare plugin detached successfully")
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "checkPermissions" -> checkPermissions(result)
            "requestPermissions" -> requestPermissions(result)
            "startHotspot" -> startHotspot(result)
            "stopHotspot" -> stopHotspot(result)
            "startServer" -> startServer(result)
            "stopServer" -> stopServer(result)
            "connectToHotspot" -> connectToHotspot(call, result)
            "setFilesToSend" -> setFilesToSend(call, result)
            "startReceiving" -> startReceiving(call, result)
            else -> result.notImplemented()
        }
    }

    private fun checkPermissions(result: MethodChannel.Result) {
        Logger.debug("PLUGIN_CHECK_PERMISSIONS", "Checking required permissions")
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        permissions.add(Manifest.permission.ACCESS_WIFI_STATE)
        permissions.add(Manifest.permission.CHANGE_WIFI_STATE)

        val granted = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        Logger.debug("PLUGIN_PERMISSIONS_STATUS", "Permissions granted: $granted")
        result.success(granted)
    }

    private fun requestPermissions(result: MethodChannel.Result) {
        Logger.debug("PLUGIN_REQUEST_PERMISSIONS", "Permission request initiated (handled in Flutter)")
        // Note: Actual permission request should be handled in Flutter
        result.success(null)
    }

    private fun startHotspot(result: MethodChannel.Result) {
        Logger.debug("PLUGIN_START_HOTSPOT", "Starting hotspot via HotspotManager")
        hotspotManager.startHotspot(
            onSuccess = { info ->
                Logger.success("PLUGIN_HOTSPOT_SUCCESS", "Hotspot started: ${info.ssid}")
                result.success(mapOf(
                    "ssid" to info.ssid,
                    "password" to info.password,
                    "ip" to info.ip,
                    "port" to info.port
                ))
                eventSink?.success(mapOf("event" to "hotspotStarted", "info" to info))
            },
            onFailure = { error ->
                Logger.error("PLUGIN_HOTSPOT_FAILED", "Hotspot start failed: $error")
                result.error("HOTSPOT_FAILED", error, null)
                eventSink?.success(mapOf("event" to "hotspotFailed", "error" to error))
            }
        )
    }

    private fun stopHotspot(result: MethodChannel.Result) {
        Logger.debug("PLUGIN_STOP_HOTSPOT", "Stopping hotspot")
        hotspotManager.stopHotspot()
        result.success(null)
    }

    private fun startServer(result: MethodChannel.Result) {
        val port = 8080 // or from call
        Logger.debug("PLUGIN_START_SERVER", "Starting TCP server on port $port")
        socketServer.start(port) { error ->
            Logger.error("PLUGIN_SERVER_FAILED", "Server start failed: $error")
            result.error("SERVER_FAILED", error, null)
        }
        result.success(null)
    }

    private fun stopServer(result: MethodChannel.Result) {
        Logger.debug("PLUGIN_STOP_SERVER", "Stopping TCP server")
        socketServer.stop()
        result.success(null)
    }

    private fun connectToHotspot(call: MethodCall, result: MethodChannel.Result) {
        val ssid = call.argument<String>("ssid") ?: return result.error("INVALID_ARG", "SSID required", null)
        val password = call.argument<String>("password") ?: return result.error("INVALID_ARG", "Password required", null)
        Logger.debug("PLUGIN_CONNECT_HOTSPOT", "Connecting to hotspot: $ssid")
        hotspotManager.connectToHotspot(ssid, password,
            onConnected = {
                Logger.success("PLUGIN_CONNECT_SUCCESS", "Connected to hotspot: $ssid")
                result.success(null)
            },
            onFailure = { error ->
                Logger.error("PLUGIN_CONNECT_FAILED", "Failed to connect to hotspot: $error")
                result.error("CONNECT_FAILED", error, null)
            }
        )
    }

    private fun setFilesToSend(call: MethodCall, result: MethodChannel.Result) {
        val paths = call.argument<List<String>>("filePaths")
        Logger.debug("PLUGIN_SET_FILES", "Setting ${paths?.size ?: 0} files to send")
        if (paths != null) {
            filesToSend.clear()
            filesToSend.addAll(paths)
            socketServer.filesToSend.clear()
            socketServer.filesToSend.addAll(paths)
            Logger.verbose("PLUGIN_FILES_CONFIGURED", "Files configured for sending")
        }
        result.success(null)
    }

    private fun startReceiving(call: MethodCall, result: MethodChannel.Result) {
        val host = call.argument<String>("host") ?: return result.error("INVALID_ARG", "Host required", null)
        val port = call.argument<Int>("port") ?: 8080
        Logger.debug("PLUGIN_START_RECEIVING", "Starting file reception from $host:$port")
        socketClient.connectAndReceive(host, port) { error ->
            Logger.error("PLUGIN_RECEIVE_FAILED", "File reception failed: $error")
            result.error("RECEIVE_FAILED", error, null)
        }
        result.success(null)
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        Logger.debug("PLUGIN_EVENT_LISTEN", "Event channel listener attached")
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
        Logger.debug("PLUGIN_EVENT_CANCEL", "Event channel listener detached")
    }

    // Method to send log events to Flutter
    fun sendLogEvent(logEvent: Logger.LogEvent) {
        eventSink?.success(mapOf(
            "event" to "log",
            "level" to logEvent.level,
            "code" to logEvent.code,
            "coloredMessage" to logEvent.coloredMessage,
            "rawMessage" to logEvent.rawMessage
        ))
    }
}