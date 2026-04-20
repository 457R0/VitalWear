package com.github.cfogrady.vitalwear.firmware

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Text
import com.github.cfogrady.vitalwear.VitalWearApp

class LoadFirmwareActivity  : ComponentActivity() {
    companion object {
        val TAG = "LoadFirmwareActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val firmwareReceiver = (application as VitalWearApp).firmwareReceiver
        setContent {
<<<<<<< HEAD
            val firmwareUploads by firmwareReceiver.firmwareUpdates.collectAsState()
                if (firmwareProgress.stage == FirmwareReceiver.FirmwareImportStage.Complete) {
            LaunchedEffect(firmwareUploads) {
                if(firmwareUploads > 0) {
=======
            val firmwareProgress by firmwareReceiver.firmwareImportProgress.collectAsState()
            LaunchedEffect(firmwareProgress.stage) {
                if(firmwareProgress.stage == FirmwareReceiver.FirmwareImportStage.Complete) {
>>>>>>> b88a756 (VBHelper transfer interop, fix VitalBox centering, fix AdventureMenuScreen preview)
                    finish()
                }
            }
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
<<<<<<< HEAD
                val loadingText = if (firmwareProgress > 0) {
                    "Importing Firmware $firmwareProgress%"
                } else {
                    "Import Firmware From Phone To Continue"
                }
                Text(text = loadingText, textAlign = TextAlign.Center)
=======
                CircularProgressIndicator(modifier = Modifier.padding(bottom = 8.dp))
                Text(text = getFirmwareProgressMessage(firmwareProgress), textAlign = TextAlign.Center)
>>>>>>> b88a756 (VBHelper transfer interop, fix VitalBox centering, fix AdventureMenuScreen preview)
            }
        }
    }

    private fun getFirmwareProgressMessage(progress: FirmwareReceiver.FirmwareImportProgress): String {
        return when (progress.stage) {
            FirmwareReceiver.FirmwareImportStage.Receiving -> {
                val kb = progress.bytesReceived / 1024
                "Importing Firmware From Phone... ${kb}KB"
            }
            FirmwareReceiver.FirmwareImportStage.Loading -> "Firmware received. Loading..."
            FirmwareReceiver.FirmwareImportStage.Failed -> "Firmware import failed. Retry from phone."
            else -> "Import Firmware From Phone To Continue"
        }
    }
}