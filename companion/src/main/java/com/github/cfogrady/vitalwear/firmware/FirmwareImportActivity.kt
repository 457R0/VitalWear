package com.github.cfogrady.vitalwear.firmware

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
            try {
                Timber.i("Starting firmware import, reading file from $uri")
                val nodes = nodeListTask.await()
                Timber.i("Found ${nodes.size} connected nodes")
                if (nodes.isEmpty()) {
                    transferStatus.value = "No watch connected"
                    Timber.w("No connected watch found")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FirmwareImportActivity, "No connected watch found", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val firmware = contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes()
                } ?: throw IllegalStateException("Unable to read selected firmware file")
                Timber.i("Read firmware file, size=${firmware.size} bytes")

                transferPercent.value = 5
                transferStatus.value = "Sending firmware"
                val totalBytes = (firmware.size + 4).toLong() * nodes.size
                var transferredBytes = 0L
                for (node in nodes) {
                    Timber.i("Opening channel to node ${node.id} (${node.displayName})")
                    try {
                        val channel = channelClient.openChannel(node.id, ChannelTypes.FIRMWARE_DATA).await()
                        Timber.i("Channel opened successfully")
                        try {
                            // We don't use sendFile because OpenDocument returns a content URI.
                            channelClient.getOutputStream(channel).await().use {
                                val output = DataOutputStream(it)
                                Timber.i("Writing firmware to watch ${node.displayName} (${node.id})")
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
                                Timber.i("Firmware written successfully to ${node.displayName}")
                            }
                        } finally {
                            runCatching { channelClient.close(channel).await() }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to send firmware to node ${node.id}")
                        throw e
                    }
                }
                transferStatus.value = "Transfer complete"
                transferPercent.value = 100
                Timber.i("Firmware transfer complete")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FirmwareImportActivity, "Firmware sent successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to send firmware to watch")
                transferStatus.value = "Transfer failed: ${e.message ?: "unknown"}"
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FirmwareImportActivity, "Firmware transfer failed: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}