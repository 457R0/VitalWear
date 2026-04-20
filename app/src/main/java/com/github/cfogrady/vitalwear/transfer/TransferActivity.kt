package com.github.cfogrady.vitalwear.transfer

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.ui.unit.Dp
import com.github.cfogrady.nearby.connections.p2p.NearbyP2PConnection
import com.github.cfogrady.vitalwear.VitalWearApp
import com.github.cfogrady.vitalwear.adventure.AdventureService
import com.github.cfogrady.vitalwear.character.CharacterManager
import com.github.cfogrady.vitalwear.character.VBCharacter
import com.github.cfogrady.vitalwear.character.data.CharacterEntity
import com.github.cfogrady.vitalwear.character.data.CharacterState
import com.github.cfogrady.vitalwear.character.transformation.history.TransformationHistoryEntity
import com.github.cfogrady.vitalwear.common.card.CharacterSpritesIO
import com.github.cfogrady.vitalwear.common.card.db.CardMetaEntity
import com.github.cfogrady.vitalwear.common.card.db.CardMetaEntityDao
import com.github.cfogrady.vitalwear.common.card.db.SpeciesEntityDao
import com.github.cfogrady.vitalwear.composable.util.BitmapScaler
import com.github.cfogrady.vitalwear.composable.util.VitalBoxFactory
import com.github.cfogrady.vitalwear.protos.Character
import com.github.cfogrady.vitalwear.settings.CharacterSettings
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDateTime

class TransferActivity: ComponentActivity(), TransferScreenController {


    private val characterManager: CharacterManager
        get() = (application as VitalWearApp).characterManager
    private val adventureService: AdventureService
        get() = (application as VitalWearApp).adventureService
    private val cardMetaEntityDao: CardMetaEntityDao
        get() = (application as VitalWearApp).cardMetaEntityDao
    private val speciesEntityDao: SpeciesEntityDao
        get() = (application as VitalWearApp).database.speciesEntityDao()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val missingPermissions = NearbyP2PConnection.getMissingPermissions(this)
        if(missingPermissions.isNotEmpty()) {
            buildPermissionRequestLauncher { requestedPermissions->
                val deniedPermissions = mutableListOf<String>()
                for(requestedPermission in requestedPermissions) {
                    if(!requestedPermission.value) {
                        deniedPermissions.add(requestedPermission.key)
                    }
                }
                if(deniedPermissions.isNotEmpty()) {
                    Toast.makeText(this, "Permission Required For Transfers", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }.launch(missingPermissions.toTypedArray())
        }
        setContent {
            TransferScreen(this)
        }
    }

    private fun buildPermissionRequestLauncher(resultBehavior: (Map<String, Boolean>)->Unit): ActivityResultLauncher<Array<String>> {
        val multiplePermissionsContract = ActivityResultContracts.RequestMultiplePermissions()
        val launcher = registerForActivityResult(multiplePermissionsContract, resultBehavior)
        return launcher
    }

    //------------------------------- Controller Members ---------------------------------------//

    override val vitalBoxFactory: VitalBoxFactory
        get() = (application as VitalWearApp).vitalBoxFactory
    override val transferBackground: Bitmap
        get() = (application as VitalWearApp).firmwareManager.getFirmware().value!!.transformationBitmaps.rayOfLightBackground
    override val bitmapScaler: BitmapScaler
        get() = (application as VitalWearApp).bitmapScaler
    override val backgroundHeight: Dp
        get() = (application as VitalWearApp).backgroundHeight

    override fun endActivityWithToast(msg: String) {
        runOnUiThread {
            Toast.makeText(this@TransferActivity, msg, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun getCharacterTransfer(): CharacterTransfer {
        return CharacterTransfer.getInstance(this)
    }

    override suspend fun getActiveCharacterProto(): Character? {
        val activeCharacter = characterManager.getCurrentCharacter()
        activeCharacter?.let {
            val maxAdventureIdxCompletedByCard = adventureService.getMaxAdventureIdxByCardCompletedForCharacter(it.characterStats.id)
            val transformationHistory = characterManager.getTransformationHistory(it.characterStats.id)
            return activeCharacter.toProto(transformationHistory, maxAdventureIdxCompletedByCard)
        }
        return null
    }

    override fun getActiveCharacter(): VBCharacter? {
        return characterManager.getCurrentCharacter()
    }

    override fun deleteActiveCharacter() {
        characterManager.deleteCurrentCharacter()
    }

    val lastReceiedCharacter = MutableStateFlow<TransferScreenController.ReceiveCharacterSprites?>(null)

    override suspend fun receiveCharacter(character: Character): Boolean {
        val sanitizedCharacter = character.sanitizeForImport()
        val matchedCard = sanitizedCharacter.resolveImportedCardMeta(cardMetaEntityDao)
        val validationError = sanitizedCharacter.validateForImport(cardMetaEntityDao, speciesEntityDao, matchedCard)
        if (validationError != null) {
            lastReceiedCharacter.emit(null)
            return false
        }
        val importCharacter = sanitizedCharacter.withCardName(matchedCard!!.cardName)
        val characterId = characterManager.addCharacter(
            importCharacter.cardName,
            importCharacter.characterStats.toCharacterEntity(importCharacter.cardName),
            importCharacter.settings.toCharacterSettings(),
            importCharacter.transformationHistoryList.toTransformationHistoryEntities()
        )
        adventureService.addCharacterAdventures(characterId, importCharacter.maxAdventureCompletedByCardMap)
        val happy = characterManager.getCharacterBitmap(this, importCharacter.cardName, importCharacter.characterStats.slotId, CharacterSpritesIO.WIN)
        val idle = characterManager.getCharacterBitmap(this, importCharacter.cardName, importCharacter.characterStats.slotId, CharacterSpritesIO.IDLE1)
        characterManager.swapToCharacter(this, CharacterManager.SwapCharacterIdentifier.buildAnonymous(importCharacter.cardName, characterId, importCharacter.characterStats.slotId, CharacterState.STORED))
        lastReceiedCharacter.emit(TransferScreenController.ReceiveCharacterSprites(idle, happy))
        return true
    }

    override fun getLastReceivedCharacterSprites(): TransferScreenController.ReceiveCharacterSprites {
        return lastReceiedCharacter.value!!
    }
}

fun VBCharacter.toProto(transformationHistory: List<TransformationHistoryEntity>, maxAdventureCompletedByCard: Map<String, Int>): Character {
    return Character.newBuilder()
        .setCardId(this.cardMeta.cardId)
        .setCardName(this.cardName())
        .setCharacterStats(this.characterStats.toProto())
        .setSettings(this.settings.toProto())
        .addAllTransformationHistory(transformationHistory.toProtoList())
        .putAllMaxAdventureCompletedByCard(maxAdventureCompletedByCard)
        .build()
}

fun List<TransformationHistoryEntity>.toProtoList(): List<Character.TransformationEvent> {
    val transformations = mutableListOf<Character.TransformationEvent>()
    for(transformation in this) {
        transformations.add(transformation.toProto())
    }
    return transformations
}

fun TransformationHistoryEntity.toProto(): Character.TransformationEvent {
    return Character.TransformationEvent.newBuilder()
        .setCardName(this.cardName)
        .setSlotId(this.speciesId)
        .setPhase(this.phase)
        .build()
}

fun List<Character.TransformationEvent>.toTransformationHistoryEntities(): List<TransformationHistoryEntity> {
    val transformations = mutableListOf<TransformationHistoryEntity>()
    for(transformation in this) {
        transformations.add(transformation.toTransformationHistoryEntitiy())
    }
    return transformations
}

fun Character.TransformationEvent.toTransformationHistoryEntitiy(): TransformationHistoryEntity {
    return TransformationHistoryEntity(
        characterId = 0,
        phase = this.phase,
        cardName = this.cardName,
        speciesId = this.slotId
    )
}

fun CharacterEntity.toProto(): Character.CharacterStats {
    val totalBattles = this.totalBattles.coerceAtLeast(0)
    val currentPhaseBattles = this.currentPhaseBattles.coerceAtLeast(0)
    return Character.CharacterStats.newBuilder()
        .setMood(this.mood.coerceAtLeast(0))
        .setVitals(this.vitals.coerceAtLeast(0))
        .setInjured(this.injured)
        .setSlotId(this.slotId)
        .setTrainingTimeRemainingInSeconds(this.trainingTimeRemainingInSeconds.coerceAtLeast(0L))
        .setTotalWins(this.totalWins.coerceIn(0, totalBattles))
        .setAccumulatedDailyInjuries(this.accumulatedDailyInjuries.coerceAtLeast(0))
        .setCurrentPhaseBattles(currentPhaseBattles)
        .setCurrentPhaseWins(this.currentPhaseWins.coerceIn(0, currentPhaseBattles))
        .setTimeUntilNextTransformation(this.timeUntilNextTransformation.coerceAtLeast(0L))
        .setTotalBattles(totalBattles)
        .setTrainedAp(this.trainedAp.coerceAtLeast(0))
        .setTrainedBp(this.trainedBp.coerceAtLeast(0))
        .setTrainedHp(this.trainedHp.coerceAtLeast(0))
        .setTrainedPp(this.trainedPP.coerceAtLeast(0))
        .build()
}

fun CharacterSettings.toProto(): Character.Settings {
    var builder = Character.Settings.newBuilder()
        .setTrainingInBackground(this.trainInBackground)
        .setAllowedBattlesValue(this.allowedBattles.ordinal)
    if(this.assumedFranchise != null) {
        builder = builder.setAssumedFranchise(this.assumedFranchise)
    }
    return builder.build()
}

fun Character.CharacterStats.toCharacterEntity(cardName: String): CharacterEntity {
    val totalBattles = this.totalBattles.coerceAtLeast(0)
    val currentPhaseBattles = this.currentPhaseBattles.coerceAtLeast(0)
    return CharacterEntity(
        id = 0,
        state = CharacterState.STORED,
        cardFile = cardName,
        slotId = this.slotId,
        lastUpdate = LocalDateTime.now(),
        vitals = this.vitals.coerceAtLeast(0),
        trainingTimeRemainingInSeconds = this.trainingTimeRemainingInSeconds.coerceAtLeast(0L),
        hasTransformations = this.timeUntilNextTransformation > 0,
        timeUntilNextTransformation = this.timeUntilNextTransformation.coerceAtLeast(0L),
        trainedBp = this.trainedBp.coerceAtLeast(0),
        trainedHp = this.trainedHp.coerceAtLeast(0),
        trainedAp = this.trainedAp.coerceAtLeast(0),
        trainedPP = this.trainedPp.coerceAtLeast(0),
        injured = this.injured,
        lostBattlesInjured = 0,
        accumulatedDailyInjuries = this.accumulatedDailyInjuries.coerceAtLeast(0),
        totalBattles = totalBattles,
        currentPhaseBattles = currentPhaseBattles,
        totalWins = this.totalWins.coerceIn(0, totalBattles),
        currentPhaseWins = this.currentPhaseWins.coerceIn(0, currentPhaseBattles),
        mood = this.mood.coerceAtLeast(0),
        sleeping = false,
        dead = false,
    )
}

fun Character.Settings.toCharacterSettings(): CharacterSettings {
    val allowedBattles = CharacterSettings.AllowedBattles.entries.getOrElse(this.allowedBattlesValue) {
        CharacterSettings.AllowedBattles.CARD_ONLY
    }
    return CharacterSettings(
        characterId = 0,
        trainInBackground = this.trainingInBackground,
        allowedBattles = allowedBattles,
        if(this.hasAssumedFranchise()) this.assumedFranchise else null
    )
}

fun Character.validateForImport(
    cardMetaEntityDao: CardMetaEntityDao,
    speciesEntityDao: SpeciesEntityDao,
    matchedCard: CardMetaEntity? = this.resolveImportedCardMeta(cardMetaEntityDao)
): String? {
    if (this.cardName.isBlank()) {
        if (this.cardId <= 0) {
            return "Missing card name"
        }
    }

    if (this.characterStats.slotId < 0) {
        return "Invalid slot id ${this.characterStats.slotId}"
    }

    if (matchedCard == null) {
        return if (this.cardId > 0) {
            "Card id ${this.cardId} is not imported on this watch"
        } else {
            "Card ${this.cardName} is not imported on this watch"
        }
    }

    if (this.cardId > 0 && matchedCard.cardId != this.cardId) {
        return "Card id mismatch for ${this.cardName}"
    }

    val speciesExists = runCatching {
        speciesEntityDao.getCharacterByCardAndCharacterId(matchedCard.cardName, this.characterStats.slotId)
    }.isSuccess
    if (!speciesExists) {
        return "Slot ${this.characterStats.slotId} does not exist on ${matchedCard.cardName}"
    }

    return null
}

fun Character.resolveImportedCardMeta(cardMetaEntityDao: CardMetaEntityDao): CardMetaEntity? {
    val byName = this.cardName.takeIf { it.isNotBlank() }?.let { cardMetaEntityDao.getByName(it) }
    if (byName != null && (this.cardId <= 0 || byName.cardId == this.cardId)) {
        return byName
    }
    if (this.cardId <= 0) {
        return null
    }
    val matchesById = cardMetaEntityDao.getAll().filter { it.cardId == this.cardId }
    return matchesById.singleOrNull()
}

fun Character.withCardName(cardName: String): Character {
    if (this.cardName == cardName) {
        return this
    }
    return Character.newBuilder(this)
        .setCardName(cardName)
        .build()
}

fun Character.sanitizeForImport(): Character {
    val totalBattles = this.characterStats.totalBattles.coerceAtLeast(0)
    val currentPhaseBattles = this.characterStats.currentPhaseBattles.coerceAtLeast(0)
    val settingsBuilder = Character.Settings.newBuilder()
        .setTrainingInBackground(this.settings.trainingInBackground)
        .setAllowedBattlesValue(
            this.settings.allowedBattlesValue.takeIf {
                it in CharacterSettings.AllowedBattles.entries.indices
            } ?: CharacterSettings.AllowedBattles.CARD_ONLY.ordinal
        )
    if (this.settings.hasAssumedFranchise()) {
        settingsBuilder.setAssumedFranchise(this.settings.assumedFranchise)
    }

    return Character.newBuilder()
        .setCardName(this.cardName)
        .setCardId(this.cardId)
        .setCharacterStats(
            Character.CharacterStats.newBuilder()
                .setSlotId(this.characterStats.slotId.coerceAtLeast(0))
                .setVitals(this.characterStats.vitals.coerceAtLeast(0))
                .setTrainingTimeRemainingInSeconds(this.characterStats.trainingTimeRemainingInSeconds.coerceAtLeast(0L))
                .setTimeUntilNextTransformation(this.characterStats.timeUntilNextTransformation.coerceAtLeast(0L))
                .setTrainedBp(this.characterStats.trainedBp.coerceAtLeast(0))
                .setTrainedHp(this.characterStats.trainedHp.coerceAtLeast(0))
                .setTrainedAp(this.characterStats.trainedAp.coerceAtLeast(0))
                .setTrainedPp(this.characterStats.trainedPp.coerceAtLeast(0))
                .setInjured(this.characterStats.injured)
                .setAccumulatedDailyInjuries(this.characterStats.accumulatedDailyInjuries.coerceAtLeast(0))
                .setTotalBattles(totalBattles)
                .setCurrentPhaseBattles(currentPhaseBattles)
                .setTotalWins(this.characterStats.totalWins.coerceIn(0, totalBattles))
                .setCurrentPhaseWins(this.characterStats.currentPhaseWins.coerceIn(0, currentPhaseBattles))
                .setMood(this.characterStats.mood.coerceAtLeast(0))
                .build()
        )
        .setSettings(settingsBuilder.build())
        .addAllTransformationHistory(
            this.transformationHistoryList.filter {
                it.cardName.isNotBlank() && it.slotId >= 0
            }
        )
        .putAllMaxAdventureCompletedByCard(
            this.maxAdventureCompletedByCardMap.filterKeys { it.isNotBlank() }
                .mapValues { (_, value) -> value.coerceAtLeast(0) }
        )
        .build()
}
