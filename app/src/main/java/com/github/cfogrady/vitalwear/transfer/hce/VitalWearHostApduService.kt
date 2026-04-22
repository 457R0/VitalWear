package com.github.cfogrady.vitalwear.transfer.hce

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.github.cfogrady.vitalwear.VitalWearApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VitalWearHostApduService : HostApduService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repository: VitalWearHceTransferRepository by lazy {
        VitalWearHceTransferRepository(application as VitalWearApp)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) {
            return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_WRONG_LENGTH)
        }

        if (VitalWearHceProtocol.isSelectAidApdu(commandApdu)) {
            return VitalWearHceProtocol.buildResponse()
        }

        if (commandApdu.size < 2 || commandApdu[0] != VitalWearHceProtocol.CLA_VITALWEAR) {
            return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_WRONG_DATA)
        }

        val ins = commandApdu[1]
        val data = VitalWearHceProtocol.parseCommandData(commandApdu)

        return try {
            when (ins) {
                VitalWearHceProtocol.INS_NEGOTIATE -> handleNegotiate(data)
                VitalWearHceProtocol.INS_READ_CHUNK -> handleReadChunk(data)
                VitalWearHceProtocol.INS_WRITE_CHUNK -> handleWriteChunk(data)
                VitalWearHceProtocol.INS_COMMIT -> handleCommit()
                else -> VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_FUNC_NOT_SUPPORTED)
            }
        } catch (_: Exception) {
            VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_INTERNAL_ERROR)
        }
    }

    override fun onDeactivated(reason: Int) {
        // Keep the armed mode until the UI changes mode or the activity exits.
    }

    private fun handleNegotiate(data: ByteArray): ByteArray {
        if (data.size < 2) {
            return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_WRONG_LENGTH)
        }

        val mode = data[0]
        val version = data[1]
        if (version != VitalWearHceProtocol.VERSION_1) {
            return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_FUNC_NOT_SUPPORTED)
        }

        val session = VitalWearHceSessionManager.negotiate(mode)
            ?: return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_CONDITIONS_NOT_SATISFIED)

        val responseData = byteArrayOf(
            VitalWearHceProtocol.VERSION_1,
            mode,
            ((VitalWearHceProtocol.DEFAULT_MAX_CHUNK_SIZE ushr 8) and 0xFF).toByte(),
            (VitalWearHceProtocol.DEFAULT_MAX_CHUNK_SIZE and 0xFF).toByte(),
            ((session.payloadLength ushr 24) and 0xFF).toByte(),
            ((session.payloadLength ushr 16) and 0xFF).toByte(),
            ((session.payloadLength ushr 8) and 0xFF).toByte(),
            (session.payloadLength and 0xFF).toByte(),
        )
        return VitalWearHceProtocol.buildResponse(responseData)
    }

    private fun handleReadChunk(data: ByteArray): ByteArray {
        if (data.size != 4) {
            return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_WRONG_LENGTH)
        }
        val offset =
            ((data[0].toInt() and 0xFF) shl 24) or
                ((data[1].toInt() and 0xFF) shl 16) or
                ((data[2].toInt() and 0xFF) shl 8) or
                (data[3].toInt() and 0xFF)
        if (offset < 0) {
            return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_WRONG_P1P2)
        }

        val chunk = VitalWearHceSessionManager.readChunk(offset, VitalWearHceProtocol.DEFAULT_MAX_CHUNK_SIZE)
            ?: return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_CONDITIONS_NOT_SATISFIED)
        return VitalWearHceProtocol.buildResponse(chunk)
    }

    private fun handleWriteChunk(data: ByteArray): ByteArray {
        if (data.size < 4) {
            return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_WRONG_LENGTH)
        }
        val offset =
            ((data[0].toInt() and 0xFF) shl 24) or
                ((data[1].toInt() and 0xFF) shl 16) or
                ((data[2].toInt() and 0xFF) shl 8) or
                (data[3].toInt() and 0xFF)
        val chunk = data.copyOfRange(4, data.size)
        val accepted = VitalWearHceSessionManager.writeChunk(offset, chunk)
        return if (accepted) {
            VitalWearHceProtocol.buildResponse()
        } else {
            VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_WRONG_P1P2)
        }
    }

    private fun handleCommit(): ByteArray {
        return when (VitalWearHceSessionManager.currentMode()) {
            VitalWearHceSessionManager.Mode.SEND_TO_PHONE -> {
                repository.deleteCurrentCharacterAfterSuccessfulSend()
                VitalWearHceSessionManager.markSuccess()
                VitalWearHceSessionManager.clear(resetStatus = false)
                VitalWearHceProtocol.buildResponse()
            }

            VitalWearHceSessionManager.Mode.RECEIVE_FROM_PHONE -> {
                val payload = VitalWearHceSessionManager.takeReceivedPayload()
                    ?: return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_CONDITIONS_NOT_SATISFIED)

                // Acknowledge COMMIT immediately so the phone-side IsoDep transceive does not time out.
                VitalWearHceSessionManager.clear(resetStatus = false)
                serviceScope.launch {
                    val importSuccess = runCatching {
                        repository.importCharacter(payload)
                    }.getOrDefault(false)
                    if (importSuccess) {
                        VitalWearHceSessionManager.markSuccess()
                    } else {
                        VitalWearHceSessionManager.markFailure()
                    }
                }
                VitalWearHceProtocol.buildResponse()
            }

            VitalWearHceSessionManager.Mode.IDLE -> {
                VitalWearHceSessionManager.markFailure()
                VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_CONDITIONS_NOT_SATISFIED)
            }
        }
    }
}

