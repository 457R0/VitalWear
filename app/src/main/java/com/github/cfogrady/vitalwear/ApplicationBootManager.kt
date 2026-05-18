package com.github.cfogrady.vitalwear

import android.content.Context
import com.github.cfogrady.vitalwear.character.VBUpdater
import com.github.cfogrady.vitalwear.character.CharacterManagerImpl
import com.github.cfogrady.vitalwear.character.mood.MoodService
import com.github.cfogrady.vitalwear.complications.ComplicationRefreshService
import com.github.cfogrady.vitalwear.firmware.FirmwareManager
import com.github.cfogrady.vitalwear.notification.NotificationChannelManager
import com.github.cfogrady.vitalwear.steps.StepSensorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import timber.log.Timber

class ApplicationBootManager(private val characterManager: CharacterManagerImpl,
                             private val firmwareManager: FirmwareManager,
                             private val stepService: StepSensorService,
                             private val vbUpdater: VBUpdater,
                             private val moodService: MoodService,
                             private val notificationChannelManager: NotificationChannelManager,
                             private val complicationRefreshService: ComplicationRefreshService,
) {

    @Synchronized
    fun onStartup(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            supervisorScope {
                // parallelize firmware manager, character manager, step service, and notificationChannelManager
                launch {
                    runCatching { firmwareManager.loadFirmware(context) }
                        .onFailure { Timber.e(it, "Firmware startup task failed") }
                }
                launch {
                    runCatching {
                        characterManager.init(context, vbUpdater)
                        moodService.initialize()
                        complicationRefreshService.startupPartnerComplications()
                    }.onFailure { Timber.e(it, "Character/mood startup task failed") }
                }
                launch {
                    runCatching { stepService.startup() }
                        .onFailure { Timber.e(it, "Step sensor startup task failed") }
                }
                launch {
                    runCatching { notificationChannelManager.createNotificationChannel() }
                        .onFailure { Timber.e(it, "Notification channel startup task failed") }
                }
            }
        }
    }
}