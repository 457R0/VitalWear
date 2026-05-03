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

    @Test
    fun sanitizeForImport_preservesAdditionalHceStats() {
        val sanitized = Character.newBuilder()
            .setCardName("Pulse City")
            .setCharacterStats(
                Character.CharacterStats.newBuilder()
                    .setSlotId(1)
                    .setDeviceType(Character.CharacterStats.TransferDeviceType.TRANSFER_DEVICE_TYPE_BE)
                    .setAgeInDays(7)
                    .setActivityLevel(2)
                    .setHeartRateCurrent(88)
                    .setGeneration(3)
                    .setTotalTrophies(25)
                    .setNextAdventureMissionStage(9)
                    .setAbilityType(4)
                    .build()
            )
            .build()
            .sanitizeForImport()

        assertEquals(Character.CharacterStats.TransferDeviceType.TRANSFER_DEVICE_TYPE_BE, sanitized.characterStats.deviceType)
        assertEquals(7, sanitized.characterStats.ageInDays)
        assertEquals(2, sanitized.characterStats.activityLevel)
        assertEquals(88, sanitized.characterStats.heartRateCurrent)
        assertEquals(3, sanitized.characterStats.generation)
        assertEquals(25, sanitized.characterStats.totalTrophies)
        assertEquals(9, sanitized.characterStats.nextAdventureMissionStage)
        assertEquals(4, sanitized.characterStats.abilityType)
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

    @Test
    fun crossDeviceTransfer_preservesAllHceStats_protoToEntity() {
        val originalProto = Character.newBuilder()
            .setCardName("Test Card")
            .setCharacterStats(
                Character.CharacterStats.newBuilder()
                    .setSlotId(2)
                    .setDeviceType(Character.CharacterStats.TransferDeviceType.TRANSFER_DEVICE_TYPE_BE)
                    .setAgeInDays(15)
                    .setActivityLevel(3)
                    .setHeartRateCurrent(95)
                    .setGeneration(5)
                    .setTotalTrophies(50)
                    .setNextAdventureMissionStage(12)
                    .setAbilityType(7)
                    .setAbilityBranch(2)
                    .setAbilityRarity(3)
                    .setAbilityReset(1)
                    .setRank(4)
                    .setItemType(5)
                    .setItemMultiplier(2)
                    .setItemRemainingTime(100)
                    .setFirmwareMinorVersion(3)
                    .setFirmwareMajorVersion(1)
                    .setTotalBattles(20)
                    .setCurrentPhaseBattles(5)
                    .setTotalWins(15)
                    .setCurrentPhaseWins(4)
                    .setMood(80)
                    .setVitals(60)
                    .setTrainedBp(10)
                    .setTrainedHp(20)
                    .setTrainedAp(15)
                    .setTrainedPp(25)
                    .build()
            )
            .build()

        // Convert proto to entity
        val entity = originalProto.characterStats.toCharacterEntity("Test Card")

        // Verify all cross-device stats are preserved
        assertEquals(15, entity.ageInDays)
        assertEquals(3, entity.activityLevel)
        assertEquals(95, entity.heartRateCurrent)
        assertEquals(5, entity.generation)
        assertEquals(50, entity.totalTrophies)
        assertEquals(12, entity.nextAdventureMissionStage)
        assertEquals(7, entity.abilityType)
        assertEquals(2, entity.abilityBranch)
        assertEquals(3, entity.abilityRarity)
        assertEquals(1, entity.abilityReset)
        assertEquals(4, entity.rank)
        assertEquals(5, entity.itemType)
        assertEquals(2, entity.itemMultiplier)
        assertEquals(100, entity.itemRemainingTime)
        assertEquals(3, entity.firmwareMinorVersion)
        assertEquals(1, entity.firmwareMajorVersion)
    }

    @Test
    fun crossDeviceTransfer_preservesAllHceStats_entityToProto() {
        // Build a CharacterEntity with cross-device stats
        val entity = com.github.cfogrady.vitalwear.character.data.CharacterEntity(
            id = 1,
            state = com.github.cfogrady.vitalwear.character.data.CharacterState.ACTIVE,
            cardFile = "Test Card",
            slotId = 2,
            lastUpdate = java.time.LocalDateTime.now(),
            vitals = 60,
            trainingTimeRemainingInSeconds = 100,
            hasTransformations = true,
            timeUntilNextTransformation = 200,
            trainedBp = 10,
            trainedHp = 20,
            trainedAp = 15,
            trainedPP = 25,
            injured = false,
            lostBattlesInjured = 0,
            accumulatedDailyInjuries = 0,
            totalBattles = 20,
            currentPhaseBattles = 5,
            totalWins = 15,
            currentPhaseWins = 4,
            mood = 80,
            sleeping = false,
            dead = false,
            // Cross-device stats
            ageInDays = 15,
            activityLevel = 3,
            heartRateCurrent = 95,
            generation = 5,
            totalTrophies = 50,
            nextAdventureMissionStage = 12,
            abilityType = 7,
            abilityBranch = 2,
            abilityRarity = 3,
            abilityReset = 1,
            rank = 4,
            itemEffectMentalStateValue = 0,
            itemEffectMentalStateMinutesRemaining = 0,
            itemEffectActivityLevelValue = 0,
            itemEffectActivityLevelMinutesRemaining = 0,
            itemEffectVitalPointsChangeValue = 0,
            itemEffectVitalPointsChangeMinutesRemaining = 0,
            itemType = 5,
            itemMultiplier = 2,
            itemRemainingTime = 100,
            firmwareMinorVersion = 3,
            firmwareMajorVersion = 1,
        )

        // Convert entity to proto
        val proto = entity.toProto(
            deviceType = Character.CharacterStats.TransferDeviceType.TRANSFER_DEVICE_TYPE_BE
        )

        // Verify all cross-device stats are preserved
        assertEquals(15, proto.ageInDays)
        assertEquals(3, proto.activityLevel)
        assertEquals(95, proto.heartRateCurrent)
        assertEquals(5, proto.generation)
        assertEquals(50, proto.totalTrophies)
        assertEquals(12, proto.nextAdventureMissionStage)
        assertEquals(7, proto.abilityType)
        assertEquals(2, proto.abilityBranch)
        assertEquals(3, proto.abilityRarity)
        assertEquals(1, proto.abilityReset)
        assertEquals(4, proto.rank)
        assertEquals(5, proto.itemType)
        assertEquals(2, proto.itemMultiplier)
        assertEquals(100, proto.itemRemainingTime)
        assertEquals(3, proto.firmwareMinorVersion)
        assertEquals(1, proto.firmwareMajorVersion)
    }

    @Test
    fun crossDeviceTransfer_fullRoundTrip_preservesStats() {
        // Original proto from one device
        val originalProto = Character.newBuilder()
            .setCardName("Impulse City")
            .setCharacterStats(
                Character.CharacterStats.newBuilder()
                    .setSlotId(1)
                    .setDeviceType(Character.CharacterStats.TransferDeviceType.TRANSFER_DEVICE_TYPE_VB)
                    .setAgeInDays(20)
                    .setActivityLevel(5)
                    .setHeartRateCurrent(110)
                    .setGeneration(3)
                    .setTotalTrophies(75)
                    .setNextAdventureMissionStage(15)
                    .setAbilityType(2)
                    .setAbilityBranch(1)
                    .build()
            )
            .build()

        // Step 1: Import to entity (simulating receiving from another device)
        val entity = originalProto.characterStats.toCharacterEntity("Impulse City")

        // Step 2: Export from entity back to proto
        val exportedProto = entity.toProto(
            deviceType = Character.CharacterStats.TransferDeviceType.TRANSFER_DEVICE_TYPE_BE
        )

        // Step 3: Verify all stats survived the round-trip
        assertEquals(originalProto.characterStats.ageInDays, exportedProto.ageInDays)
        assertEquals(originalProto.characterStats.activityLevel, exportedProto.activityLevel)
        assertEquals(originalProto.characterStats.heartRateCurrent, exportedProto.heartRateCurrent)
        assertEquals(originalProto.characterStats.generation, exportedProto.generation)
        assertEquals(originalProto.characterStats.totalTrophies, exportedProto.totalTrophies)
        assertEquals(originalProto.characterStats.nextAdventureMissionStage, exportedProto.nextAdventureMissionStage)
        assertEquals(originalProto.characterStats.abilityType, exportedProto.abilityType)
        assertEquals(originalProto.characterStats.abilityBranch, exportedProto.abilityBranch)
        // Verify device type was updated for new device
        assertEquals(Character.CharacterStats.TransferDeviceType.TRANSFER_DEVICE_TYPE_BE, exportedProto.deviceType)
    }
}
