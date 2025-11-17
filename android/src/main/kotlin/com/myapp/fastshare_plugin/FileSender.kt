package com.myapp.fastshare_plugin

import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.util.UUID
import java.util.zip.CRC32

class FileSender(
    private val socket: Socket,
    private val emit: (Any?) -> Unit,
    private val files: List<String>
) {
    private val output = DataOutputStream(socket.getOutputStream())
    private val buffer = ByteArray(Protocol.BUFFER_SIZE)

    init {
        socket.tcpNoDelay = true
        socket.receiveBufferSize = Protocol.BUFFER_SIZE
        socket.sendBufferSize = Protocol.BUFFER_SIZE
    }

    fun sendFiles() {
        try {
            val header = Protocol.createHeader(files.size)
            output.write(header)
            for (path in files) {
                val file = File(path)
                if (!file.exists()) {
                    emit(mapOf("event" to "errorOccurred", "code" to "FILE_NOT_FOUND", "message" to file.path))
                    continue
                }
                sendFile(file)
            }
            emit(mapOf("event" to "allFilesSent"))
        } catch (e: Exception) {
            emit(mapOf("event" to "errorOccurred", "message" to e.message))
        }
    }

    private fun sendFile(file: File) {
        val fileId = UUID.randomUUID()
        val header = Protocol.createFileHeader(file.name, file.length(), fileId)
        output.write(header)
        emit(mapOf("event" to "sendingStarted", "fileName" to file.name, "fileSize" to file.length()))

        val crc = CRC32()
        var totalSent = 0L
        val start = System.currentTimeMillis()

        FileInputStream(file).use { fis ->
            val channel = fis.channel
            var offset = 0L
            while (offset < file.length()) {
                val chunkSize = minOf(Protocol.BUFFER_SIZE.toLong(), file.length() - offset).toInt()
                val chunkHeader = Protocol.createChunkHeader(chunkSize, offset)
                output.write(chunkHeader)

                val bytesRead = channel.read(ByteBuffer.wrap(buffer, 0, chunkSize))
                if (bytesRead > 0) {
                    output.write(buffer, 0, bytesRead)
                    crc.update(buffer, 0, bytesRead)
                    totalSent += bytesRead
                    offset += bytesRead

                    val progress = (totalSent.toDouble() / file.length() * 100).toInt()
                    val elapsed = System.currentTimeMillis() - start
                    val speedMbps = if (elapsed > 0) (totalSent.toDouble() / (1024.0 * 1024.0)) / (elapsed.toDouble() / 1000.0) else 0.0

                    emit(mapOf("event" to "sendingProgress", "fileName" to file.name, "progress" to progress, "speedMbps" to speedMbps))
                }
            }
        }

        // EOF marker
        val eof = Protocol.createChunkHeader(Protocol.END_OF_FILE, file.length())
        output.write(eof)
        output.writeLong(crc.value)

        emit(mapOf("event" to "sendingCompleted", "fileName" to file.name, "crc32" to crc.value))
    }
}
