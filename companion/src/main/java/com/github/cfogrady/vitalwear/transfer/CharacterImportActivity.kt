package com.github.cfogrady.vitalwear.transfer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.github.cfogrady.vitalwear.common.communication.ChannelTypes
import com.github.cfogrady.vitalwear.protos.Character
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class CharacterImportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val importUri = extractImportUri(intent)
        if (importUri == null) {
            Toast.makeText(this, "No VitalWear character payload found", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                contentResolver.openInputStream(importUri).use { inputStream ->
                    requireNotNull(inputStream) { "Unable to open import stream" }
                    val character = Character.parseFrom(inputStream)
                    sendCharacterToWatch(character)
                }
            }.getOrElse {
                Timber.e(it, "Failed to import shared VitalWear character")
                false
            }

            runOnUiThread {
                val message = if (result) {
                    "Character sent to watch"
                } else {
                    "Failed to send character to watch"
                }
                Toast.makeText(this@CharacterImportActivity, message, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun extractImportUri(intent: Intent?): Uri? {
        if (intent == null || intent.action != Intent.ACTION_SEND) {
            return null
        }
        @Suppress("DEPRECATION")
        return intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }

    private suspend fun sendCharacterToWatch(character: Character): Boolean {
        val channelClient = Wearable.getChannelClient(this)
        val nodes = Wearable.getNodeClient(this).connectedNodes.await()
        if (nodes.isEmpty()) {
            return false
        }

        var sentAny = false
        for (node in nodes) {
            runCatching {
                val channel = channelClient.openChannel(node.id, ChannelTypes.CHARACTER_DATA).await()
                channelClient.getOutputStream(channel).await().use { output ->
                    output.write(character.toByteArray())
                }
                channelClient.close(channel).await()
                sentAny = true
            }.onFailure {
                Timber.e(it, "Failed sending character to node ${node.id}")
            }
        }
        return sentAny
    }
}

