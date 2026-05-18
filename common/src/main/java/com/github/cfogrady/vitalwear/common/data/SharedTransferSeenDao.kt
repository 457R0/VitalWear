package com.github.cfogrady.vitalwear.common.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SharedTransferSeenDao {
    data class TransferSeenRecord(
        val cardName: String,
        val slotId: Int,
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(entry: SharedTransferSeenEntity): Long

    @Query(
        """
        UPDATE ${SharedTransferSeenEntity.TABLE}
        SET cardName = :cardName,
            seenAtEpochMillis = :seenAtEpochMillis
        WHERE cardLookupKey = :cardLookupKey
          AND slotId = :slotId
        """
    )
    fun update(cardName: String, cardLookupKey: String, slotId: Int, seenAtEpochMillis: Long)

    @Transaction
    fun markSeen(cardName: String, slotId: Int, seenAtEpochMillis: Long) {
        markSeen(listOf(TransferSeenRecord(cardName, slotId)), seenAtEpochMillis)
    }

    @Transaction
    fun markSeen(entries: List<TransferSeenRecord>, seenAtEpochMillis: Long) {
        for (entry in entries.distinctBy { it.cardName to it.slotId }) {
            if (entry.slotId < 0) continue
            val cardLookupKey = entry.cardName.lowercase().filter { it.isLetterOrDigit() }
            if (cardLookupKey.isBlank()) continue
            val inserted = insert(
                SharedTransferSeenEntity(
                    cardName = entry.cardName,
                    cardLookupKey = cardLookupKey,
                    slotId = entry.slotId,
                    seenAtEpochMillis = seenAtEpochMillis,
                )
            )
            if (inserted == -1L) {
                update(entry.cardName, cardLookupKey, entry.slotId, seenAtEpochMillis)
            }
        }
    }
}

