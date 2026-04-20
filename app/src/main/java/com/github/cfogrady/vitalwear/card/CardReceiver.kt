package com.github.cfogrady.vitalwear.card

import android.content.Context
import com.github.cfogrady.vitalwear.notification.NotificationChannelManager
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.nio.charset.Charset

class CardReceiver(
    private val cardLoader: AppCardLoader,
    private val notificationChannelManager: NotificationChannelManager,
) {

    enum class CardImportStage {
        Idle,
        Receiving,
        Complete,
        Failed,
    }

    data class CardImportProgress(
        val stage: CardImportStage,
        val bytesReceived: Long,
        val cardName: String?,
    ) {
        companion object {
            fun idle(): CardImportProgress = CardImportProgress(CardImportStage.Idle, 0L, null)
        }
    }

    enum class CardImportState {
        IDLE,
        TRANSFERRING,
        PROCESSING,
        SUCCESS,
        FAILURE,
    }

    private val _cardsImported = MutableStateFlow(0)
    val cardsImported: StateFlow<Int> = _cardsImported
    private val _cardImportPercent = MutableStateFlow(0)
    val cardImportPercent: StateFlow<Int> = _cardImportPercent
    private val _cardImportProgress = MutableStateFlow(CardImportProgress.idle())
    val cardImportProgress: StateFlow<CardImportProgress> = _cardImportProgress
    private val _cardImportState = MutableStateFlow(CardImportState.IDLE)
    val cardImportState: StateFlow<CardImportState> = _cardImportState
    private val _cardImportName = MutableStateFlow<String?>(null)
    val cardImportName: StateFlow<String?> = _cardImportName

    class ImportCardResult(val success: Boolean, val cardName: String?)

    fun prepareForIncomingImport() {
        _cardImportState.value = CardImportState.TRANSFERRING
        _cardImportPercent.value = 0
        _cardImportProgress.value = CardImportProgress(CardImportStage.Receiving, 0L, null)
        _cardImportName.value = null
    }

    suspend fun importCardFromChannel(context: Context, channel: ChannelClient.Channel): ImportCardResult {
        val channelClient = Wearable.getChannelClient(context)
        var cardName: String? = null
        var success = false
        withContext(Dispatchers.IO) {
            channelClient.getInputStream(channel).await().use {rawStream ->
                try {
                    val cardStream = DataInputStream(BufferedInputStream(rawStream))
                    cardName = getName(cardStream)
                    _cardImportName.value = cardName
                    _cardImportState.value = CardImportState.TRANSFERRING
                    _cardImportProgress.value = CardImportProgress(CardImportStage.Receiving, 0L, cardName)
                    val uniqueSprites = cardStream.read() != 0
                    val payloadSize = cardStream.readInt()
                    if (payloadSize <= 0) {
                        throw IllegalStateException("Card payload was empty")
                    }
                    val cardBytes = ByteArray(payloadSize)
                    var totalRead = 0
                    _cardImportPercent.value = 0
                    notificationChannelManager.sendProgressNotification(
                        context,
                        "Importing $cardName",
                        0,
                        NotificationChannelManager.CARD_IMPORT_PROGRESS_ID,
                    )
                    while (totalRead < payloadSize) {
                        val bytesRead = cardStream.read(cardBytes, totalRead, payloadSize - totalRead)
                        if (bytesRead < 0) {
                            throw IllegalStateException("Card transfer ended early")
                        }
                        totalRead += bytesRead
                        val transferPercent = ((totalRead * 100L) / payloadSize).toInt()
                        val mappedPercent = (transferPercent * 90) / 100
                        _cardImportPercent.value = mappedPercent
                        _cardImportProgress.value = CardImportProgress(CardImportStage.Receiving, totalRead.toLong(), cardName)
                        notificationChannelManager.sendProgressNotification(
                            context,
                            "Importing $cardName",
                            mappedPercent,
                            NotificationChannelManager.CARD_IMPORT_PROGRESS_ID,
                        )
                    }
                    _cardImportPercent.value = 95
                    _cardImportState.value = CardImportState.PROCESSING
                    notificationChannelManager.sendProgressNotification(
                        context,
                        "Importing $cardName",
                        95,
                        NotificationChannelManager.CARD_IMPORT_PROGRESS_ID,
                    )
                    cardLoader.importCard(context, cardName, ByteArrayInputStream(cardBytes), uniqueSprites)
                    success = true
                    _cardsImported.value++
                    _cardImportPercent.value = 100
                    _cardImportState.value = CardImportState.SUCCESS
                    _cardImportProgress.value = CardImportProgress(CardImportStage.Complete, totalRead.toLong(), cardName)
                    notificationChannelManager.sendProgressNotification(
                        context,
                        "Importing $cardName",
                        100,
                        NotificationChannelManager.CARD_IMPORT_PROGRESS_ID,
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Unable to load received card data")
                    _cardImportState.value = CardImportState.FAILURE
                    _cardImportProgress.value = CardImportProgress(CardImportStage.Failed, _cardImportProgress.value.bytesReceived, cardName)
                    notificationChannelManager.cancelNotification(NotificationChannelManager.CARD_IMPORT_PROGRESS_ID)
                }
            }
            channelClient.close(channel)
        }
        return ImportCardResult(success, cardName)
    }

    private fun getName(inputStream: InputStream): String {
        var lastReadByte: Int
        val nameBytes = ByteArrayOutputStream()
        do {
            lastReadByte = inputStream.read()
            if(lastReadByte != 0) {
                nameBytes.write(lastReadByte)
            }
        } while(lastReadByte != 0)
        return nameBytes.toString(Charset.defaultCharset().name())
    }

}