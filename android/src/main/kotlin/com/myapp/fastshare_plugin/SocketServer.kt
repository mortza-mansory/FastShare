package com.myapp.fastshare_plugin

import android.util.Log
import io.flutter.plugin.common.EventChannel
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class SocketServer(private val eventSink: EventChannel.EventSink?) {
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private var isRunning = false
    var filesToSend = mutableListOf<String>()

    fun start(port: Int, onError: (String) -> Unit) {
        Logger.debug("SERVER_START", "Attempting to start TCP server on port $port")
        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            Logger.success("SERVER_STARTED", "TCP server started successfully on port $port")
            eventSink?.success(mapOf("event" to "serverStarted", "port" to port))
            executor.execute {
                Logger.verbose("SERVER_LISTEN_LOOP", "Entering client accept loop")
                while (isRunning) {
                    try {
                        Logger.debug("SERVER_WAIT_CLIENT", "Waiting for client connection...")
                        val clientSocket = serverSocket?.accept()
                        clientSocket?.let {
                            Logger.success("SERVER_CLIENT_ACCEPT", "Accepted client connection from ${it.inetAddress.hostAddress}")
                            handleClient(it)
                        }
                    } catch (e: IOException) {
                        if (isRunning) {
                            Logger.error("SERVER_ACCEPT_ERROR", "Server accept error: ${e.message}")
                            onError("Server accept error: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Logger.error("SERVER_START_FAIL", "Failed to start TCP server: ${e.message}")
            onError("Failed to start server: ${e.message}")
        }
    }

    fun stop() {
        Logger.debug("SERVER_STOP", "Stopping TCP server")
        isRunning = false
        serverSocket?.close()
        executor.shutdown()
        Logger.success("SERVER_STOPPED", "TCP server stopped successfully")
    }

    private fun handleClient(socket: Socket) {
        executor.execute {
            try {
                Logger.debug("SERVER_HANDLE_CLIENT", "Starting file transfer session with client ${socket.inetAddress.hostAddress}")
                eventSink?.success(mapOf("event" to "clientConnected", "address" to socket.inetAddress.hostAddress))
                val fileSender = FileSender(socket, eventSink, filesToSend)
                fileSender.sendFiles()
                Logger.success("SERVER_CLIENT_DONE", "File transfer completed for client ${socket.inetAddress.hostAddress}")
            } catch (e: Exception) {
                Logger.error("SERVER_CLIENT_ERROR", "Client handling error: ${e.message}")
                eventSink?.success(mapOf("event" to "errorOccurred", "error" to e.message))
            } finally {
                Logger.verbose("SERVER_CLIENT_CLOSE", "Closing client socket")
                socket.close()
            }
        }
    }
}