package com.myapp.fastshare_plugin

import android.util.Log
import io.flutter.plugin.common.EventChannel
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.UUID
import java.util.zip.CRC32

class FileSender(private val socket: Socket, private val eventSink: EventChannel.EventSink?, private val filesToSend: List<String>) {
    private val output = DataOutputStream(socket.getOutputStream())
    private val buffer = ByteArray(Protocol.BUFFER_SIZE)

    init {
        socket.tcpNoDelay = true
        socket.receiveBufferSize = Protocol.BUFFER_SIZE
        socket.sendBufferSize = Protocol.BUFFER_SIZE
    }

    fun sendFiles() {
        Logger.debug("FILESENDER_START", "Starting to send ${filesToSend.size} files")
        try {
            // Send header
            val header = Protocol.createHeader(filesToSend.size)
            Logger.verbose("FILESENDER_HEADER", "Sending protocol header for ${filesToSend.size} files")
            output.write(header)

            // Send each file
            for ((index, path) in filesToSend.withIndex()) {
                Logger.debug("FILESENDER_FILE_START", "Sending file ${index + 1}/${filesToSend.size}: $path")
                sendFile(File(path))
            }
            Logger.success("FILESENDER_COMPLETE", "All ${filesToSend.size} files sent successfully")
        } catch (e: Exception) {
            Logger.error("FILESENDER_ERROR", "Error sending files: ${e.message}")
            eventSink?.success(mapOf("event" to "errorOccurred", "error" to e.message))
        }
    }

    private fun sendFile(file: File) {
        if (!file.exists()) {
            Logger.error("FILESENDER_FILE_MISSING", "File not found: ${file.path}")
            eventSink?.success(mapOf("event" to "errorOccurred", "error" to "File not found: ${file.path}"))
            return
        }

        Logger.debug("FILESENDER_FILE_INFO", "File: ${file.name}, Size: ${file.length()} bytes")
        val fileId = UUID.randomUUID()
        val fileHeader = Protocol.createFileHeader(file.name, file.length(), fileId)
        Logger.verbose("FILESENDER_FILE_HEADER", "Sending file header for ${file.name}")
        output.write(fileHeader)

        eventSink?.success(mapOf("event" to "sendingStarted", "fileName" to file.name, "fileSize" to file.length()))

        val crc32 = CRC32()
        var totalSent = 0L
        val startTime = System.currentTimeMillis()
        var chunkCount = 0

        FileInputStream(file).use { fis ->
            val channel = fis.channel
            var offset = 0L
            while (offset < file.length()) {
                val chunkSize = minOf(Protocol.BUFFER_SIZE.toLong(), file.length() - offset).toInt()
                val chunkHeader = Protocol.createChunkHeader(chunkSize, offset)
                Logger.verbose("FILESENDER_CHUNK_SEND", "Sending chunk ${++chunkCount} of size ${chunkSize}KB at offset $offset")
                output.write(chunkHeader)

                val bytesRead = channel.read(ByteBuffer.wrap(buffer, 0, chunkSize))
                if (bytesRead > 0) {
                    output.write(buffer, 0, bytesRead)
                    crc32.update(buffer, 0, bytesRead)
                    totalSent += bytesRead
                    offset += bytesRead

                    val progress = (totalSent.toDouble() / file.length() * 100).toInt()
                    val speed = totalSent / (System.currentTimeMillis() - startTime + 1) * 1000 / 1024 / 1024 // MB/s
                    Logger.debug("FILESENDER_PROGRESS", "Progress: $progress%, Speed: ${String.format("%.2f", speed)} MB/s")
                    eventSink?.success(mapOf(
                        "event" to "sendingProgress",
                        "fileName" to file.name,
                        "progress" to progress,
                        "speed" to speed
                    ))
                }
            }
        }

        // Send end of file
        Logger.verbose("FILESENDER_EOF", "Sending end-of-file marker")
        val endHeader = Protocol.createChunkHeader(Protocol.END_OF_FILE, file.length())
        output.write(endHeader)

        // Send CRC32
        Logger.debug("FILESENDER_CRC", "Sending CRC32 checksum: ${crc32.value}")
        output.writeLong(crc32.value)

        val totalTime = System.currentTimeMillis() - startTime
        val avgSpeed = file.length() / totalTime * 1000 / 1024 / 1024
        Logger.success("FILESENDER_FILE_COMPLETE", "File ${file.name} sent successfully. CRC32: ${crc32.value}, Avg speed: ${String.format("%.2f", avgSpeed)} MB/s")
        eventSink?.success(mapOf("event" to "sendingCompleted", "fileName" to file.name, "crc32" to crc32.value))
    }
}