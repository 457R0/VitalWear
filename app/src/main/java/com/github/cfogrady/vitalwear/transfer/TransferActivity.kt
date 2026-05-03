package com.github.cfogrady.vitalwear.transfer

import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.ui.unit.Dp
import com.github.cfogrady.vitalwear.VitalWearApp
import com.github.cfogrady.vitalwear.adventure.AdventureService
import com.github.cfogrady.vitalwear.character.CharacterManager
import com.github.cfogrady.vitalwear.character.VBCharacter
import com.github.cfogrady.vitalwear.character.data.CharacterEntity
import com.github.cfogrady.vitalwear.character.data.CharacterState
import com.github.cfogrady.vitalwear.character.transformation.history.TransformationHistoryEntity
import com.github.cfogrady.vitalwear.common.card.CharacterSpritesIO
import com.github.cfogrady.vitalwear.common.card.CardType
import com.github.cfogrady.vitalwear.common.card.db.CardMetaEntity
import com.github.cfogrady.vitalwear.common.card.db.CardMetaEntityDao
import com.github.cfogrady.vitalwear.common.card.db.SpeciesEntityDao
import com.github.cfogrady.vitalwear.composable.util.BitmapScaler
import com.github.cfogrady.vitalwear.composable.util.VitalBoxFactory
import com.github.cfogrady.vitalwear.protos.Character
import com.github.cfogrady.vitalwear.settings.CharacterSettings
import com.github.cfogrady.vitalwear.transfer.hce.VitalWearHceSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDateTime
import com.github.cfogrady.vitalwear.common.character.CharacterSprites

class TransferActivity: ComponentActivity(), TransferScreenController {


    private val characterManager: CharacterManager
        get() = (application as VitalWearApp).characterManager
    private val adventureService: AdventureService
        get() = (application as VitalWearApp).adventureService
    private val cardMetaEntityDao: CardMetaEntityDao
        get() = (application as VitalWearApp).cardMetaEntityDao
    private val speciesEntityDao: SpeciesEntityDao
        get() = (application as VitalWearApp).database.speciesEntityDao()
    private val transferSeenDao
        get() = (application as VitalWearApp).sharedTransferSeenDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            TransferScreen(this)
        }
    }

    override fun onDestroy() {
        VitalWearHceSessionManager.clear()
        super.onDestroy()
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


    override suspend fun getActiveCharacterProto(): Character? {
        val activeCharacter = characterManager.getCurrentCharacter()
        activeCharacter?.let {
            val maxAdventureIdxCompletedByCard = adventureService.getMaxAdventureIdxByCardCompletedForCharacter(it.characterStats.id)
            val transformationHistory = characterManager.getTransformationHistory(it.characterStats.id)
            val app = application as VitalWearApp
            return activeCharacter.toProto(
                transformationHistory = transformationHistory,
                maxAdventureCompletedByCard = maxAdventureIdxCompletedByCard,
                currentExerciseLevel = app.heartRateService.currentExerciseLevel.value,
                heartRateCurrent = app.heartRateService.lastHeartRate.value,
            )
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
        val matchedCard = sanitizedCharacter.resolveImportedCardMeta(cardMetaEntityDao, speciesEntityDao)
        val validationError = sanitizedCharacter.validateForImport(cardMetaEntityDao, speciesEntityDao, matchedCard)
        if (validationError != null) {
            lastReceiedCharacter.emit(null)
            return false
        }
        val importCharacter = sanitizedCharacter.remapImportedRootCardName(matchedCard!!.cardName)
        val seenTimestamp = System.currentTimeMillis()
        transferSeenDao.recordImportedCharacterSeen(importCharacter, seenTimestamp)
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
        lastReceiedCharacter.value?.let {
            return it
        }
        val activeCharacter = characterManager.getCurrentCharacter()
            ?: throw IllegalStateException("No received character sprites are available")
        return TransferScreenController.ReceiveCharacterSprites(
            idle = activeCharacter.characterSprites.sprites[CharacterSprites.IDLE_1],
            happy = activeCharacter.characterSprites.sprites[CharacterSprites.WIN],
        )
    }
}

fun VBCharacter.toProto(
    transformationHistory: List<TransformationHistoryEntity>,
    maxAdventureCompletedByCard: Map<String, Int>,
    currentExerciseLevel: Int = 0,
    heartRateCurrent: Int = 0,
): Character {
    val generation = (transformationHistory.size - 1).coerceAtLeast(0)
    val totalTrophies = this.characterStats.trainedPP.coerceAtLeast(0)
    val nextAdventureMissionStage = (maxAdventureCompletedByCard[this.cardName()]?.plus(1) ?: 0).coerceAtLeast(0)

    return Character.newBuilder()
        .setCardId(this.cardMeta.cardId)
        .setCardName(this.cardName())
        .setCharacterStats(
            this.characterStats.toProto(
                deviceType = this.toTransferDeviceType(),
                currentHeartRateCurrent = heartRateCurrent,
                generationOverride = generation,
                totalTrophiesOverride = totalTrophies,
                nextAdventureMissionStageOverride = nextAdventureMissionStage,
            )
        )
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

fun CharacterEntity.toProto(
    deviceType: Character.CharacterStats.TransferDeviceType = Character.CharacterStats.TransferDeviceType.TRANSFER_DEVICE_TYPE_UNSPECIFIED,
    currentHeartRateCurrent: Int = 0,
    generationOverride: Int? = null,
    totalTrophiesOverride: Int? = null,
    nextAdventureMissionStageOverride: Int? = null,
): Character.CharacterStats {
    val totalBattles = this.totalBattles.coerceAtLeast(0)
    val currentPhaseBattles = this.currentPhaseBattles.coerceAtLeast(0)
    // Use currentHeartRateCurrent for real-time HR, fallback to stored value
    val heartRateToUse = if (currentHeartRateCurrent > 0) currentHeartRateCurrent else this.heartRateCurrent

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
        .setDeviceType(deviceType)
        .setAgeInDays((generationOverride ?: this.ageInDays).coerceAtLeast(0))
        .setActivityLevel(this.activityLevel.coerceAtLeast(0))
        .setHeartRateCurrent(heartRateToUse.coerceAtLeast(0))
        .setGeneration((generationOverride ?: this.generation).coerceAtLeast(0))
        .setTotalTrophies((totalTrophiesOverride ?: this.totalTrophies).coerceAtLeast(0))
        .setNextAdventureMissionStage((nextAdventureMissionStageOverride ?: this.nextAdventureMissionStage).coerceAtLeast(0))
        .setItemEffectMentalStateValue(this.itemEffectMentalStateValue.coerceAtLeast(0))
        .setItemEffectMentalStateMinutesRemaining(this.itemEffectMentalStateMinutesRemaining.coerceAtLeast(0))
        .setItemEffectActivityLevelValue(this.itemEffectActivityLevelValue.coerceAtLeast(0))
        .setItemEffectActivityLevelMinutesRemaining(this.itemEffectActivityLevelMinutesRemaining.coerceAtLeast(0))
        .setItemEffectVitalPointsChangeValue(this.itemEffectVitalPointsChangeValue.coerceAtLeast(0))
        .setItemEffectVitalPointsChangeMinutesRemaining(this.itemEffectVitalPointsChangeMinutesRemaining.coerceAtLeast(0))
        .setAbilityRarity(this.abilityRarity.coerceAtLeast(0))
        .setAbilityType(this.abilityType.coerceAtLeast(0))
        .setAbilityBranch(this.abilityBranch.coerceAtLeast(0))
        .setAbilityReset(this.abilityReset.coerceAtLeast(0))
        .setRank(this.rank.coerceAtLeast(0))
        .setItemType(this.itemType.coerceAtLeast(0))
        .setItemMultiplier(this.itemMultiplier.coerceAtLeast(0))
        .setItemRemainingTime(this.itemRemainingTime.coerceAtLeast(0))
        .setFirmwareMinorVersion(this.firmwareMinorVersion.coerceAtLeast(0))
        .setFirmwareMajorVersion(this.firmwareMajorVersion.coerceAtLeast(0))
        .build()
}

private fun VBCharacter.toTransferDeviceType(): Character.CharacterStats.TransferDeviceType {
    return if (cardMeta.cardType == CardType.BEM || settings.assumedFranchise != null || characterStats.trainedHp > 0 || characterStats.trainedAp > 0 || characterStats.trainedBp > 0) {
        Character.CharacterStats.TransferDeviceType.TRANSFER_DEVICE_TYPE_BE
    } else {
        Character.CharacterStats.TransferDeviceType.TRANSFER_DEVICE_TYPE_VB
    }
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
        // Cross-device stats preservation
        ageInDays = this.ageInDays.coerceAtLeast(0),
        activityLevel = this.activityLevel.coerceAtLeast(0),
        heartRateCurrent = this.heartRateCurrent.coerceAtLeast(0),
        generation = this.generation.coerceAtLeast(0),
        totalTrophies = this.totalTrophies.coerceAtLeast(0),
        nextAdventureMissionStage = this.nextAdventureMissionStage.coerceAtLeast(0),
        abilityType = this.abilityType.coerceAtLeast(0),
        abilityBranch = this.abilityBranch.coerceAtLeast(0),
        abilityRarity = this.abilityRarity.coerceAtLeast(0),
        abilityReset = this.abilityReset.coerceAtLeast(0),
        rank = this.rank.coerceAtLeast(0),
        itemEffectMentalStateValue = this.itemEffectMentalStateValue.coerceAtLeast(0),
        itemEffectMentalStateMinutesRemaining = this.itemEffectMentalStateMinutesRemaining.coerceAtLeast(0),
        itemEffectActivityLevelValue = this.itemEffectActivityLevelValue.coerceAtLeast(0),
        itemEffectActivityLevelMinutesRemaining = this.itemEffectActivityLevelMinutesRemaining.coerceAtLeast(0),
        itemEffectVitalPointsChangeValue = this.itemEffectVitalPointsChangeValue.coerceAtLeast(0),
        itemEffectVitalPointsChangeMinutesRemaining = this.itemEffectVitalPointsChangeMinutesRemaining.coerceAtLeast(0),
        itemType = this.itemType.coerceAtLeast(0),
        itemMultiplier = this.itemMultiplier.coerceAtLeast(0),
        itemRemainingTime = this.itemRemainingTime.coerceAtLeast(0),
        firmwareMinorVersion = this.firmwareMinorVersion.coerceAtLeast(0),
        firmwareMajorVersion = this.firmwareMajorVersion.coerceAtLeast(0),
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
    matchedCard: CardMetaEntity? = this.resolveImportedCardMeta(cardMetaEntityDao, speciesEntityDao)
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
        true
    }.getOrDefault(false)
    if (!speciesExists) {
        return "Slot ${this.characterStats.slotId} does not exist on ${matchedCard.cardName}"
    }

    return null
}

fun Character.resolveImportedCardMeta(
    cardMetaEntityDao: CardMetaEntityDao,
    speciesEntityDao: SpeciesEntityDao? = null
): CardMetaEntity? {
    val allCards = cardMetaEntityDao.getAll()
    return selectImportedCardMeta(
        candidates = allCards,
        incomingCardName = this.cardName,
        incomingCardId = this.cardId,
        slotId = this.characterStats.slotId,
        hasSlot = { candidate, slotId ->
            speciesEntityDao?.let {
                runCatching {
                    it.getCharacterByCardAndCharacterId(candidate.cardName, slotId)
                    true
                }.getOrDefault(false)
            } ?: false
        }
    )
}

internal fun selectImportedCardMeta(
    candidates: List<CardMetaEntity>,
    incomingCardName: String,
    incomingCardId: Int,
    slotId: Int,
    hasSlot: (CardMetaEntity, Int) -> Boolean,
): CardMetaEntity? {
    val requestedName = incomingCardName.takeIf { it.isNotBlank() }
    val idMatches = if (incomingCardId > 0) {
        candidates.filter { it.cardId == incomingCardId }
    } else {
        emptyList()
    }

    requestedName?.let { requestedNameValue ->
        candidates.firstOrNull {
            it.cardName == requestedNameValue && (incomingCardId <= 0 || it.cardId == incomingCardId)
        }?.let { return it }

        val exactIgnoreCaseMatches = candidates.filter {
            it.cardName.equals(requestedNameValue, ignoreCase = true) && (incomingCardId <= 0 || it.cardId == incomingCardId)
        }
        if (exactIgnoreCaseMatches.size == 1) {
            return exactIgnoreCaseMatches.single()
        }
    }

    if (idMatches.size == 1) {
        return idMatches.single()
    }

    val slotFilteredIdMatches = idMatches.filter { slotId >= 0 && hasSlot(it, slotId) }
    if (slotFilteredIdMatches.size == 1) {
        return slotFilteredIdMatches.single()
    }

    requestedName?.let { requestedNameValue ->
        val normalizedNameMatches = candidates.filter {
            cardNamesMatch(it.cardName, requestedNameValue) && (incomingCardId <= 0 || it.cardId == incomingCardId)
        }
        if (normalizedNameMatches.size == 1) {
            return normalizedNameMatches.single()
        }
        val slotFilteredNameMatches = normalizedNameMatches.filter { slotId >= 0 && hasSlot(it, slotId) }
        if (slotFilteredNameMatches.size == 1) {
            return slotFilteredNameMatches.single()
        }
    }

    requestedName?.let { requestedNameValue ->
        val normalizedIdMatches = idMatches.filter { cardNamesMatch(it.cardName, requestedNameValue) }
        if (normalizedIdMatches.size == 1) {
            return normalizedIdMatches.single()
        }
    }

    return null
}

private fun cardNamesMatch(left: String, right: String): Boolean {
    return left.equals(right, ignoreCase = true) || left.toNormalizedCardLookupKey() == right.toNormalizedCardLookupKey()
}

private fun String.toNormalizedCardLookupKey(): String {
    return lowercase().filter { it.isLetterOrDigit() }
}

fun Character.remapImportedRootCardName(cardName: String): Character {
    val originalRootCardName = this.cardName
    val remappedTransformationHistory = this.transformationHistoryList.map { transformation ->
        if (transformation.cardName.shouldRemapToRootCard(originalRootCardName)) {
            Character.TransformationEvent.newBuilder(transformation)
                .setCardName(cardName)
                .build()
        } else {
            transformation
        }
    }
    val remappedAdventureProgress = linkedMapOf<String, Int>()
    for ((adventureCardName, progress) in this.maxAdventureCompletedByCardMap) {
        val remappedCardName = if (adventureCardName.shouldRemapToRootCard(originalRootCardName)) {
            cardName
        } else {
            adventureCardName
        }
        val previousProgress = remappedAdventureProgress[remappedCardName]
        remappedAdventureProgress[remappedCardName] = previousProgress?.coerceAtLeast(progress) ?: progress
    }

    if (this.cardName == cardName &&
        remappedTransformationHistory == this.transformationHistoryList &&
        remappedAdventureProgress == this.maxAdventureCompletedByCardMap
    ) {
        return this
    }

    return Character.newBuilder(this)
        .setCardName(cardName)
        .clearTransformationHistory()
        .addAllTransformationHistory(remappedTransformationHistory)
        .clearMaxAdventureCompletedByCard()
        .putAllMaxAdventureCompletedByCard(remappedAdventureProgress)
        .build()
}

fun Character.withCardName(cardName: String): Character {
    return remapImportedRootCardName(cardName)
}

private fun String.shouldRemapToRootCard(originalRootCardName: String): Boolean {
    if (this.isBlank() || originalRootCardName.isBlank()) {
        return false
    }
    return cardNamesMatch(this, originalRootCardName)
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
                .setDeviceType(this.characterStats.deviceType)
                .setAgeInDays(this.characterStats.ageInDays.coerceAtLeast(0))
                .setActivityLevel(this.characterStats.activityLevel.coerceAtLeast(0))
                .setHeartRateCurrent(this.characterStats.heartRateCurrent.coerceAtLeast(0))
                .setGeneration(this.characterStats.generation.coerceAtLeast(0))
                .setTotalTrophies(this.characterStats.totalTrophies.coerceAtLeast(0))
                .setNextAdventureMissionStage(this.characterStats.nextAdventureMissionStage.coerceAtLeast(0))
                .setItemEffectMentalStateValue(this.characterStats.itemEffectMentalStateValue.coerceAtLeast(0))
                .setItemEffectMentalStateMinutesRemaining(this.characterStats.itemEffectMentalStateMinutesRemaining.coerceAtLeast(0))
                .setItemEffectActivityLevelValue(this.characterStats.itemEffectActivityLevelValue.coerceAtLeast(0))
                .setItemEffectActivityLevelMinutesRemaining(this.characterStats.itemEffectActivityLevelMinutesRemaining.coerceAtLeast(0))
                .setItemEffectVitalPointsChangeValue(this.characterStats.itemEffectVitalPointsChangeValue.coerceAtLeast(0))
                .setItemEffectVitalPointsChangeMinutesRemaining(this.characterStats.itemEffectVitalPointsChangeMinutesRemaining.coerceAtLeast(0))
                .setAbilityRarity(this.characterStats.abilityRarity.coerceAtLeast(0))
                .setAbilityType(this.characterStats.abilityType.coerceAtLeast(0))
                .setAbilityBranch(this.characterStats.abilityBranch.coerceAtLeast(0))
                .setAbilityReset(this.characterStats.abilityReset.coerceAtLeast(0))
                .setRank(this.characterStats.rank.coerceAtLeast(0))
                .setItemType(this.characterStats.itemType.coerceAtLeast(0))
                .setItemMultiplier(this.characterStats.itemMultiplier.coerceAtLeast(0))
                .setItemRemainingTime(this.characterStats.itemRemainingTime.coerceAtLeast(0))
                .setFirmwareMinorVersion(this.characterStats.firmwareMinorVersion.coerceAtLeast(0))
                .setFirmwareMajorVersion(this.characterStats.firmwareMajorVersion.coerceAtLeast(0))
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