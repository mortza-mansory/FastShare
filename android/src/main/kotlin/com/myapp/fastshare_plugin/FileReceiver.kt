package com.myapp.fastshare_plugin

import android.os.Environment
import android.util.Log
import io.flutter.plugin.common.EventChannel
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.Socket
import java.util.zip.CRC32

class FileReceiver(private val socket: Socket, private val eventSink: EventChannel.EventSink?) {
    private val input = DataInputStream(socket.getInputStream())
    private val buffer = ByteArray(Protocol.BUFFER_SIZE)
    private val saveDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "fastshare")

    init {
        saveDir.mkdirs()
    }

    fun receiveFiles() {
        Logger.debug("FILERECEIVER_START", "Starting file reception")
        try {
            // Read header
            Logger.verbose("FILERECEIVER_READ_HEADER", "Reading protocol header")
            val headerData = ByteArray(Protocol.HEADER_SIZE)
            input.readFully(headerData)
            val (version, numFiles) = Protocol.parseHeader(headerData)
            Logger.success("FILERECEIVER_HEADER_PARSED", "Protocol v$version, expecting $numFiles files")

            for (i in 0 until numFiles) {
                Logger.debug("FILERECEIVER_FILE_START", "Receiving file ${i + 1}/$numFiles")
                receiveFile()
            }
            Logger.success("FILERECEIVER_COMPLETE", "All $numFiles files received successfully")
        } catch (e: Exception) {
            Logger.error("FILERECEIVER_ERROR", "Error receiving files: ${e.message}")
            eventSink?.success(mapOf("event" to "errorOccurred", "error" to e.message))
        }
    }

    private fun receiveFile() {
        // Read file header
        Logger.verbose("FILERECEIVER_READ_FILE_HEADER", "Reading file header")
        val fileHeaderData = ByteArray(Protocol.FILE_HEADER_SIZE)
        input.readFully(fileHeaderData)
        val (fileName, fileSize, fileId) = Protocol.parseFileHeader(fileHeaderData)

        Logger.debug("FILERECEIVER_FILE_INFO", "Receiving file: $fileName, Size: $fileSize bytes")
        val saveFile = File(saveDir, fileName)
        Logger.verbose("FILERECEIVER_SAVE_PATH", "Saving to: ${saveFile.absolutePath}")
        eventSink?.success(mapOf("event" to "receivingStarted", "fileName" to fileName, "fileSize" to fileSize))

        val crc32 = CRC32()
        var totalReceived = 0L
        val startTime = System.currentTimeMillis()
        var chunkCount = 0

        FileOutputStream(saveFile).use { fos ->
            while (true) {
                val chunkHeaderData = ByteArray(Protocol.CHUNK_HEADER_SIZE)
                input.readFully(chunkHeaderData)
                val (chunkSize, offset) = Protocol.parseChunkHeader(chunkHeaderData)

                if (chunkSize == Protocol.END_OF_FILE) {
                    Logger.verbose("FILERECEIVER_EOF", "Received end-of-file marker")
                    break
                }

                Logger.verbose("FILERECEIVER_CHUNK_READ", "Reading chunk ${++chunkCount} of size ${chunkSize}KB at offset $offset")
                val bytesRead = input.read(buffer, 0, chunkSize)
                if (bytesRead > 0) {
                    fos.write(buffer, 0, bytesRead)
                    crc32.update(buffer, 0, bytesRead)
                    totalReceived += bytesRead

                    val progress = (totalReceived.toDouble() / fileSize * 100).toInt()
                    val speed = totalReceived / (System.currentTimeMillis() - startTime + 1) * 1000 / 1024 / 1024 // MB/s
                    Logger.debug("FILERECEIVER_PROGRESS", "Progress: $progress%, Speed: ${String.format("%.2f", speed)} MB/s")
                    eventSink?.success(mapOf(
                        "event" to "receivingProgress",
                        "fileName" to fileName,
                        "progress" to progress,
                        "speed" to speed
                    ))
                }
            }
        }

        // Read CRC32
        Logger.verbose("FILERECEIVER_READ_CRC", "Reading CRC32 checksum")
        val receivedCrc = input.readLong()
        Logger.debug("FILERECEIVER_CRC_CHECK", "Calculated CRC32: ${crc32.value}, Received CRC32: $receivedCrc")

        if (crc32.value != receivedCrc) {
            Logger.error("FILERECEIVER_CRC_MISMATCH", "CRC mismatch for $fileName - calculated: ${crc32.value}, received: $receivedCrc")
            eventSink?.success(mapOf("event" to "errorOccurred", "error" to "CRC mismatch for $fileName"))
            saveFile.delete()
            Logger.warning("FILERECEIVER_FILE_DELETED", "Corrupted file deleted: $fileName")
        } else {
            val totalTime = System.currentTimeMillis() - startTime
            val avgSpeed = fileSize / totalTime * 1000 / 1024 / 1024
            Logger.success("FILERECEIVER_FILE_COMPLETE", "File $fileName received successfully. CRC32: ${crc32.value}, Avg speed: ${String.format("%.2f", avgSpeed)} MB/s")
            eventSink?.success(mapOf("event" to "receivingCompleted", "fileName" to fileName, "crc32" to crc32.value))
        }
    }
}