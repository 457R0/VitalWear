package com.github.cfogrady.vitalwear.transfer

import com.github.cfogrady.vitalwear.common.card.CardType
import com.github.cfogrady.vitalwear.common.card.db.CardMetaEntity
import com.github.cfogrady.vitalwear.protos.Character
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TransferImportResolutionTest {
    @Test
    fun selectImportedCardMeta_prefersMatchingSlotWhenCardIdMatchesMultipleRenamedCards() {
        val selected = selectImportedCardMeta(
            candidates = listOf(
                CardMetaEntity("Vital Series", 77, 0, CardType.BEM, 1, null),
                CardMetaEntity("My Custom Vital Series", 77, 0, CardType.BEM, 1, null),
                CardMetaEntity("Other Card", 88, 0, CardType.BEM, 1, null),
            ),
            incomingCardName = "Vital-Series",
            incomingCardId = 77,
            slotId = 12,
            hasSlot = { candidate, slotId ->
                candidate.cardName == "My Custom Vital Series" && slotId == 12
            }
        )

        assertNotNull(selected)
        assertEquals("My Custom Vital Series", selected?.cardName)
    }

    @Test
    fun selectImportedCardMeta_matchesNormalizedNameWhenCardIdIsMissing() {
        val selected = selectImportedCardMeta(
            candidates = listOf(
                CardMetaEntity("Impulse City!", 0, 0, CardType.DIM, 0, null),
            ),
            incomingCardName = "impulse-city",
            incomingCardId = 0,
            slotId = 0,
            hasSlot = { _, _ -> false }
        )

        assertNotNull(selected)
        assertEquals("Impulse City!", selected?.cardName)
    }

    @Test
    fun remapImportedRootCardName_rewritesNestedRootReferences() {
        val remapped = buildImportedCharacter(
            cardName = "Impulse City",
            transformationNames = listOf("Impulse-City", "Other Card"),
            adventures = linkedMapOf("impulse city!" to 2, "Other Card" to 3),
        ).remapImportedRootCardName("My Custom DIM")

        assertEquals("My Custom DIM", remapped.cardName)
        assertEquals("My Custom DIM", remapped.transformationHistoryList[0].cardName)
        assertEquals("Other Card", remapped.transformationHistoryList[1].cardName)
        assertEquals(2, remapped.maxAdventureCompletedByCardMap["My Custom DIM"])
        assertEquals(3, remapped.maxAdventureCompletedByCardMap["Other Card"])
    }

    @Test
    fun remapImportedRootCardName_mergesDuplicateAdventureKeysUsingMaxProgress() {
        val remapped = buildImportedCharacter(
            cardName = "Impulse City",
            transformationNames = emptyList(),
            adventures = linkedMapOf("Impulse City" to 1, "impulse-city" to 4),
        ).remapImportedRootCardName("My Custom DIM")

        assertEquals(1, remapped.maxAdventureCompletedByCardCount)
        assertEquals(4, remapped.maxAdventureCompletedByCardMap["My Custom DIM"])
    }

    private fun buildImportedCharacter(
        cardName: String,
        transformationNames: List<String>,
        adventures: Map<String, Int>,
    ): Character {
        val builder = Character.newBuilder()
            .setCardName(cardName)
            .setCardId(77)
            .setCharacterStats(
                Character.CharacterStats.newBuilder()
                    .setSlotId(1)
                    .build()
            )
        transformationNames.forEachIndexed { index, transformationName ->
            builder.addTransformationHistory(
                Character.TransformationEvent.newBuilder()
                    .setCardName(transformationName)
                    .setSlotId(index)
                    .setPhase(0)
                    .build()
            )
        }
        builder.putAllMaxAdventureCompletedByCard(adventures)
        return builder.build()
    }
}

