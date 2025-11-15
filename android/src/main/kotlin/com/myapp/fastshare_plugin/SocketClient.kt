package com.myapp.fastshare_plugin

import android.util.Log
import io.flutter.plugin.common.EventChannel
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class SocketClient(private val eventSink: EventChannel.EventSink?) {
    private val executor = Executors.newSingleThreadExecutor()

    fun connectAndReceive(host: String, port: Int, onError: (String) -> Unit) {
        Logger.debug("CLIENT_CONNECT", "Attempting to connect to $host:$port")
        executor.execute {
            var socket: Socket? = null
            try {
                Logger.verbose("CLIENT_SOCKET_CREATE", "Creating TCP socket")
                socket = Socket()
                Logger.debug("CLIENT_CONNECTING", "Connecting to server...")
                socket.connect(InetSocketAddress(host, port), Protocol.TIMEOUT_MS.toInt())
                Logger.success("CLIENT_CONNECTED", "Successfully connected to $host:$port")

                socket.tcpNoDelay = true
                socket.receiveBufferSize = Protocol.BUFFER_SIZE
                socket.sendBufferSize = Protocol.BUFFER_SIZE
                Logger.verbose("CLIENT_SOCKET_OPTIMIZED", "Socket optimized with TCP_NODELAY and ${Protocol.BUFFER_SIZE}KB buffers")

                // Receive and handle files
                val fileReceiver = FileReceiver(socket, eventSink)
                Logger.debug("CLIENT_START_RECEIVE", "Starting file reception")
                fileReceiver.receiveFiles()
                Logger.success("CLIENT_RECEIVE_COMPLETE", "File reception completed successfully")

            } catch (e: Exception) {
                Logger.error("CLIENT_ERROR", "Connection/reception error: ${e.message}")
                onError(e.message ?: "Unknown error")
                eventSink?.success(mapOf("event" to "errorOccurred", "error" to e.message))
            } finally {
                Logger.verbose("CLIENT_SOCKET_CLOSE", "Closing client socket")
                socket?.close()
            }
        }
    }
}