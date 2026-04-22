package com.github.cfogrady.vitalwear.transfer.hce

import com.github.cfogrady.vitalwear.VitalWearApp
import com.github.cfogrady.vitalwear.character.CharacterManager
import com.github.cfogrady.vitalwear.character.data.CharacterState
import com.github.cfogrady.vitalwear.protos.Character
import com.github.cfogrady.vitalwear.transfer.resolveImportedCardMeta
import com.github.cfogrady.vitalwear.transfer.sanitizeForImport
import com.github.cfogrady.vitalwear.transfer.toCharacterEntity
import com.github.cfogrady.vitalwear.transfer.toCharacterSettings
import com.github.cfogrady.vitalwear.transfer.toTransformationHistoryEntities
import com.github.cfogrady.vitalwear.transfer.validateForImport
import com.github.cfogrady.vitalwear.transfer.remapImportedRootCardName

class VitalWearHceTransferRepository(
    private val app: VitalWearApp,
) {
    fun deleteCurrentCharacterAfterSuccessfulSend() {
        app.characterManager.deleteCurrentCharacter()
    }

    suspend fun importCharacter(payload: ByteArray): Boolean {
        val incoming = Character.parseFrom(payload)
        val sanitized = incoming.sanitizeForImport()
        val speciesDao = app.database.speciesEntityDao()
        val matchedCard = sanitized.resolveImportedCardMeta(app.cardMetaEntityDao, speciesDao)
        val validationError = sanitized.validateForImport(app.cardMetaEntityDao, speciesDao, matchedCard)
        if (validationError != null) {
            return false
        }

        val importCharacter = sanitized.remapImportedRootCardName(matchedCard!!.cardName)
        val characterId = app.characterManager.addCharacter(
            importCharacter.cardName,
            importCharacter.characterStats.toCharacterEntity(importCharacter.cardName),
            importCharacter.settings.toCharacterSettings(),
            importCharacter.transformationHistoryList.toTransformationHistoryEntities()
        )
        app.adventureService.addCharacterAdventures(characterId, importCharacter.maxAdventureCompletedByCardMap)
        app.characterManager.swapToCharacter(
            app.applicationContext,
            CharacterManager.SwapCharacterIdentifier.buildAnonymous(
                importCharacter.cardName,
                characterId,
                importCharacter.characterStats.slotId,
                CharacterState.STORED,
            )
        )

        // Keep COMMIT fast on HCE: heavy sprite file checks can outlive NFC field and cause TagLost.
        return true
    }
}
