package com.github.cfogrady.vitalwear.transfer

import android.content.Context
import com.github.cfogrady.vitalwear.adventure.AdventureService
import com.github.cfogrady.vitalwear.character.CharacterManager
import com.github.cfogrady.vitalwear.character.data.CharacterState
import com.github.cfogrady.vitalwear.common.card.db.CardMetaEntityDao
import com.github.cfogrady.vitalwear.common.card.db.SpeciesEntityDao
import com.github.cfogrady.vitalwear.protos.Character
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber

class CharacterReceiver(
    private val characterManager: CharacterManager,
    private val adventureService: AdventureService,
    private val cardMetaEntityDao: CardMetaEntityDao,
    private val speciesEntityDao: SpeciesEntityDao,
) {
    data class ImportCharacterResult(val success: Boolean, val cardName: String?)

    suspend fun importCharacterFromChannel(context: Context, channel: ChannelClient.Channel): ImportCharacterResult {
        val channelClient = Wearable.getChannelClient(context)
        var importedCardName: String? = null
        var success = false

        withContext(Dispatchers.IO) {
            channelClient.getInputStream(channel).await().use { input ->
                try {
                    val character = Character.parseFrom(input).sanitizeForImport()
                    val matchedCard = character.resolveImportedCardMeta(cardMetaEntityDao)
                    importedCardName = matchedCard?.cardName ?: character.cardName
                    val validationError = character.validateForImport(cardMetaEntityDao, speciesEntityDao, matchedCard)
                    if (validationError != null) {
                        Timber.i("Rejecting character import: $validationError")
                    } else {
                        val importCharacter = character.withCardName(matchedCard!!.cardName)
                        val characterId = characterManager.addCharacter(
                            importCharacter.cardName,
                            importCharacter.characterStats.toCharacterEntity(importCharacter.cardName),
                            importCharacter.settings.toCharacterSettings(),
                            importCharacter.transformationHistoryList.toTransformationHistoryEntities()
                        )
                        adventureService.addCharacterAdventures(characterId, importCharacter.maxAdventureCompletedByCardMap)
                        characterManager.swapToCharacter(
                            context,
                            CharacterManager.SwapCharacterIdentifier.buildAnonymous(
                                importCharacter.cardName,
                                characterId,
                                importCharacter.characterStats.slotId,
                                CharacterState.STORED
                            )
                        )
                        success = true
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Unable to load received character data")
                }
            }
            channelClient.close(channel).await()
        }

        return ImportCharacterResult(success, importedCardName)
    }
}


