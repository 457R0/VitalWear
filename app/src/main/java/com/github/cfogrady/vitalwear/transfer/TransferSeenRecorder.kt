package com.github.cfogrady.vitalwear.transfer

import com.github.cfogrady.vitalwear.common.data.SharedTransferSeenDao
import com.github.cfogrady.vitalwear.protos.Character

internal fun SharedTransferSeenDao.recordImportedCharacterSeen(character: Character, seenAtEpochMillis: Long) {
    markSeen(character.cardName, character.characterStats.slotId, seenAtEpochMillis)
    for (transformation in character.transformationHistoryList) {
        if (transformation.cardName.isBlank()) {
            continue
        }
        markSeen(transformation.cardName, transformation.slotId, seenAtEpochMillis)
    }
}

