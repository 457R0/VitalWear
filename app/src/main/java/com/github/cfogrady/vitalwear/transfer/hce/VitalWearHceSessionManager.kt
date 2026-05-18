package com.github.cfogrady.vitalwear.transfer.hce

import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object VitalWearHceSessionManager {
    enum class Mode {
        IDLE,
        SEND_TO_PHONE,
        RECEIVE_FROM_PHONE,
    }

    enum class TransferStatus {
        IDLE,
        ARMED_SEND,
        ARMED_RECEIVE,
        SYNCING,
        SUCCESS,
        FAILURE,
    }

    private val lock = Any()

    private var mode: Mode = Mode.IDLE
    private var sendPayload: ByteArray = byteArrayOf()
    private var receiveBuffer = ByteArrayOutputStream()
    private var negotiatedMaxChunkSize: Int = VitalWearHceProtocol.PREFERRED_MAX_CHUNK_SIZE
    private val _transferStatus = MutableStateFlow(TransferStatus.IDLE)

    val transferStatus: StateFlow<TransferStatus>
        get() = _transferStatus

    fun armSend(payload: ByteArray) {
        synchronized(lock) {
            mode = Mode.SEND_TO_PHONE
            sendPayload = payload
            receiveBuffer = ByteArrayOutputStream()
            _transferStatus.value = TransferStatus.ARMED_SEND
        }
    }

    fun armReceive() {
        synchronized(lock) {
            mode = Mode.RECEIVE_FROM_PHONE
            sendPayload = byteArrayOf()
            receiveBuffer = ByteArrayOutputStream()
            _transferStatus.value = TransferStatus.ARMED_RECEIVE
        }
    }

    fun currentMode(): Mode {
        synchronized(lock) {
            return mode
        }
    }

    internal fun negotiate(modeByte: Byte, requestedMaxChunkSize: Int): VitalWearHceSessionInfo? {
        synchronized(lock) {
            val agreedChunkSize = requestedMaxChunkSize.coerceIn(1, VitalWearHceProtocol.PREFERRED_MAX_CHUNK_SIZE)
            negotiatedMaxChunkSize = agreedChunkSize
            return when (modeByte) {
                VitalWearHceProtocol.MODE_WATCH_TO_PHONE -> {
                    if (mode == Mode.SEND_TO_PHONE) {
                        VitalWearHceSession(
                            direction = VitalWearHceTransferDirection.WATCH_TO_PHONE,
                            maxChunkSize = agreedChunkSize,
                            payloadLength = sendPayload.size,
                        )
                    } else {
                        null
                    }
                }
                VitalWearHceProtocol.MODE_PHONE_TO_WATCH -> {
                    if (mode == Mode.RECEIVE_FROM_PHONE) {
                        receiveBuffer = ByteArrayOutputStream()
                        VitalWearHceSession(
                            direction = VitalWearHceTransferDirection.PHONE_TO_WATCH,
                            maxChunkSize = agreedChunkSize,
                            payloadLength = 0,
                        )
                    } else {
                        null
                    }
                }
                else -> null
            }
        }
    }

    fun readChunk(offset: Int, maxChunkSize: Int): ByteArray? {
        synchronized(lock) {
            if (mode != Mode.SEND_TO_PHONE || offset < 0 || offset > sendPayload.size) {
                return null
            }
            if (offset == sendPayload.size) {
                return byteArrayOf()
            }
            val effectiveMaxChunkSize = maxChunkSize.coerceAtMost(negotiatedMaxChunkSize)
            val chunkEnd = (offset + effectiveMaxChunkSize).coerceAtMost(sendPayload.size)
            return sendPayload.copyOfRange(offset, chunkEnd)
        }
    }

    fun writeChunk(offset: Int, chunk: ByteArray): Boolean {
        synchronized(lock) {
            if (mode != Mode.RECEIVE_FROM_PHONE || offset < 0) {
                return false
            }
            if (offset != receiveBuffer.size()) {
                return false
            }
            receiveBuffer.write(chunk)
            return true
        }
    }

    fun takeReceivedPayload(): ByteArray? {
        synchronized(lock) {
            if (mode != Mode.RECEIVE_FROM_PHONE) {
                return null
            }
            return receiveBuffer.toByteArray()
        }
    }

    fun clear(resetStatus: Boolean = true) {
        synchronized(lock) {
            mode = Mode.IDLE
            sendPayload = byteArrayOf()
            receiveBuffer = ByteArrayOutputStream()
            negotiatedMaxChunkSize = VitalWearHceProtocol.PREFERRED_MAX_CHUNK_SIZE
            if (resetStatus) {
                _transferStatus.value = TransferStatus.IDLE
            }
        }
    }

    fun markSuccess() {
        synchronized(lock) {
            _transferStatus.value = TransferStatus.SUCCESS
        }
    }

    fun markSyncing() {
        synchronized(lock) {
            _transferStatus.value = TransferStatus.SYNCING
        }
    }

    fun markFailure() {
        synchronized(lock) {
            _transferStatus.value = TransferStatus.FAILURE
        }
    }

}

