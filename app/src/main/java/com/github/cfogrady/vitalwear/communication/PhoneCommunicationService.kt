package com.github.cfogrady.vitalwear.communication

import android.content.Intent
import android.net.Uri
import com.github.cfogrady.vitalwear.VitalWearApp
import com.github.cfogrady.vitalwear.card.LoadCardActivity
import com.github.cfogrady.vitalwear.common.communication.ChannelTypes
import com.github.cfogrady.vitalwear.common.log.TinyLogTree
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class PhoneCommunicationService  : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        if(messageEvent.path==ChannelTypes.SEND_LOGS_REQUEST) {
            val mostRecentLog = TinyLogTree.getMostRecentLogFile(this)
            val channelClient = Wearable.getChannelClient(this)
            CoroutineScope(Dispatchers.IO).launch {
                val channel = channelClient.openChannel(messageEvent.sourceNodeId, ChannelTypes.LOGS_DATA).await()
                channelClient.sendFile(channel, Uri.fromFile(mostRecentLog)).apply {
                    addOnFailureListener {
                        Timber.e(it, "Failed to send log to phone")
                    }
                }
            }
        }
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        super.onChannelOpened(channel)
        when(channel.path) {
            ChannelTypes.CARD_DATA -> {
                val cardReceiver = (application as VitalWearApp).cardReceiver
                cardReceiver.prepareForIncomingImport()
                runCatching {
                    val loadCardIntent = Intent(applicationContext, LoadCardActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    applicationContext.startActivity(loadCardIntent)
                }.onFailure {
                    Timber.w(it, "Unable to launch card import progress activity")
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val result = cardReceiver.importCardFromChannel(applicationContext, channel)
                    val notificationChannelManager = (application as VitalWearApp).notificationChannelManager
                    if(result.success) {
                        notificationChannelManager.sendGenericNotification(applicationContext, "${result.cardName} Import Successful", "")
                    } else {
                        val notificationCardName = result.cardName ?: "Card"
                        notificationChannelManager.sendGenericNotification(applicationContext, "$notificationCardName Import Failed", "")
                    }
                }
            }
            ChannelTypes.CHARACTER_DATA -> {
                CoroutineScope(Dispatchers.IO).launch {
                    Wearable.getChannelClient(applicationContext).close(channel).await()
                    val notificationChannelManager = (application as VitalWearApp).notificationChannelManager
                    notificationChannelManager.sendGenericNotification(
                        applicationContext,
                        "Character Transfer Not Supported",
                        "Use watch Transfer mode for Digimon transfer."
                    )
                }
            }
            ChannelTypes.FIRMWARE_DATA -> {
                CoroutineScope(Dispatchers.IO).launch {
                    (application as VitalWearApp).firmwareReceiver.importFirmwareFromChannel(applicationContext, channel)
                }
            }
            else -> {
                Timber.i("Unknown channel: ${channel.path}")
            }
        }
    }
}