package com.github.cfogrady.vitalwear.card

import android.content.Context
import com.github.cfogrady.vitalwear.notification.NotificationChannelManager
import com.github.cfogrady.vitalwear.notification.NotificationChannelManager
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import timber.log.Timber
import java.io.DataInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
<<<<<<< HEAD
class CardReceiver(
    private val cardLoader: AppCardLoader,
    private val notificationChannelManager: NotificationChannelManager,
) {
=======
import java.io.FilterInputStream
>>>>>>> b88a756 (VBHelper transfer interop, fix VitalBox centering, fix AdventureMenuScreen preview)
import java.io.InputStream
import java.nio.charset.Charset

class CardReceiver(
    private val cardLoader: AppCardLoader,
    private val notificationChannelManager: NotificationChannelManager,
) {

    enum class CardImportState {
        IDLE,
        TRANSFERRING,
        PROCESSING,
        SUCCESS,
        FAILURE,
    }

    enum class CardImportStage {
        Idle,
        Receiving,
        Complete,
        Failed,
    }
    fun prepareForIncomingImport() {
        _cardImportProgress.value = CardImportProgress(CardImportStage.Receiving, 0L, null)
    }


    data class CardImportProgress(
        val stage: CardImportStage,
        val bytesReceived: Long,
            fun idle(): CardImportProgress = CardImportProgress(CardImportStage.Idle, 0L, null)
            channelClient.getInputStream(channel).await().use { rawStream ->
    private val _cardImportState = MutableStateFlow(CardImportState.IDLE)
                    val cardStream = DataInputStream(BufferedInputStream(rawStream))
    val cardImportState: StateFlow<CardImportState> = _cardImportState
                    _cardImportProgress.value = CardImportProgress(CardImportStage.Receiving, 0L, cardName)
    val cardImportName: StateFlow<String?> = _cardImportName
                    val payloadSize = cardStream.readInt()
                    if (payloadSize <= 0) {
                        throw IllegalStateException("Card payload was empty")
                    }
                    val cardBytes = ByteArray(payloadSize)
                    var totalRead = 0
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
                        val mappedPercent = ((totalRead * 90L) / payloadSize).toInt()
                        _cardImportProgress.value = CardImportProgress(CardImportStage.Receiving, totalRead.toLong(), cardName)
                        notificationChannelManager.sendProgressNotification(
                            context,
                            "Importing $cardName",
                            mappedPercent,
                            NotificationChannelManager.CARD_IMPORT_PROGRESS_ID,
                        )
                    }
                    notificationChannelManager.sendProgressNotification(
                        context,
                        "Importing $cardName",
                        95,
                        NotificationChannelManager.CARD_IMPORT_PROGRESS_ID,
                    )
                    cardLoader.importCard(context, cardName, ByteArrayInputStream(cardBytes), uniqueSprites)
    private val _cardImportProgress = MutableStateFlow(CardImportProgress.idle())
    val cardImportProgress: StateFlow<CardImportProgress> = _cardImportProgress
                    _cardImportProgress.value = CardImportProgress(CardImportStage.Complete, totalRead.toLong(), cardName)
                    notificationChannelManager.sendProgressNotification(
                        context,
                        "Importing $cardName",
                        100,
                        NotificationChannelManager.CARD_IMPORT_PROGRESS_ID,
                    )

                    Timber.e(e, "Unable to load received card data")
                    _cardImportProgress.value = CardImportProgress(CardImportStage.Failed, _cardImportProgress.value.bytesReceived, cardName)
                    notificationChannelManager.cancelNotification(NotificationChannelManager.CARD_IMPORT_PROGRESS_ID)
    fun prepareForIncomingImport() {
        _cardImportState.value = CardImportState.TRANSFERRING
        _cardImportProgress.value = 0
        _cardImportName.value = null
    }

    suspend fun importCardFromChannel(context: Context, channel: ChannelClient.Channel): ImportCardResult {
>>>>>>> b88a756 (VBHelper transfer interop, fix VitalBox centering, fix AdventureMenuScreen preview)
                try {
                    val cardStream = DataInputStream(BufferedInputStream(rawStream))
                    cardName = getName(cardStream)
<<<<<<< HEAD
                    _cardImportName.value = cardName
                    _cardImportState.value = CardImportState.TRANSFERRING
=======
                    _cardImportProgress.value = CardImportProgress(CardImportStage.Receiving, bytesReceived, cardName)
>>>>>>> b88a756 (VBHelper transfer interop, fix VitalBox centering, fix AdventureMenuScreen preview)
                    val uniqueSprites = cardStream.read() != 0
                    val payloadSize = cardStream.readInt()
                    if (payloadSize <= 0) {
                        throw IllegalStateException("Card payload was empty")
                    }
                    val cardBytes = ByteArray(payloadSize)
                    var totalRead = 0
                    _cardImportProgress.value = 0
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
                        _cardImportProgress.value = mappedPercent
                        notificationChannelManager.sendProgressNotification(
                            context,
                            "Importing $cardName",
                            mappedPercent,
                            NotificationChannelManager.CARD_IMPORT_PROGRESS_ID,
                        )
                    }
                    _cardImportProgress.value = 95
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
<<<<<<< HEAD
                    _cardImportProgress.value = 100
                    _cardImportState.value = CardImportState.SUCCESS
                    notificationChannelManager.sendProgressNotification(
                        context,
                        "Importing $cardName",
                        100,
                        NotificationChannelManager.CARD_IMPORT_PROGRESS_ID,
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Unable to load received card data")
                    _cardImportState.value = CardImportState.FAILURE
                    notificationChannelManager.cancelNotification(NotificationChannelManager.CARD_IMPORT_PROGRESS_ID)
=======
                    _cardImportProgress.value = CardImportProgress(CardImportStage.Complete, bytesReceived, cardName)
                } catch (e: Exception) {
                    Timber.e("Unable to load received card data", e)
                    _cardImportProgress.value = CardImportProgress(CardImportStage.Failed, bytesReceived, cardName)
>>>>>>> b88a756 (VBHelper transfer interop, fix VitalBox centering, fix AdventureMenuScreen preview)
                }
            }
            channelClient.close(channel).await()
        }
        return ImportCardResult(success, cardName)
    }

    private class ProgressInputStream(inputStream: InputStream, val onRead: (Int) -> Unit) : FilterInputStream(inputStream) {
        override fun read(): Int {
            val value = super.read()
            if (value >= 0) {
                onRead(1)
            }
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val readBytes = super.read(b, off, len)
            if (readBytes > 0) {
                onRead(readBytes)
            }
            return readBytes
        }
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