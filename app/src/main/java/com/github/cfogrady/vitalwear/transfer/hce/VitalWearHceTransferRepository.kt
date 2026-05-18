package com.github.cfogrady.vitalwear.transfer.hce

import android.util.Log
import com.github.cfogrady.vitalwear.VitalWearApp
import com.github.cfogrady.vitalwear.character.CharacterManager
import com.github.cfogrady.vitalwear.character.data.CharacterState
import com.github.cfogrady.vitalwear.protos.Character
import com.github.cfogrady.vitalwear.transfer.resolveImportedCardMetaOrPlaceholder
import com.github.cfogrady.vitalwear.transfer.recordImportedCharacterSeen
import com.github.cfogrady.vitalwear.transfer.sanitizeForImport
import com.github.cfogrady.vitalwear.transfer.toCharacterEntity
import com.github.cfogrady.vitalwear.transfer.toCharacterSettings
import com.github.cfogrady.vitalwear.transfer.toTransformationHistoryEntities
import com.github.cfogrady.vitalwear.transfer.validateForImport
import com.github.cfogrady.vitalwear.transfer.remapImportedRootCardName
import com.github.cfogrady.vitalwear.transfer.toProto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

class VitalWearHceTransferRepository(
    private val app: VitalWearApp,
) {
    companion object {
        private const val TAG = "VW_HCE_IMPORT"
    }

    private val seenSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun deleteCurrentCharacterAfterSuccessfulSend() {
        app.characterManager.deleteCurrentCharacter()
    }

    /**
     * Builds and returns the serialised protobuf payload for the currently active character,
     * or null if there is no active character.
     *
     * Called synchronously from [VitalWearHostApduService.processCommandApdu] (runs on the
     * HCE thread) via [runBlocking].  All DB work is pinned to [Dispatchers.IO] so the main
     * thread is never blocked through coroutine machinery itself — and the NFC 10-second
     * transaction timeout is more than sufficient for these fast queries.
     */
    fun getActiveCharacterPayload(): ByteArray? = runBlocking {
        withContext(Dispatchers.IO) {
            val character = app.characterManager.getCurrentCharacter()
                ?: return@withContext null
            val transformationHistory = app.characterManager.getTransformationHistory(character.characterStats.id)
            val maxAdventureByCard = app.adventureService
                .getMaxAdventureIdxByCardCompletedForCharacter(character.characterStats.id)
            character.toProto(
                context = app.applicationContext,
                transformationHistory = transformationHistory,
                maxAdventureCompletedByCard = maxAdventureByCard,
                currentExerciseLevel = app.heartRateService.currentExerciseLevel.value,
                heartRateCurrent = app.heartRateService.lastHeartRate.value,
            ).toByteArray()
        }
    }

    suspend fun importCharacter(payload: ByteArray): Boolean {
        val incoming = Character.parseFrom(payload)
        val sanitized = incoming.sanitizeForImport()
        val speciesDao = app.database.speciesEntityDao()
        val matchedCard = sanitized.resolveImportedCardMetaOrPlaceholder(app.applicationContext, app.cardMetaEntityDao, speciesDao)
        val resolvedCardName = matchedCard?.cardName
            ?: sanitized.transferCard.cardName.takeIf { sanitized.hasTransferCard() && it.isNotBlank() }
            ?: sanitized.cardName.takeIf { it.isNotBlank() }
            ?: "Transferred Card"
        val validationError = sanitized.validateForImport(app.cardMetaEntityDao, speciesDao, matchedCard)
        if (validationError != null) {
            // VBH -> watch HCE can provide transfer metadata even when strict local card checks fail.
            // In that case continue with best-effort import instead of rejecting the whole transfer.
            if (!(sanitized.hasTransferCard() && sanitized.hasTransferSpecies())) {
                Log.w(TAG, "Import validation failed: $validationError")
                return false
            }
            Log.w(TAG, "Import validation mismatch tolerated due embedded transfer metadata: $validationError")
        }

        val importCharacter = sanitized.remapImportedRootCardName(resolvedCardName)
        Log.i(TAG, "Importing character card=${importCharacter.cardName} slot=${importCharacter.characterStats.slotId}")
        val characterId = app.characterManager.addCharacter(
            importCharacter.cardName,
            importCharacter.characterStats.toCharacterEntity(importCharacter.cardName),
            importCharacter.settings.toCharacterSettings(),
            importCharacter.transformationHistoryList.toTransformationHistoryEntities()
        )
        app.adventureService.addCharacterAdventures(characterId, importCharacter.maxAdventureCompletedByCardMap)
        val activationSucceeded = runCatching {
            app.characterManager.swapToCharacter(
                app.applicationContext,
                CharacterManager.SwapCharacterIdentifier.buildAnonymous(
                    importCharacter.cardName,
                    characterId,
                    importCharacter.characterStats.slotId,
                    CharacterState.STORED,
                )
            )
            app.characterManager.getCurrentCharacter()?.characterStats?.id == characterId
        }.onFailure {
            Log.w(TAG, "Character imported but activation failed", it)
        }.getOrDefault(false)

        if (!activationSucceeded) {
            Log.w(TAG, "Character import persisted but activation verification failed")
            return false
        }

        seenSyncScope.launch {
            runCatching {
                app.sharedTransferSeenDao.recordImportedCharacterSeen(importCharacter, System.currentTimeMillis())
            }.onFailure {
                // Watch import should remain successful even if cross-process transfer-seen sync is unavailable.
                Log.w(TAG, "Transfer-seen sync unavailable; continuing import", it)
            }
        }

        // Keep COMMIT fast on HCE: heavy sprite file checks can outlive NFC field and cause TagLost.
        return true
    }
}
