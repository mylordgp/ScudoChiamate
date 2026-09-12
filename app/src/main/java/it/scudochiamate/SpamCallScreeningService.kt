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
 * Ordine di priorità dei controlli (dalla più alta alla più bassa):
 *
 *   1. WHITELIST  — numero o prefisso in whitelist → sempre consentito
 *   2. TIME BLOCK — fascia oraria di silenzio attiva → blocca se non in whitelist
 *   3. FOREIGN    — prefisso non italiano → blocca (se toggle attivo)
 *   4. SPAM       — numero spam noto → blocca (se toggle attivo)
 *   5. DEFAULT    — lascia passare
 *
 * REQUISITO: l'app deve essere impostata come app predefinita per
 * "Caller ID e spam" in Impostazioni → App → App predefinite.
 */
class SpamCallScreeningService : CallScreeningService() {

    private val tag = "ScudoChiamate"
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val rawNumber = callDetails.handle?.schemeSpecificPart ?: ""

        val settings  = SettingsManager(this)
        val whitelist = WhitelistManager(this)
        val timeMgr   = TimeBlockManager(this)

        Log.d(tag, "Chiamata in entrata da: $rawNumber")

        // 1. WHITELIST → passa sempre
        if (whitelist.isWhitelisted(rawNumber)) {
            Log.d(tag, "Whitelist — consentita: $rawNumber")
            respondToCall(callDetails, buildAllowResponse())
            return
        }

        // 2. FASCIA ORARIA → blocca tutto (tranne whitelist già gestita sopra)
        if (timeMgr.isCurrentlyInBlockPeriod()) {
            Log.i(tag, "Time-block attivo — bloccata: $rawNumber")
            onCallBlocked(rawNumber, BlockReason.TIME_BLOCK)
            respondToCall(callDetails, buildBlockResponse())
            return
        }

        // 3–4. Filtri prefisso/spam
        val blockReason: BlockReason? = when {
            settings.isBlockForeignEnabled() && SpamChecker.isForeignNumber(rawNumber) ->
                BlockReason.FOREIGN_PREFIX
            settings.isBlockSpamEnabled() && SpamChecker.isKnownSpam(rawNumber) ->
                BlockReason.KNOWN_SPAM
            else -> null
        }

        if (blockReason != null) {
            Log.i(tag, "BLOCCATA: $rawNumber (motivo: $blockReason)")
            onCallBlocked(rawNumber, blockReason)
            respondToCall(callDetails, buildBlockResponse())
        } else {
            Log.d(tag, "Consentita: $rawNumber")
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
    SYSTEM_IMPORT   // numero era già bloccato nel sistema; non viene usato attivamente qui
}
