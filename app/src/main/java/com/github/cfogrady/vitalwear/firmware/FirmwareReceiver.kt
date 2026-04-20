package com.github.cfogrady.vitalwear.firmware

import android.content.Context
import com.github.cfogrady.vitalwear.notification.NotificationChannelManager
import com.google.android.gms.wearable.ChannelClient.Channel
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.DataInputStream

class FirmwareReceiver(private val firmwareManager: FirmwareManager, private val notificationChannelManager: NotificationChannelManager) {
    private val _firmwareUpdates = MutableStateFlow(0)
    val firmwareUpdates: StateFlow<Int> = _firmwareUpdates
    private val _firmwareImportProgress = MutableStateFlow(0)
    val firmwareImportProgress: StateFlow<Int> = _firmwareImportProgress

    suspend fun importFirmwareFromChannel(context: Context, channel: Channel) {
        withContext(Dispatchers.IO) {
            val channelClient = Wearable.getChannelClient(context)
            try {
                _firmwareImportProgress.value = 0
                notificationChannelManager.sendProgressNotification(
                    context,
                    "Importing Firmware",
                    0,
                    NotificationChannelManager.FIRMWARE_IMPORT_PROGRESS_ID,
                )
                channelClient.getInputStream(channel).await().use { rawInput ->
                    val channelInput = DataInputStream(BufferedInputStream(rawInput))
                    val payloadSize = channelInput.readInt()
                    if (payloadSize <= 0) {
                        throw IllegalStateException("Firmware payload was empty")
                    }
                    context.contentResolver.openOutputStream(firmwareManager.firmwareUri(context), "w").use { firmwareOutput ->
                        if (firmwareOutput == null) {
                            throw IllegalStateException("Unable to open firmware output stream")
                        }
                        val transferBuffer = ByteArray(4096)
                        var totalRead = 0
                        while (totalRead < payloadSize) {
                            val bytesToRead = minOf(transferBuffer.size, payloadSize - totalRead)
                            val bytesRead = channelInput.read(transferBuffer, 0, bytesToRead)
                            if (bytesRead < 0) {
                                throw IllegalStateException("Firmware transfer ended early")
                            }
                            firmwareOutput.write(transferBuffer, 0, bytesRead)
                            totalRead += bytesRead
                            val transferPercent = ((totalRead * 100L) / payloadSize).toInt()
                            val mappedPercent = (transferPercent * 90) / 100
                            _firmwareImportProgress.value = mappedPercent
                            notificationChannelManager.sendProgressNotification(
                                context,
                                "Importing Firmware",
                                mappedPercent,
                                NotificationChannelManager.FIRMWARE_IMPORT_PROGRESS_ID,
                            )
                        }
                    }
                }
                _firmwareImportProgress.value = 95
                notificationChannelManager.sendProgressNotification(
                    context,
                    "Applying Firmware",
                    95,
                    NotificationChannelManager.FIRMWARE_IMPORT_PROGRESS_ID,
                )
                firmwareManager.loadFirmware(context)
                _firmwareImportProgress.value = 100
                _firmwareUpdates.value++
                Timber.i("Firmware fully received")
                notificationChannelManager.sendProgressNotification(
                    context,
                    "Firmware Import Complete",
                    100,
                    NotificationChannelManager.FIRMWARE_IMPORT_PROGRESS_ID,
                )
                notificationChannelManager.sendGenericNotification(context, "New Firmware Loaded", "")
            } finally {
                channelClient.close(channel)
            }
        }
    }

}