package com.github.cfogrady.vitalwear.battle.composable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.cfogrady.vitalwear.battle.data.PreBattleModel
import com.github.cfogrady.vitalwear.composable.util.BitmapScaler
import kotlinx.coroutines.delay

class OpponentSplashFactory(private val bitmapScaler: BitmapScaler) {

    @Composable
    fun OpponentSplash(battleModel: PreBattleModel, stateUpdater: (FightTargetState) -> Unit) {
        var leftScreenEarly = remember { false }
        BackHandler {
            leftScreenEarly = true
            stateUpdater.invoke(FightTargetState.END_FIGHT)
        }
        bitmapScaler.FullScreenBackground(
            bitmap = battleModel.background,
            contentDescription = "Background",
        )
        val battleCharacter = battleModel.opponent
        bitmapScaler.ScaledBitmap(bitmap = battleCharacter.battleSprites.splashBitmap, contentDescription = "Opponent", alignment = Alignment.BottomCenter,
            modifier = Modifier.clickable {
                leftScreenEarly = true
                stateUpdater.invoke(FightTargetState.READY)
            })
        LaunchedEffect(Unit) {
            delay(1000)
            if(!leftScreenEarly) {
                stateUpdater.invoke(FightTargetState.OPPONENT_NAME)
            }
        }
    }
}