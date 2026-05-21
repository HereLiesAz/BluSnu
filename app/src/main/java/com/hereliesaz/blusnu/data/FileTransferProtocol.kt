package com.hereliesaz.blusnu.data

import java.util.UUID

object FileTransferProtocol {
    // Custom SPP UUID for BluSnu file transfer
    val SERVICE_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")

    const val CHUNK_SIZE = 4096

    // Message markers
    const val MSG_FILE_START: Byte = 0x01
    const val MSG_FILE_DATA: Byte = 0x02
    const val MSG_FILE_END: Byte = 0x03
    const val MSG_ACK: Byte = 0x04
    const val MSG_NACK: Byte = 0x05
    const val MSG_CANCEL: Byte = 0x06

    // Header: [MSG_FILE_START][filename_len:2][filename][file_size:8]
    fun buildFileStartHeader(fileName: String, fileSize: Long): ByteArray {
        val nameBytes = fileName.toByteArray(Charsets.UTF_8)
        val nameLen = nameBytes.size
        val header = ByteArray(1 + 2 + nameLen + 8)
        header[0] = MSG_FILE_START
        header[1] = (nameLen shr 8 and 0xFF).toByte()
        header[2] = (nameLen and 0xFF).toByte()
        nameBytes.copyInto(header, 3)
        val sizeOffset = 3 + nameLen
        for (i in 0..7) {
            header[sizeOffset + i] = (fileSize shr (56 - i * 8) and 0xFF).toByte()
        }
        return header
    }

    fun parseFileStartHeader(data: ByteArray): Pair<String, Long>? {
        if (data.isEmpty() || data[0] != MSG_FILE_START) return null
        if (data.size < 3) return null
        val nameLen = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
        if (data.size < 3 + nameLen + 8) return null
        val fileName = String(data, 3, nameLen, Charsets.UTF_8)
        var fileSize = 0L
        val sizeOffset = 3 + nameLen
        for (i in 0..7) {
            fileSize = fileSize or ((data[sizeOffset + i].toLong() and 0xFF) shl (56 - i * 8))
        }
        return fileName to fileSize
    }
}

enum class TransferDirection { SEND, RECEIVE }

enum class TransferStatus {
    IDLE,
    WAITING_FOR_CONNECTION,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class TransferInfo(
    val fileName: String,
    val fileSize: Long,
    val direction: TransferDirection,
    val status: TransferStatus,
    val progress: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)
