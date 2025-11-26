package com.myapp.fastshare_plugin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.bluetooth.BluetoothAdapter
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import java.io.File

class FastSharePlugin : FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler, ActivityAware {

    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private lateinit var context: Context
    private var eventSink: EventChannel.EventSink? = null
    private var activity: Activity? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var hotspotManager: HotspotManager
    private lateinit var wifiDirectManager: WifiDirectManager
    private lateinit var socketServer: SocketServer
    private lateinit var socketClient: SocketClient

    private val filesToSend = mutableListOf<String>()

    // ========== SAFE EMIT TO FLUTTER ==========
    private fun emitJson(json: String) {
        mainHandler.post {
            try { eventSink?.success(json) }
            catch (_: Exception) {}
        }
    }

    private fun emit(event: String, payload: Map<String, Any?> = emptyMap()) {
        val root = JSONObject()
        val data = JSONObject()

        root.put("event", event)
        root.put("timestamp", System.currentTimeMillis())

        payload.forEach { (k, v) ->
            if (v == null) data.put(k, JSONObject.NULL) else data.put(k, v)
        }
        root.put("payload", data)

        emitJson(root.toString())
    }

    // For SocketServer and SocketClient emissions
    private fun componentEmit(ev: Any?) {
        when (ev) {
            is Map<*, *> -> {
                val clean = hashMapOf<String, Any?>()
                ev.forEach { (k, v) -> clean[k.toString()] = v }

                val e = clean["event"]?.toString() ?: "unknown"
                clean.remove("event")
                emit(e, clean)
            }
            is String -> emitJson(ev)
            else -> emit("componentEvent", mapOf("value" to ev.toString()))
        }
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext

        methodChannel = MethodChannel(binding.binaryMessenger, "fastshare_plugin")
        methodChannel.setMethodCallHandler(this)

        eventChannel = EventChannel(binding.binaryMessenger, "fastshare_events")
        eventChannel.setStreamHandler(this)

        hotspotManager = HotspotManager(context)
        wifiDirectManager = WifiDirectManager(context) { ev -> componentEmit(ev) }
        socketServer = SocketServer { ev -> componentEmit(ev) }
        socketClient  = SocketClient  { ev -> componentEmit(ev) }

        Logger.logEventCallback = { log ->
            emit("log", mapOf(
                "level" to log.level,
                "code" to log.code,
                "message" to log.coloredMessage
            ))
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        eventSink = null
        Logger.logEventCallback = null
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        emit("pluginReady")
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {

        when (call.method) {

            // ---------------- HOTSPOT ----------------
            "startHotspot" -> {
                hotspotManager.startHotspot(
                    onSuccess = { info ->
                        emit("hotspotStarted", mapOf(
                            "ssid" to info.ssid,
                            "password" to info.password,
                            "ip" to info.ip,
                            "port" to info.port
                        ))
                        result.success(null)
                    },
                    onFailure = { err ->
                        emit("errorOccurred", mapOf("message" to err))
                        result.error("HOTSPOT_FAILED", err, null)
                    }
                )
            }

            "stopHotspot" -> {
                hotspotManager.stopHotspot()
                emit("hotspotStopped")
                result.success(null)
            }

            "disconnectFromHotspot" -> {
                hotspotManager.disconnectFromHotspot()
                emit("disconnectedFromHotspot")
                result.success(null)
            }

            // ---------------- CONNECTION ----------------
            "connectToHotspot" -> {
                val ssid = call.argument<String>("ssid")
                    ?: return result.error("INVALID_ARG", "Missing ssid", null)

                val pass = call.argument<String>("password") ?: ""

                hotspotManager.connectToHotspot(
                    ssid, pass,
                    onConnected = {
                        emit("connectedToHotspot", mapOf("ssid" to ssid))
                        result.success(null)
                    },
                    onFailure = { err ->
                        emit("errorOccurred", mapOf("message" to err))
                        result.error("CONNECT_FAIL", err, null)
                    }
                )
            }

            // ---------------- SERVER ----------------
            "startServer" -> {
                val port = call.argument<Int>("port") ?: 8080

                socketServer.start(port) { err ->
                    emit("errorOccurred", mapOf("message" to err))
                }

                result.success(null)
            }

            "stopServer" -> {
                socketServer.stop()
                emit("serverStopped")
                result.success(null)
            }

            "setFilesToSend" -> {
                val list = call.argument<List<String>>("filePaths") ?: emptyList()
                filesToSend.clear()
                filesToSend.addAll(list)
                socketServer.setFiles(filesToSend)
                result.success(null)
            }

            // ---------------- CLIENT RECEIVE ----------------
            "startReceiving" -> {
                val host = call.argument<String>("host")
                    ?: return result.error("INVALID_ARG", "Host missing", null)

                val port = call.argument<Int>("port") ?: 8080

                socketClient.connectAndReceive(host, port) { err ->
                    emit("errorOccurred", mapOf("message" to err))
                }

                result.success(null)
            }

            "startSending" -> {
                val host = call.argument<String>("host")
                    ?: return result.error("INVALID_ARG", "Missing host", null)

                val port = call.argument<Int>("port") ?: 8080

                val filePaths = call.argument<List<String>>("filePaths") ?: emptyList()

                socketClient.connectAndSend(host, port, filePaths) { err ->
                    emit("errorOccurred", mapOf("message" to err))
                }

                result.success(null)
            }

            "enableWifi" -> {
                hotspotManager.enableWifi(
                    onSuccess = {
                        result.success(null)
                    },
                    onFailure = { err ->
                        emit("errorOccurred", mapOf("message" to err))
                        result.error("ENABLE_WIFI_FAILED", err, null)
                    }
                )
            }

            "scanHotspots" -> {
                hotspotManager.scanHotspots(
                    onSuccess = { hotspots ->
                        result.success(hotspots)
                    },
                    onFailure = { err ->
                        emit("errorOccurred", mapOf("message" to err))
                        result.error("SCAN_FAILED", err, null)
                    }
                )
            }

            "sendFileViaBluetooth" -> {
                val filePath = call.argument<String>("filePath")
                if (filePath != null) {
                    sendFileViaBluetooth(filePath)
                    result.success(null)
                } else {
                    result.error("INVALID_ARGUMENT", "filePath not provided", null)
                }
            }

            // ---------------- WIFI DIRECT ----------------
            "startWifiDirect" -> {
                wifiDirectManager.startWifiDirect()
                result.success(null)
            }

            "stopWifiDirect" -> {
                wifiDirectManager.stopWifiDirect()
                result.success(null)
            }

            "discoverWifiDirectPeers" -> {
                wifiDirectManager.discoverPeers()
                result.success(null)
            }

            "connectToWifiDirectPeer" -> {
                val deviceAddress = call.argument<String>("deviceAddress")
                    ?: return result.error("INVALID_ARG", "Missing deviceAddress", null)
                wifiDirectManager.connectToPeer(deviceAddress)
                result.success(null)
            }

            "getWifiDirectPeers" -> {
                val peers = wifiDirectManager.getPeers()
                result.success(peers)
            }

            else -> result.notImplemented()
        }
    }

    private fun sendFileViaBluetooth(filePath: String) {
        val originalFile = File(filePath)
        if (!originalFile.exists()) {
            Logger.error("BLUETOOTH_SEND", "File does not exist: $filePath")
            return
        }

        try {
            // Copy file to external cache directory for sharing
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val tempFile = File(cacheDir, "sharethis_temp.apk")
            originalFile.copyTo(tempFile, overwrite = true)

            val uri = FileProvider.getUriForFile(context, "com.myapp.fastshare_plugin.fileprovider", tempFile)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive" // For APK
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Start the activity to choose Bluetooth device
            activity?.startActivity(Intent.createChooser(intent, "Send via Bluetooth"))
                ?: Logger.error("BLUETOOTH_SEND", "Activity is null, cannot start Bluetooth send")

            // Clean up temp file after a delay
            Handler(Looper.getMainLooper()).postDelayed({
                tempFile.delete()
            }, 30000) // Delete after 30 seconds

        } catch (e: Exception) {
            Logger.error("BLUETOOTH_SEND", "Failed to prepare file for sharing: ${e.message}")
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }
}
