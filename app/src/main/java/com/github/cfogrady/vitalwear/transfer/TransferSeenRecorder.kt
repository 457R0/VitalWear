package com.github.cfogrady.vitalwear.transfer

import com.github.cfogrady.vitalwear.common.data.SharedTransferSeenDao
import com.github.cfogrady.vitalwear.protos.Character

internal fun SharedTransferSeenDao.recordImportedCharacterSeen(character: Character, seenAtEpochMillis: Long) {
    val entries = buildList {
        add(SharedTransferSeenDao.TransferSeenRecord(character.cardName, character.characterStats.slotId))
        for (transformation in character.transformationHistoryList) {
            if (transformation.cardName.isBlank()) {
                continue
            }
            add(SharedTransferSeenDao.TransferSeenRecord(transformation.cardName, transformation.slotId))
        }
    }
    markSeen(entries, seenAtEpochMillis)
}

