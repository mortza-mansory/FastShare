package com.myapp.fastshare_plugin

import android.os.Environment
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.Socket
import java.util.zip.CRC32

class FileReceiver(private val socket: Socket, private val emit: (Any?) -> Unit) {
    private val input = DataInputStream(socket.getInputStream())
    private val buffer = ByteArray(Protocol.BUFFER_SIZE)
    private val saveDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "fastshare")

    init { saveDir.mkdirs() }

    fun receiveFiles() {
        try {
            val header = ByteArray(Protocol.HEADER_SIZE)
            input.readFully(header)
            val (_, numFiles) = Protocol.parseHeader(header)
            for (i in 0 until numFiles) receiveSingleFile()
            emit(mapOf("event" to "allFilesReceived"))
        } catch (e: Exception) {
            emit(mapOf("event" to "errorOccurred", "message" to e.message))
        }
    }

    private fun receiveSingleFile() {
        val fileHeader = ByteArray(Protocol.FILE_HEADER_SIZE)
        input.readFully(fileHeader)
        val (fileName, fileSize) = Protocol.parseFileHeader(fileHeader)

        val outFile = File(saveDir, fileName)
        emit(mapOf("event" to "receivingStarted", "fileName" to fileName, "fileSize" to fileSize))

        val crc = CRC32()
        var received = 0L
        val start = System.currentTimeMillis()

        FileOutputStream(outFile).use { fos ->
            while (true) {
                val chunkHeader = ByteArray(Protocol.CHUNK_HEADER_SIZE)
                input.readFully(chunkHeader)
                val (chunkSize, _) = Protocol.parseChunkHeader(chunkHeader)
                if (chunkSize == Protocol.END_OF_FILE) break
                val bytesRead = input.read(buffer, 0, chunkSize)
                fos.write(buffer, 0, bytesRead)
                crc.update(buffer, 0, bytesRead)
                received += bytesRead
                val progress = (received.toDouble() / fileSize * 100).toInt()
                val elapsed = System.currentTimeMillis() - start
                val speedMbps = if (elapsed > 0) (received.toDouble() / (1024.0 * 1024.0)) / (elapsed.toDouble() / 1000.0) else 0.0
                emit(mapOf("event" to "receivingProgress", "fileName" to fileName, "progress" to progress, "speedMbps" to speedMbps))
            }
        }

        val expected = input.readLong()
        if (crc.value != expected) {
            outFile.delete()
            emit(mapOf("event" to "errorOccurred", "code" to "CRC_MISMATCH", "message" to "CRC mismatch for $fileName"))
        } else {
            emit(mapOf("event" to "receivingCompleted", "fileName" to fileName, "crc32" to crc.value))
        }
    }
}
