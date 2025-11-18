package com.myapp.fastshare_plugin

import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class SocketClient(private val emit: (Any?) -> Unit) {

    private val executor = Executors.newSingleThreadExecutor()

    fun connectAndSend(host: String, port: Int, files: List<String>, onError: (String) -> Unit) {
        emit(mapOf("event" to "log", "message" to "Client connecting to $host:$port"))
        executor.execute {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(host, port), Protocol.TIMEOUT_MS.toInt())
                socket.tcpNoDelay = true
                socket.receiveBufferSize = Protocol.BUFFER_SIZE
                socket.sendBufferSize = Protocol.BUFFER_SIZE

                val sender = FileSender(socket, { ev -> emit(ev) }, files)
                sender.sendFiles()
                emit(mapOf("event" to "sendingCompleted"))
            } catch (e: Exception) {
                onError(e.message ?: "Unknown")
                emit(mapOf("event" to "errorOccurred", "message" to e.message))
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    fun connectAndReceive(host: String, port: Int, onError: (String) -> Unit) {
        emit(mapOf("event" to "log", "message" to "Client connecting to $host:$port for receiving"))
        executor.execute {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(host, port), Protocol.TIMEOUT_MS.toInt())
                socket.tcpNoDelay = true
                socket.receiveBufferSize = Protocol.BUFFER_SIZE
                socket.sendBufferSize = Protocol.BUFFER_SIZE

                val receiver = FileReceiver(socket, { ev -> emit(ev) })
                receiver.receiveFiles()
                emit(mapOf("event" to "receivingCompleted"))
            } catch (e: Exception) {
                onError(e.message ?: "Unknown")
                emit(mapOf("event" to "errorOccurred", "message" to e.message))
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }
}
