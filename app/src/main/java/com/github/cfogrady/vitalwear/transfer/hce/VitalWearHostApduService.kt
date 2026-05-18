package com.github.cfogrady.vitalwear.transfer.hce

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.github.cfogrady.vitalwear.VitalWearApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VitalWearHostApduService : HostApduService() {
    companion object {
        private const val TAG = "VW_HCE_SERVICE"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val metricsLock = Any()

    private data class HostSessionMetrics(
        val direction: String,
        val requestedChunkBytes: Int,
        val negotiatedChunkBytes: Int,
        val payloadBytes: Int,
        val startedAtMs: Long,
        var apduTotal: Int = 0,
        var readChunkApdus: Int = 0,
        var writeChunkApdus: Int = 0,
        var statusPollApdus: Int = 0,
        var readBytes: Int = 0,
        var writeBytes: Int = 0,
    )

    private var currentMetrics: HostSessionMetrics? = null

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
                VitalWearHceProtocol.INS_STATUS -> handleStatus()
                VitalWearHceProtocol.INS_SYNC_UI -> handleSyncUi(data)
                VitalWearHceProtocol.INS_VIBRATE -> handleVibrate(data)
                else -> VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_FUNC_NOT_SUPPORTED)
            }
        } catch (error: Exception) {
            Log.e(TAG, "APDU handling failed for INS=${String.format("0x%02X", ins)}", error)
            VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_INTERNAL_ERROR)
        }
    }

    override fun onDeactivated(reason: Int) {
        // Keep the armed mode until the UI changes mode or the activity exits.
        logAndClearMetrics("deactivated(reason=$reason)")
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

        val requestedChunkSize = if (data.size >= 4) {
            ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        } else {
            VitalWearHceProtocol.PREFERRED_MAX_CHUNK_SIZE
        }

        // Auto-arm so VBHelper can initiate a transfer without the user first navigating to
        // TransferActivity on the watch.  The existing buttons in TransferActivity still work
        // and take precedence when the session is already armed.
        if (VitalWearHceSessionManager.currentMode() == VitalWearHceSessionManager.Mode.IDLE) {
            when (mode) {
                VitalWearHceProtocol.MODE_WATCH_TO_PHONE -> {
                    // VBHelper wants to READ from the watch — arm a SEND session automatically.
                    val payload = repository.getActiveCharacterPayload()
                        ?: return VitalWearHceProtocol.buildResponse(
                            statusWord = VitalWearHceProtocol.SW_CONDITIONS_NOT_SATISFIED
                        )
                    VitalWearHceSessionManager.armSend(payload)
                }
                VitalWearHceProtocol.MODE_PHONE_TO_WATCH -> {
                    // VBHelper wants to WRITE to the watch — arm a RECEIVE session automatically.
                    VitalWearHceSessionManager.armReceive()
                }
            }
        }

        val session = VitalWearHceSessionManager.negotiate(mode, requestedChunkSize)
            ?: return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_CONDITIONS_NOT_SATISFIED)

        startMetrics(
            direction = when (mode) {
                VitalWearHceProtocol.MODE_WATCH_TO_PHONE -> "WATCH_TO_PHONE"
                VitalWearHceProtocol.MODE_PHONE_TO_WATCH -> "PHONE_TO_WATCH"
                else -> "UNKNOWN"
            },
            requestedChunkBytes = requestedChunkSize,
            negotiatedChunkBytes = session.maxChunkSize,
            payloadBytes = session.payloadLength,
        )
        bumpApduCount()

        val responseData = byteArrayOf(
            VitalWearHceProtocol.VERSION_1,
            mode,
            ((session.maxChunkSize ushr 8) and 0xFF).toByte(),
            (session.maxChunkSize and 0xFF).toByte(),
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

        val chunk = VitalWearHceSessionManager.readChunk(offset, VitalWearHceProtocol.PREFERRED_MAX_CHUNK_SIZE)
            ?: return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_CONDITIONS_NOT_SATISFIED)
        recordReadChunk(chunk.size)
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
        if (accepted) {
            recordWriteChunk(chunk.size)
        }
        return if (accepted) {
            VitalWearHceProtocol.buildResponse()
        } else {
            VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_WRONG_P1P2)
        }
    }

    private fun handleCommit(): ByteArray {
        return when (VitalWearHceSessionManager.currentMode()) {
            VitalWearHceSessionManager.Mode.SEND_TO_PHONE -> {
                VitalWearHceSessionManager.markSuccess()
                VitalWearHceSessionManager.clear(resetStatus = false)
                bumpApduCount()
                logAndClearMetrics("success(send_commit)")
                serviceScope.launch {
                    runCatching {
                        repository.deleteCurrentCharacterAfterSuccessfulSend()
                    }.onFailure {
                        Log.w(TAG, "Post-send delete failed after COMMIT", it)
                    }
                }
                VitalWearHceProtocol.buildResponse()
            }

            VitalWearHceSessionManager.Mode.RECEIVE_FROM_PHONE -> {
                val payload = VitalWearHceSessionManager.takeReceivedPayload()
                    ?: return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_CONDITIONS_NOT_SATISFIED)

                // Acknowledge COMMIT immediately so the phone-side IsoDep transceive does not time out.
                VitalWearHceSessionManager.clear(resetStatus = false)
                VitalWearHceSessionManager.markSyncing()
                serviceScope.launch {
                    val importSuccess = runCatching {
                        repository.importCharacter(payload)
                    }.onFailure {
                        Log.e(TAG, "Async import threw exception", it)
                    }.getOrDefault(false)
                    if (importSuccess) {
                        Log.i(TAG, "Async import marked SUCCESS")
                        VitalWearHceSessionManager.markSuccess()
                        logAndClearMetrics("success(import_complete)")
                    } else {
                        Log.w(TAG, "Async import marked FAILURE")
                        VitalWearHceSessionManager.markFailure()
                        logAndClearMetrics("failure(import_failed)")
                    }
                }
                bumpApduCount()
                VitalWearHceProtocol.buildResponse()
            }

            VitalWearHceSessionManager.Mode.IDLE -> {
                VitalWearHceSessionManager.markFailure()
                bumpApduCount()
                logAndClearMetrics("failure(commit_idle)")
                VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_CONDITIONS_NOT_SATISFIED)
            }
        }
    }

    private fun handleStatus(): ByteArray {
        recordStatusPoll()
        val statusByte = when (VitalWearHceSessionManager.transferStatus.value) {
            VitalWearHceSessionManager.TransferStatus.IDLE -> 0x00.toByte()
            VitalWearHceSessionManager.TransferStatus.ARMED_SEND -> 0x01.toByte()
            VitalWearHceSessionManager.TransferStatus.ARMED_RECEIVE -> 0x02.toByte()
            VitalWearHceSessionManager.TransferStatus.SYNCING -> 0x03.toByte()
            VitalWearHceSessionManager.TransferStatus.SUCCESS -> 0x04.toByte()
            VitalWearHceSessionManager.TransferStatus.FAILURE -> 0x05.toByte()
        }
        return VitalWearHceProtocol.buildResponse(byteArrayOf(statusByte))
    }

    private fun handleSyncUi(data: ByteArray): ByteArray {
        // Implement UI sync logic (e.g., trigger animation via a broadcast or event bus)
        // For now, mark as sync-in-progress
        VitalWearHceSessionManager.markSyncing()
        return VitalWearHceProtocol.buildResponse()
    }

    private fun handleVibrate(data: ByteArray): ByteArray {
        // Trigger haptic feedback
        return VitalWearHceProtocol.buildResponse()
    }

    private fun startMetrics(
        direction: String,
        requestedChunkBytes: Int,
        negotiatedChunkBytes: Int,
        payloadBytes: Int,
    ) {
        synchronized(metricsLock) {
            currentMetrics = HostSessionMetrics(
                direction = direction,
                requestedChunkBytes = requestedChunkBytes,
                negotiatedChunkBytes = negotiatedChunkBytes,
                payloadBytes = payloadBytes,
                startedAtMs = SystemClock.elapsedRealtime(),
            )
        }
    }

    private fun bumpApduCount() {
        synchronized(metricsLock) {
            currentMetrics?.apduTotal = (currentMetrics?.apduTotal ?: 0) + 1
        }
    }

    private fun recordReadChunk(bytes: Int) {
        synchronized(metricsLock) {
            currentMetrics?.let {
                it.apduTotal += 1
                it.readChunkApdus += 1
                it.readBytes += bytes
            }
        }
    }

    private fun recordWriteChunk(bytes: Int) {
        synchronized(metricsLock) {
            currentMetrics?.let {
                it.apduTotal += 1
                it.writeChunkApdus += 1
                it.writeBytes += bytes
            }
        }
    }

    private fun recordStatusPoll() {
        synchronized(metricsLock) {
            currentMetrics?.let {
                it.apduTotal += 1
                it.statusPollApdus += 1
            }
        }
    }

    private fun logAndClearMetrics(result: String) {
        synchronized(metricsLock) {
            val metrics = currentMetrics ?: return
            val elapsed = SystemClock.elapsedRealtime() - metrics.startedAtMs
            Log.i(
                "HCE_HOST_METRICS",
                "dir=${metrics.direction} requestedChunk=${metrics.requestedChunkBytes} negotiatedChunk=${metrics.negotiatedChunkBytes} " +
                    "payload=${metrics.payloadBytes} apduTotal=${metrics.apduTotal} readApdus=${metrics.readChunkApdus} " +
                    "writeApdus=${metrics.writeChunkApdus} statusApdus=${metrics.statusPollApdus} readBytes=${metrics.readBytes} " +
                    "writeBytes=${metrics.writeBytes} elapsedMs=$elapsed result=$result"
            )
            currentMetrics = null
        }
    }
}

