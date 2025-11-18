package com.myapp.fastshare_plugin

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject

class FastSharePlugin : FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler {

    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private lateinit var context: Context
    private var eventSink: EventChannel.EventSink? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var hotspotManager: HotspotManager
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

            else -> result.notImplemented()
        }
    }
}
