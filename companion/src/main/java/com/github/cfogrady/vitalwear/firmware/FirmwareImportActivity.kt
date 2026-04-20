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

    data class TransferProgress(val bytesSent: Long, val totalBytes: Long) {
        fun percent(): Int {
            if (totalBytes <= 0) return 0
            return ((bytesSent * 100) / totalBytes).toInt().coerceIn(0, 100)
        }
    }

    enum class FirmwareImportState {
        PickFirmware,
        LoadFirmware,
    }

    var importState = MutableStateFlow(FirmwareImportState.PickFirmware)
    private val transferProgress = MutableStateFlow(TransferProgress(0, 0))

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
        val progress by transferProgress.collectAsState()
        if(state == FirmwareImportState.PickFirmware) {
            LaunchedEffect(true) {
                firmwareImportActivity.launch(arrayOf("*/*"))
            }
        }
        Loading(loadingText = buildProgressText(progress)) {}
    }

    private fun buildProgressText(progress: TransferProgress): String {
        if (progress.totalBytes <= 0) {
            return "Importing Firmware... May take up to 30 seconds"
        }
        val sentKb = progress.bytesSent / 1024
        val totalKb = progress.totalBytes / 1024
        return "Importing Firmware... ${progress.percent()}% (${sentKb}KB/${totalKb}KB)"
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
        val channelClient = Wearable.getChannelClient(this)
        val nodeListTask = Wearable.getNodeClient(this).connectedNodes
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val nodes = nodeListTask.await()
                if (nodes.isEmpty()) {
                    Timber.w("No connected watch nodes found for firmware import")
                    runOnUiThread {
                        Toast.makeText(this@FirmwareImportActivity, "No connected watch found", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    return@launch
                }

                lateinit var firmware: ByteArray
                contentResolver.openInputStream(uri).use {
                    firmware = it!!.readBytes()
                }
                // total = 4-byte int size header + firmware payload
                val totalBytes = (4 + firmware.size).toLong()
                transferProgress.value = TransferProgress(0, totalBytes)

                for (node in nodes) {
                    val channel = channelClient.openChannel(node.id, ChannelTypes.FIRMWARE_DATA).await()
                    channelClient.getOutputStream(channel).await().use {
                        val output = DataOutputStream(it)
                        Timber.i("Writing firmware to watch")
                        output.writeInt(firmware.size)
                        transferProgress.value = TransferProgress(4L, totalBytes)

                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        var transferredBytes = 4L
                        firmware.inputStream().use { firmwareInput ->
                            while (firmwareInput.read(buffer).also { bytesRead = it } >= 0) {
                                output.write(buffer, 0, bytesRead)
                                transferredBytes += bytesRead
                                transferProgress.value = TransferProgress(transferredBytes, totalBytes)
                            }
                        }
                        output.flush()
                        Timber.i("Done writing firmware to watch")
                    }
                    Timber.i("Closing channel to watch")
                    channelClient.close(channel).await()
                    withContext(Dispatchers.Main) { finish() }
                    return@launch
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to import firmware")
                runOnUiThread {
                    Toast.makeText(this@FirmwareImportActivity, "Firmware import failed", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}