package com.github.cfogrady.vitalwear.transfer.hce

object VitalWearHceProtocol {
    val AID: ByteArray = byteArrayOf(
        0xF0.toByte(), 0x56, 0x49, 0x54, 0x41, 0x4C, 0x57, 0x45, 0x41, 0x52
    )

    const val CLA_VITALWEAR: Byte = 0x80.toByte()
    const val INS_NEGOTIATE: Byte = 0x10
    const val INS_READ_CHUNK: Byte = 0x20
    const val INS_WRITE_CHUNK: Byte = 0x30
    const val INS_COMMIT: Byte = 0x40

    const val MODE_WATCH_TO_PHONE: Byte = 0x01
    const val MODE_PHONE_TO_WATCH: Byte = 0x02

    const val VERSION_1: Byte = 0x01

    const val SW_OK: Int = 0x9000
    const val SW_WRONG_LENGTH: Int = 0x6700
    const val SW_CONDITIONS_NOT_SATISFIED: Int = 0x6985
    const val SW_WRONG_DATA: Int = 0x6A80
    const val SW_FUNC_NOT_SUPPORTED: Int = 0x6A81
    const val SW_WRONG_P1P2: Int = 0x6B00
    const val SW_INTERNAL_ERROR: Int = 0x6F00

    const val DEFAULT_MAX_CHUNK_SIZE = 180

    fun isSelectAidApdu(commandApdu: ByteArray): Boolean {
        if (commandApdu.size < 5) {
            return false
        }
        if (commandApdu[0] != 0x00.toByte() || commandApdu[1] != 0xA4.toByte() || commandApdu[2] != 0x04.toByte()) {
            return false
        }
        val lc = commandApdu[4].toInt() and 0xFF
        if (commandApdu.size < 5 + lc) {
            return false
        }
        val aidBytes = commandApdu.copyOfRange(5, 5 + lc)
        return aidBytes.contentEquals(AID)
    }

    fun parseCommandData(commandApdu: ByteArray): ByteArray {
        if (commandApdu.size < 5) {
            return byteArrayOf()
        }
        val lc = commandApdu[4].toInt() and 0xFF
        val dataEnd = 5 + lc
        if (dataEnd > commandApdu.size) {
            return byteArrayOf()
        }
        return commandApdu.copyOfRange(5, dataEnd)
    }

    fun buildResponse(data: ByteArray = byteArrayOf(), statusWord: Int = SW_OK): ByteArray {
        return data + statusWord.toStatusWordBytes()
    }
}

private fun Int.toStatusWordBytes(): ByteArray {
    return byteArrayOf(((this ushr 8) and 0xFF).toByte(), (this and 0xFF).toByte())
}

