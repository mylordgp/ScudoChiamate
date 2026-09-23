package it.scudochiamate

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import it.scudochiamate.database.AppDatabase
import it.scudochiamate.database.BlockedCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

/**
 * SpamCallScreeningService — cuore dell'app.
 *
 * Ordine di priorità (dalla più alta alla più bassa):
 *
 *   0. USER BLACKLIST — numero bloccato manualmente dall'utente
         // 0.5 RUBRICA → passa sempre
        if (isInContacts(rawNumber)) {
            Log.d(tag, "Contatto in rubrica — consentita: $rawNumber")
            respondToCall(callDetails, buildAllowResponse())
            return
        }
 *   1. WHITELIST      — numero/prefisso in whitelist → sempre consentito
 *   2. TIME BLOCK     — fascia oraria di silenzio → blocca
 *   3. FOREIGN        — prefisso non italiano → blocca (se toggle attivo)
 *   4. SPAM           — spam statico + lista comunitaria → blocca (se toggle attivo)
 *   5. DEFAULT        — lascia passare
 */
class SpamCallScreeningService : CallScreeningService() {

    private val tag   = "ScudoChiamate"
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val rawNumber = callDetails.handle?.schemeSpecificPart ?: ""

        val settings    = SettingsManager(this)
        val whitelist   = WhitelistManager(this)
        val timeMgr     = TimeBlockManager(this)
        val userBl      = UserBlacklist(this)
        val dynamicSpam = DynamicSpamList(this)

        Log.d(tag, "Chiamata in entrata da: \$rawNumber")

        // 0. USER BLACKLIST → blocca sempre
        if (userBl.isBlocked(rawNumber)) {
            Log.i(tag, "Blacklist utente — bloccata: \$rawNumber")
            onCallBlocked(rawNumber, BlockReason.USER_BLACKLIST)
            respondToCall(callDetails, buildBlockResponse())
            return
        }

        // 1. WHITELIST → passa sempre
        if (whitelist.isWhitelisted(rawNumber)) {
            Log.d(tag, "Whitelist — consentita: \$rawNumber")
            respondToCall(callDetails, buildAllowResponse())
            return
        }

        // 2. FASCIA ORARIA → blocca tutto
        if (timeMgr.isCurrentlyInBlockPeriod()) {
            Log.i(tag, "Time-block attivo — bloccata: \$rawNumber")
            onCallBlocked(rawNumber, BlockReason.TIME_BLOCK)
            respondToCall(callDetails, buildBlockResponse())
            return
        }

        // 3–4. Filtri prefisso/spam
        val blockReason: BlockReason? = when {
            settings.isBlockForeignEnabled() && SpamChecker.isForeignNumber(rawNumber) ->
                BlockReason.FOREIGN_PREFIX
            settings.isBlockSpamEnabled() &&
                (SpamChecker.isKnownSpam(rawNumber) || dynamicSpam.isSpam(rawNumber)) ->
                BlockReason.KNOWN_SPAM
            else -> null
        }

        if (blockReason != null) {
            Log.i(tag, "BLOCCATA: \$rawNumber (motivo: \$blockReason)")
            onCallBlocked(rawNumber, blockReason)
            respondToCall(callDetails, buildBlockResponse())
        } else {
            Log.d(tag, "Consentita: \$rawNumber")
            respondToCall(callDetails, buildAllowResponse())
        }
    }

    // -------------------------------------------------------------------------
    // Azioni al blocco
    // -------------------------------------------------------------------------

    private fun onCallBlocked(number: String, reason: BlockReason) {
        NotificationHelper(this).showBlockedCallNotification(number, reason)
        scope.launch {
            AppDatabase.getInstance(applicationContext)
                .blockedCallDao()
                .insert(BlockedCall(phoneNumber = number, reason = reason.name, timestamp = Date()))
        }
    }

    // -------------------------------------------------------------------------
    // Risposte CallScreening
    // -------------------------------------------------------------------------

        private fun isInContacts(number: String): Boolean {
        return try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            contentResolver.query(uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup._ID),
                null, null, null)?.use { it.count > 0 } ?: false
        } catch (e: Exception) { false }
    }
    private fun buildBlockResponse(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSilenceCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(true)
            .build()

    private fun buildAllowResponse(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(false)
            .build()
}

/** Motivo del blocco — usato per notifiche, log e statistiche. */
enum class BlockReason {
    FOREIGN_PREFIX,
    KNOWN_SPAM,
    TIME_BLOCK,
    SYSTEM_IMPORT,   // importato dalla blacklist di sistema
    USER_BLACKLIST   // bloccato manualmente dall'utente
}
