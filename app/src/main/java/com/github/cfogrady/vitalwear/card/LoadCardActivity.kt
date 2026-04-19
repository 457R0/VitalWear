package com.github.cfogrady.vitalwear.card

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.Text
import com.github.cfogrady.vitalwear.VitalWearApp
import kotlinx.coroutines.delay

class LoadCardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cardReceiver = (application as VitalWearApp).cardReceiver
        setContent {
            val importState by cardReceiver.cardImportState.collectAsState()
            val importProgress by cardReceiver.cardImportProgress.collectAsState()
            val cardName by cardReceiver.cardImportName.collectAsState()

            LaunchedEffect(importState) {
                if (importState == CardReceiver.CardImportState.SUCCESS || importState == CardReceiver.CardImportState.FAILURE) {
                    delay(1000)
                    finish()
                }
            }

            val loadingText = when (importState) {
                CardReceiver.CardImportState.PROCESSING -> "Applying ${cardName ?: "card"}"
                CardReceiver.CardImportState.SUCCESS -> "${cardName ?: "Card"} imported"
                CardReceiver.CardImportState.FAILURE -> "${cardName ?: "Card"} import failed"
                else -> "Importing ${cardName ?: "card"} ${importProgress.coerceIn(0, 100)}%"
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = loadingText, textAlign = TextAlign.Center)
            }
        }
    }
}

