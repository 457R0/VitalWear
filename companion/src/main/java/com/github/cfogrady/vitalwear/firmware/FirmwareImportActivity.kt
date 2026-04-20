package com.github.cfogrady.vitalwear.firmware

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.github.cfogrady.vitalwear.Loading
import com.github.cfogrady.vitalwear.common.communication.ChannelTypes
import com.github.cfogrady.vitalwear.common.composable.util.KeepScreenOn
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.DataOutputStream


class FirmwareImportActivity : ComponentActivity() {

    enum class FirmwareImportState {
        PickFirmware,
        LoadFirmware,
    }

    var importState = MutableStateFlow(FirmwareImportState.PickFirmware)
    private val transferPercent = MutableStateFlow(0)
    private val transferStatus = MutableStateFlow("Preparing transfer")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val firmwareImportActivity = buildFirmwareFilePickLauncher()
        setContent{
            BuildScreen(firmwareImportActivity)
        }
    }

    @Composable
    fun BuildScreen(firmwareImportActivity: ActivityResultLauncher<Array<String>>) {
        KeepScreenOn()
        val state by importState.collectAsState()
        val percent by transferPercent.collectAsState()
        val status by transferStatus.collectAsState()
        if(state == FirmwareImportState.PickFirmware) {
            LaunchedEffect(true ) {
                firmwareImportActivity.launch(arrayOf("*/*"))
            }
        }
        val loadingText = if (state == FirmwareImportState.PickFirmware) {
            "Select firmware file"
        } else {
            "$status ($percent%)"
        }
        Loading(loadingText = loadingText) {}
    }

    private fun buildFirmwareFilePickLauncher(): ActivityResultLauncher<Array<String>> {
        return registerForActivityResult(ActivityResultContracts.OpenDocument()) {
            importState.value = FirmwareImportState.LoadFirmware
            if(it == null) {
                finish()
            } else {
                Timber.i("Path: ${it.path}")
                importFirmware(it)
            }
        }
    }

    private fun importFirmware(uri: Uri) {
        transferPercent.value = 0
        transferStatus.value = "Reading firmware file"
        val channelClient = Wearable.getChannelClient(this)
        val nodeListTask = Wearable.getNodeClient(this).connectedNodes
        lifecycleScope.launch(Dispatchers.IO) {
            val nodes = nodeListTask.await()
            lateinit var firmware: ByteArray
            contentResolver.openInputStream(uri).use {
                firmware = it!!.readBytes()
            }
            transferPercent.value = 5
            transferStatus.value = "Sending firmware"
            val totalBytes = (firmware.size + 4).toLong() * nodes.size
            var transferredBytes = 0L
            for (node in nodes) {
                val channel = channelClient.openChannel(node.id, ChannelTypes.FIRMWARE_DATA).await()
                // We don't use send file because we can't make use of the uri received from the file picker with sendFile. We need a full file path, to which we don't have access.
                channelClient.getOutputStream(channel).await().use {
                    val output = DataOutputStream(it)
                    Timber.i("Writing to firmware to watch")
                    output.writeInt(firmware.size)
                    transferredBytes += 4
                    transferPercent.value = if (totalBytes == 0L) 0 else ((transferredBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    firmware.inputStream().use { firmwareInput ->
                        while (firmwareInput.read(buffer).also { bytesRead = it } >= 0) {
                            output.write(buffer, 0, bytesRead)
                            transferredBytes += bytesRead
                            transferPercent.value = if (totalBytes == 0L) 0 else ((transferredBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        }
                    }
                    output.flush()
                    Timber.i("Done writing to firmware to watch")
                }
                Timber.i("Closing channel to watch")
                channelClient.close(channel).await()
            }
            transferStatus.value = "Transfer complete"
            transferPercent.value = 100
            withContext(Dispatchers.Main) {
                finish()
            }
        }
    }
}