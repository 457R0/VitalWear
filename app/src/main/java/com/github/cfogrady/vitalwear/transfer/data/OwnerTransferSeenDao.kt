package com.github.cfogrady.vitalwear.transfer.data

import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.github.cfogrady.vitalwear.common.data.SharedTransferSeenDao
import com.github.cfogrady.vitalwear.common.data.SharedTransferSeenEntity

/**
 * Provider-backed transfer DAO. VBH-VW owns storage; VitalWear forwards markSeen calls.
 */
class OwnerTransferSeenDao(
    private val context: Context,
) : SharedTransferSeenDao {
    companion object {
        private const val AUTHORITY = "com.github.nacabaro.vbhelper.transferseen"
        private val URI: Uri = Uri.parse("content://$AUTHORITY")
        private const val METHOD_MARK_SEEN = "markSeen"
        private const val EXTRA_CARD_NAME = "cardName"
        private const val EXTRA_SLOT_ID = "slotId"
        private const val EXTRA_SEEN_AT_EPOCH_MILLIS = "seenAtEpochMillis"
    }

    override fun insert(entry: SharedTransferSeenEntity): Long {
        throw UnsupportedOperationException("OwnerTransferSeenDao supports markSeen only")
    }

    override fun update(cardName: String, cardLookupKey: String, slotId: Int, seenAtEpochMillis: Long) {
        throw UnsupportedOperationException("OwnerTransferSeenDao supports markSeen only")
    }

    override fun markSeen(cardName: String, slotId: Int, seenAtEpochMillis: Long) {
        val extras = Bundle().apply {
            putString(EXTRA_CARD_NAME, cardName)
            putInt(EXTRA_SLOT_ID, slotId)
            putLong(EXTRA_SEEN_AT_EPOCH_MILLIS, seenAtEpochMillis)
        }
        context.contentResolver.call(
            URI,
            METHOD_MARK_SEEN,
            null,
            extras
        )
    }
}


