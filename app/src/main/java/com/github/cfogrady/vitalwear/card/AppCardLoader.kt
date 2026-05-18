package com.github.cfogrady.vitalwear.card

import android.content.Context
import com.github.cfogrady.vitalwear.common.card.CardLoader
import com.github.cfogrady.vitalwear.common.card.db.SpeciesEntityDao
import com.github.cfogrady.vitalwear.settings.CardSettingsDao
import com.github.cfogrady.vitalwear.settings.CardSettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class AppCardLoader(
    private val cardLoader: CardLoader,
    private val cardSettingsDao: CardSettingsDao,
    private val speciesEntityDao: SpeciesEntityDao,
    private val dimToBemStatConversion: DimToBemStatConversion
) {
    suspend fun importCard(context: Context, cardName: String, cardStream: InputStream, uniqueSprites: Boolean, convertToBem: Boolean = false) {
        cardLoader.importCardImage(context, cardName, cardStream, uniqueSprites, convertToBem)
        cardSettingsDao.upsert(CardSettingsEntity.default(cardName))

        // Apply DIM-to-BEM conversion if requested
        if (convertToBem) {
            withContext(Dispatchers.IO) {
                val allSpecies = speciesEntityDao.getCharacterByCard(cardName)
                for (species in allSpecies) {
                    val convertedSpecies = dimToBemStatConversion.convertSpeciesEntity(species)
                    speciesEntityDao.upsert(convertedSpecies)
                }
            }
        }
    }
}