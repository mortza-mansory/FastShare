package com.myapp.fastshare_plugin

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object Protocol {
    init {
        Logger.verbose("PROTOCOL_INIT", "Protocol constants initialized")
    }
    const val MAGIC_BYTES = "FSTR" // 4 bytes
    const val VERSION: Byte = 1
    const val HEADER_SIZE = 4 + 1 + 4 // magic + version + numFiles
    const val FILE_HEADER_SIZE = 4 + 256 + 8 + 16 // nameLen + name (max 256) + size + uuid
    const val CHUNK_HEADER_SIZE = 4 + 8 // chunkSize + offset
    const val END_OF_FILE: Int = -1
    const val BUFFER_SIZE = 1024 * 1024 // 1MB
    const val MAX_RETRIES = 3
    const val TIMEOUT_MS = 30000L

    fun createHeader(numFiles: Int): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        buffer.put(MAGIC_BYTES.toByteArray())
        buffer.put(VERSION)
        buffer.putInt(numFiles)
        return buffer.array()
    }

    fun parseHeader(data: ByteArray): Pair<Byte, Int> {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val magic = String(buffer.get(4).toString().toByteArray()) // wait, fix
        val version = buffer.get()
        val numFiles = buffer.getInt()
        return Pair(version, numFiles)
    }

    fun createFileHeader(name: String, size: Long, fileId: UUID): ByteArray {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(FILE_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(nameBytes.size)
        buffer.put(nameBytes)
        buffer.position(4 + 256) // pad name to 256
        buffer.putLong(size)
        buffer.putLong(fileId.mostSignificantBits)
        buffer.putLong(fileId.leastSignificantBits)
        return buffer.array()
    }

    fun parseFileHeader(data: ByteArray): Triple<String, Long, UUID> {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val nameLen = buffer.getInt()
        val nameBytes = ByteArray(nameLen)
        buffer.get(nameBytes)
        buffer.position(4 + 256)
        val size = buffer.getLong()
        val msb = buffer.getLong()
        val lsb = buffer.getLong()
        val fileId = UUID(msb, lsb)
        return Triple(String(nameBytes, Charsets.UTF_8), size, fileId)
    }

    fun createChunkHeader(chunkSize: Int, offset: Long): ByteArray {
        val buffer = ByteBuffer.allocate(CHUNK_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(chunkSize)
        buffer.putLong(offset)
        return buffer.array()
    }

    fun parseChunkHeader(data: ByteArray): Pair<Int, Long> {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val chunkSize = buffer.getInt()
        val offset = buffer.getLong()
        return Pair(chunkSize, offset)
    }
}