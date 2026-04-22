package com.github.cfogrady.vitalwear.transfer

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.github.cfogrady.vitalwear.R
import com.github.cfogrady.vitalwear.character.VBCharacter
import com.github.cfogrady.vitalwear.common.card.CardSpriteLoader
import com.github.cfogrady.vitalwear.common.character.CharacterSprites
import com.github.cfogrady.vitalwear.composable.util.BitmapScaler
import com.github.cfogrady.vitalwear.composable.util.ImageScaler
import com.github.cfogrady.vitalwear.composable.util.VitalBoxFactory
import com.github.cfogrady.vitalwear.firmware.Firmware
import com.github.cfogrady.vitalwear.protos.Character
import com.github.cfogrady.vitalwear.transfer.hce.VitalWearHceSessionManager
import com.github.cfogrady.vitalwear.transfer.TransferScreenController.ReceiveCharacterSprites
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TransferState {
    ENTRY,
    WAITING,
    TRANSFERRED,
}

enum class SendOrReceive {
    SEND,
    RECEIVE
}

enum class HceTransferResult {
    TRANSFERRING,
    SUCCESS,
    FAILURE,
}

interface TransferScreenController: SendAnimationController, ReceiveAnimationController {
    fun endActivityWithToast(msg: String)

    suspend fun getActiveCharacterProto(): Character?

    fun getActiveCharacter(): VBCharacter?

    fun deleteActiveCharacter()

    fun finish()

    suspend fun receiveCharacter(character: Character): Boolean

    data class ReceiveCharacterSprites(val idle: Bitmap, val happy: Bitmap)
}


@Composable
fun TransferScreen(controller: TransferScreenController) {
    val coroutineScope = rememberCoroutineScope()
    val hceStatus by VitalWearHceSessionManager.transferStatus.collectAsState()
    var state by remember { mutableStateOf(TransferState.ENTRY) }
    var sendOrReceive by remember { mutableStateOf(SendOrReceive.SEND) }
    var resultStatus by remember { mutableStateOf(HceTransferResult.TRANSFERRING) }
    when(state) {
        TransferState.ENTRY -> SelectSendOrReceive(onSelect = {
            if (it == SendOrReceive.SEND) {
                coroutineScope.launch(Dispatchers.IO) {
                    val character = controller.getActiveCharacterProto()
                    if (character == null) {
                        controller.endActivityWithToast("No active character to send!")
                    } else {
                        VitalWearHceSessionManager.armSend(character.toByteArray())
                        sendOrReceive = it
                        state = TransferState.WAITING
                    }
                }
            } else {
                VitalWearHceSessionManager.armReceive()
                sendOrReceive = it
                state = TransferState.WAITING
            }
        })
        TransferState.WAITING -> {
            LaunchedEffect(hceStatus) {
                when (hceStatus) {
                    VitalWearHceSessionManager.TransferStatus.SUCCESS -> {
                        resultStatus = HceTransferResult.SUCCESS
                        state = TransferState.TRANSFERRED
                    }
                    VitalWearHceSessionManager.TransferStatus.FAILURE -> {
                        resultStatus = HceTransferResult.FAILURE
                        state = TransferState.TRANSFERRED
                    }
                    else -> {}
                }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val msg = when (hceStatus) {
                    VitalWearHceSessionManager.TransferStatus.ARMED_SEND -> stringResource(R.string.transfer_waiting_send)
                    VitalWearHceSessionManager.TransferStatus.ARMED_RECEIVE -> stringResource(R.string.transfer_waiting_receive)
                    VitalWearHceSessionManager.TransferStatus.SUCCESS -> stringResource(R.string.transfer_waiting_success)
                    VitalWearHceSessionManager.TransferStatus.FAILURE -> stringResource(R.string.transfer_waiting_failure)
                    VitalWearHceSessionManager.TransferStatus.IDLE -> stringResource(R.string.transfer_waiting_preparing)
                }
                Text(msg)
            }
        }
        TransferState.TRANSFERRED -> {
            TransferResult(controller, sendOrReceive, resultStatus, onComplete = controller::finish)
        }
    }
}

@Preview(
    device = WearDevices.LARGE_ROUND,
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
fun SelectSendOrReceive(onSelect: (SendOrReceive) -> Unit = {}) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.transfer_title_app_link))
        CompactButton(onClick = {onSelect.invoke(SendOrReceive.SEND)}) {
            Text(stringResource(R.string.transfer_button_send_to_vbh))
        }
        CompactButton(onClick = {onSelect.invoke(SendOrReceive.RECEIVE)}) {
            Text(stringResource(R.string.transfer_button_receive_from_vbh))
        }
    }
}

@Composable
fun TransferResult(controller: TransferScreenController, sendOrReceive: SendOrReceive, resultStatus: HceTransferResult, onComplete: () -> Unit) {
    when(resultStatus) {
        HceTransferResult.TRANSFERRING -> {
            throw IllegalStateException("Shouldn't be looking at result is the status is still Trasnferring")
        }
        HceTransferResult.SUCCESS -> {
            if(sendOrReceive == SendOrReceive.SEND) {
                val activeCharacter = controller.getActiveCharacter()
                if (activeCharacter == null) {
                    // Source may already be removed at protocol COMMIT time for move semantics.
                    LaunchedEffect(true) {
                        onComplete.invoke()
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.transfer_waiting_success))
                    }
                } else {
                    val idle = activeCharacter.characterSprites.sprites[CharacterSprites.IDLE_1]
                    val walk = activeCharacter.characterSprites.sprites[CharacterSprites.WALK_1]
                    SendAnimation(controller, idleBitmap = idle, walkBitmap = walk) { onComplete() }
                }
            } else {
                ReceiveAnimation(controller) { onComplete() }
            }
        }
        HceTransferResult.FAILURE -> {
            controller.endActivityWithToast("Transfer Failed!")
        }
    }
}

interface SendAnimationController {
    val vitalBoxFactory: VitalBoxFactory
    val bitmapScaler: BitmapScaler
    val transferBackground: Bitmap
    val backgroundHeight: Dp
}

@Composable
fun SendAnimation(controller: SendAnimationController, idleBitmap: Bitmap, walkBitmap: Bitmap, onComplete: ()->Unit) {
    var targetAnimation by remember { mutableStateOf(0) }
    var idle by remember { mutableStateOf(true) }
    val flicker by animateIntAsState(targetAnimation, tween(
        durationMillis = 3000,
        easing = FastOutLinearInEasing
    )
    ) {
        if(it == 11) {
            onComplete.invoke()
        }
    }
    LaunchedEffect(true) {
        delay(500)
        idle = false
        delay(500)
        targetAnimation = 11
    }
    controller.vitalBoxFactory.VitalBox {
        controller.bitmapScaler.ScaledBitmap(controller.transferBackground, "Background", alignment = Alignment.BottomCenter)

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            if(flicker % 2 == 0) {
                controller.bitmapScaler.ScaledBitmap(if(idle) idleBitmap else walkBitmap, "Character", alignment = Alignment.BottomCenter,
                    modifier = Modifier.offset(y = controller.backgroundHeight.times(-0.05f)))
            }
        }
    }
}

@Preview(
    device = WearDevices.LARGE_ROUND,
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
private fun SendAnimationPreview() {
    val context = LocalContext.current
    val imageScaler = remember { ImageScaler.getContextImageScaler(context) }
    val firmware = remember { Firmware.loadPreviewFirmwareFromDisk(context) }
    val characterSprites = remember { CardSpriteLoader.loadTestCharacterSprites(context, 3) }
    val sendAnimationController = remember {
        object: SendAnimationController {
            override val vitalBoxFactory = VitalBoxFactory(imageScaler)
            override val bitmapScaler = BitmapScaler(imageScaler)
            override val transferBackground = firmware.transformationBitmaps.rayOfLightBackground
            override val backgroundHeight = imageScaler.calculateBackgroundHeight()
        }
    }
    SendAnimation(controller = sendAnimationController, idleBitmap = characterSprites.sprites[CharacterSprites.IDLE_1], walkBitmap = characterSprites.sprites[CharacterSprites.WALK_1]) { }
}

interface ReceiveAnimationController {
    val vitalBoxFactory: VitalBoxFactory
    val bitmapScaler: BitmapScaler
    val transferBackground: Bitmap
    val backgroundHeight: Dp

    fun getLastReceivedCharacterSprites(): ReceiveCharacterSprites
}

@Composable
fun ReceiveAnimation(controller: ReceiveAnimationController, onComplete: () -> Unit) {
    val receivedCharacterSprites = remember { controller.getLastReceivedCharacterSprites() }
    val idleBitmap = receivedCharacterSprites.idle
    val happyBitmap = receivedCharacterSprites.happy
    var targetAnimation by remember { mutableStateOf(0) }
    var idle by remember { mutableStateOf(false) }
    var startIdleFlip by remember { mutableStateOf(false) }
    val flicker by animateIntAsState(targetAnimation, tween(
        durationMillis = 3000,
        easing = LinearOutSlowInEasing
    )
    ) {
        if(it == 11) {
            startIdleFlip = true
        }
    }
    LaunchedEffect(true) {
        targetAnimation = 11
    }
    LaunchedEffect(startIdleFlip) {
        if(startIdleFlip) {
            idle = true
            delay(500)
            idle = false
            delay(500)
            idle = true
            delay(500)
            idle = false
            onComplete.invoke()

        }
    }
    controller.vitalBoxFactory.VitalBox {
        controller.bitmapScaler.ScaledBitmap(controller.transferBackground, "Background", alignment = Alignment.BottomCenter)

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            if(flicker % 2 == 1) {
                controller.bitmapScaler.ScaledBitmap(if(idle) idleBitmap else happyBitmap, "Character", alignment = Alignment.BottomCenter,
                    modifier = Modifier.offset(y = controller.backgroundHeight.times(-0.05f)))
            }
        }
    }
}

@Preview(
    device = WearDevices.LARGE_ROUND,
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
private fun ReceiveAnimationPreview() {
    val context = LocalContext.current
    val imageScaler = remember { ImageScaler.getContextImageScaler(context) }
    val firmware = remember { Firmware.loadPreviewFirmwareFromDisk(context) }
    val characterSprites = remember { CardSpriteLoader.loadTestCharacterSprites(context, 3) }
    val receiveAnimationController = remember {
        object: ReceiveAnimationController {
            override val vitalBoxFactory = VitalBoxFactory(imageScaler)
            override val bitmapScaler = BitmapScaler(imageScaler)
            override val transferBackground = firmware.transformationBitmaps.rayOfLightBackground
            override val backgroundHeight = imageScaler.calculateBackgroundHeight()
            override fun getLastReceivedCharacterSprites(): ReceiveCharacterSprites {
                return ReceiveCharacterSprites(
                    idle = characterSprites.sprites[CharacterSprites.IDLE_1],
                    happy = characterSprites.sprites[CharacterSprites.WIN])
            }
        }
    }
    ReceiveAnimation(controller = receiveAnimationController) { }
}