package com.github.cfogrady.vitalwear.card

import android.content.Context
import android.net.Uri
import android.os.Bundle
import timber.log.Timber

/**
 * Client-side helper for communicating with VBHelper's CardImportProvider.
 *
 * VBHelper exposes a ContentProvider at [AUTHORITY] that lets the companion query card
 * names already stored in VBHelper's database and push new DIM card bytes so that VBHelper
 * can parse and store them.  Both apps share the same signing certificate, so the
 * signature-level permission (ACCESS_CARD_DATA) is automatically granted.
 *
 * If VBHelper is not installed on the device every call returns a safe empty/false result —
 * the companion continues to work for watch-import even without VBHelper present.
 */
object VBHelperCardSync {

    private const val AUTHORITY             = "com.github.nacabaro.vbhelper.cardimport"
    private const val METHOD_GET_CARD_NAMES = "getCardNames"
    private const val METHOD_IMPORT_CARD    = "importCard"
    private const val EXTRA_CARD_BYTES      = "cardBytes"
    private const val EXTRA_CARD_NAME       = "cardName"
    private const val EXTRA_CARD_NAMES_LIST = "cardNames"

    private val URI = Uri.parse("content://$AUTHORITY")

    /**
     * Returns the set of card names already stored in VBHelper's database.
     * Used by [ImportCardActivity] to warn the user before re-importing a duplicate.
     */
    fun getImportedCardNames(context: Context): Set<String> {
        return runCatching {
            val result: Bundle = context.contentResolver.call(
                URI, METHOD_GET_CARD_NAMES, null, null
            ) ?: return@runCatching emptySet()
            result.getStringArray(EXTRA_CARD_NAMES_LIST)?.toSet() ?: emptySet()
        }.onFailure {
            Timber.w(it, "VBHelper CardImportProvider not reachable; card name check skipped.")
        }.getOrElse { emptySet() }
    }

    /**
     * Sends [cardBytes] (a serialised DIM file) and the user-visible [customName] to VBHelper
     * so it can parse and store the card in its own database.
     *
     * This call is fire-and-forget: failures are logged but not propagated — the watch
     * import is never blocked by VBHelper sync errors.
     */
    fun syncCard(context: Context, cardBytes: ByteArray, customName: String) {
        runCatching {
            val extras = Bundle().apply {
                putByteArray(EXTRA_CARD_BYTES, cardBytes)
                putString(EXTRA_CARD_NAME, customName)
            }
            context.contentResolver.call(URI, METHOD_IMPORT_CARD, null, extras)
            Timber.i("Card '$customName' synced to VBHelper database.")
        }.onFailure {
            Timber.w(it, "Failed to sync card '$customName' to VBHelper; skipping.")
        }
    }
}

