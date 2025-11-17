package com.myapp.fastshare_plugin

import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class SocketServer(private val emit: (Any?) -> Unit) {

    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private var isRunning = false
    private var filesToSend = mutableListOf<String>()

    fun setFiles(list: List<String>) {
        filesToSend.clear()
        filesToSend.addAll(list)
    }

    fun start(port: Int, onError: (String) -> Unit) {
        emit(mapOf("event" to "log", "message" to "Starting server on $port"))
        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            emit(mapOf("event" to "serverStarted", "port" to port))
            executor.execute {
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept()
                        if (client != null) {
                            emit(mapOf("event" to "clientConnected", "address" to client.inetAddress.hostAddress))
                            handleClient(client)
                        }
                    } catch (e: IOException) {
                        if (isRunning) {
                            onError("Accept error: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: IOException) {
            onError("Server start failed: ${e.message}")
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        executor.shutdownNow()
        emit(mapOf("event" to "serverStopped"))
    }

    private fun handleClient(socket: Socket) {
        executor.execute {
            try {
                val sender = FileSender(socket, { ev -> emit(ev) }, filesToSend.toList())
                sender.sendFiles()
                emit(mapOf("event" to "sendingCompleted"))
            } catch (e: Exception) {
                emit(mapOf("event" to "errorOccurred", "message" to e.message))
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }
}
