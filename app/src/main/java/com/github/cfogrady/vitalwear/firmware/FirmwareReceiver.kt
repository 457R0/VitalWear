package com.github.cfogrady.vitalwear.firmware

import android.content.Context
import com.github.cfogrady.vitalwear.notification.NotificationChannelManager
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
<<<<<<< HEAD
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
        withContext(Dispatchers.IO) {
            val channelClient = Wearable.getChannelClient(context)
            try {
                        val transferBuffer = ByteArray(4096)
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
                                NotificationChannelManager.FIRMWARE_IMPORT_PROGRESS_ID,
                            firmwareOutput.write(transferBuffer, 0, bytesRead)
                            totalRead += bytesRead
                            _firmwareImportProgress.value = FirmwareImportProgress(FirmwareImportStage.Receiving, totalRead.toLong())
                            val mappedPercent = ((totalRead * 90L) / payloadSize).toInt()
                            notificationChannelManager.sendProgressNotification(
                                context,
                                "Importing Firmware",
                                mappedPercent,
                                NotificationChannelManager.FIRMWARE_IMPORT_PROGRESS_ID,
                            )
                            )
                        }
                    }
                _firmwareImportProgress.value = FirmwareImportProgress(FirmwareImportStage.Loading, _firmwareImportProgress.value.bytesReceived)
                notificationChannelManager.sendProgressNotification(
                    context,
                    "Applying Firmware",
                    95,
                    NotificationChannelManager.FIRMWARE_IMPORT_PROGRESS_ID,
                )
                firmwareManager.loadFirmware(context)
                _firmwareImportProgress.value = FirmwareImportProgress(FirmwareImportStage.Complete, _firmwareImportProgress.value.bytesReceived)
                _firmwareUpdates.value++
                Timber.i("Firmware fully received")
                notificationChannelManager.sendProgressNotification(
                    context,
                    "Firmware Import Complete",
                    100,
                    NotificationChannelManager.FIRMWARE_IMPORT_PROGRESS_ID,
                )
                notificationChannelManager.sendGenericNotification(context, "New Firmware Loaded", "")
            } catch (e: Exception) {
                Timber.e(e, "Unable to receive firmware from phone")
                _firmwareImportProgress.value = FirmwareImportProgress(FirmwareImportStage.Failed, _firmwareImportProgress.value.bytesReceived)
                notificationChannelManager.cancelNotification(NotificationChannelManager.FIRMWARE_IMPORT_PROGRESS_ID)
            } finally {
                channelClient.close(channel)
            }
                    "Firmware Import Complete",
                    100,
                    NotificationChannelManager.FIRMWARE_IMPORT_PROGRESS_ID,
                )
                notificationChannelManager.sendGenericNotification(context, "New Firmware Loaded", "")
            } finally {
                channelClient.close(channel)
=======
import java.io.File
import java.io.FileOutputStream

class FirmwareReceiver(private val firmwareManager: FirmwareManager, private val notificationChannelManager: NotificationChannelManager) {
    enum class FirmwareImportStage {
        Idle,
        Receiving,
        Loading,
        Complete,
        Failed,
    }

    data class FirmwareImportProgress(val stage: FirmwareImportStage, val bytesReceived: Long) {
        companion object {
            fun idle(): FirmwareImportProgress = FirmwareImportProgress(FirmwareImportStage.Idle, 0L)
        }
    }

    private val _firmwareUpdates = MutableStateFlow(0)
    val firmwareUpdates: StateFlow<Int> = _firmwareUpdates
    private val _firmwareImportProgress = MutableStateFlow(FirmwareImportProgress.idle())
    val firmwareImportProgress: StateFlow<FirmwareImportProgress> = _firmwareImportProgress

    suspend fun importFirmwareFromChannel(context: Context, channel: Channel) {
        val channelClient = Wearable.getChannelClient(context)
        try {
            withContext(Dispatchers.IO) {
                val firmwareFile = File(context.filesDir, FIRMWARE_FILE)
                var bytesReceived = 0L
                var lastReportedBytes = 0L
                _firmwareImportProgress.value = FirmwareImportProgress(FirmwareImportStage.Receiving, 0L)
                channelClient.getInputStream(channel).await().use { inputStream ->
                    FileOutputStream(firmwareFile).use { outputStream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val readBytes = inputStream.read(buffer)
                            if (readBytes < 0) {
                                break
                            }
                            if (readBytes == 0) {
                                continue
                            }
                            outputStream.write(buffer, 0, readBytes)
                            bytesReceived += readBytes
                            if (bytesReceived - lastReportedBytes >= 16 * 1024) {
                                lastReportedBytes = bytesReceived
                                _firmwareImportProgress.value = FirmwareImportProgress(FirmwareImportStage.Receiving, bytesReceived)
                            }
                        }
                    }
                }
                _firmwareImportProgress.value = FirmwareImportProgress(FirmwareImportStage.Loading, bytesReceived)
>>>>>>> b88a756 (VBHelper transfer interop, fix VitalBox centering, fix AdventureMenuScreen preview)
            }
            firmwareManager.loadFirmware(context)
            _firmwareUpdates.value++
            _firmwareImportProgress.value = FirmwareImportProgress(FirmwareImportStage.Complete, _firmwareImportProgress.value.bytesReceived)
            Timber.i("Firmware fully received")
            notificationChannelManager.sendGenericNotification(context, "New Firmware Loaded", "")
        } catch (e: Exception) {
            Timber.e(e, "Unable to receive firmware from phone")
            _firmwareImportProgress.value = FirmwareImportProgress(FirmwareImportStage.Failed, _firmwareImportProgress.value.bytesReceived)
            notificationChannelManager.sendGenericNotification(context, "Firmware Import Failed", "")
        } finally {
            runCatching { channelClient.close(channel).await() }
                .onFailure { Timber.w(it, "Failed to close firmware channel") }
        }
    }

}