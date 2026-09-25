package it.scudochiamate

import android.content.Context
import android.util.Log
import androidx.work.*
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Worker periodico — scarica automaticamente la lista spam comunitaria.
 *
 * Fonti: vedi SPAM_LIST_URLS (lista italiana + lista internazionale).
 * Liste mantenute dalla community GitHub, nessuna registrazione o chiave API richiesta.
 * Aggiornamento automatico ogni 7 giorni in background.
 */
class SpamListUpdater(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    /**
     * Scarica tutte le liste e le unisce. Se anche una sola fallisce non si
     * aggiorna nulla: meglio tenere la lista precedente che perdere metà voci.
     */
    override suspend fun doWork(): Result {
        return try {
            val contents = SPAM_LIST_URLS.map { url ->
                val content = URL(url).readText(Charsets.UTF_8)
                if (content.isBlank()) {
                    Log.w(TAG, "Risposta vuota da $url — retry")
                    return Result.retry()
                }
                content
            }
            val list = DynamicSpamList(applicationContext)
            list.update(contents.joinToString("\n"))
            Log.i(TAG, "Lista spam aggiornata: ${list.count()} voci")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Errore download lista spam: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SpamListUpdater"
        private const val WORK_NAME = "spam_list_update"

        // Liste spam comunitarie — aggiornate automaticamente dalla community.
        // Formato: CSV con il numero E.164 nella prima colonna.
        private val SPAM_LIST_URLS = listOf(
            // https://github.com/thesqual87/blocklist-telefonica-italia (CC BY-SA 4.0,
            // richiede citazione della fonte: vedi R.string.spam_sources)
            "https://raw.githubusercontent.com/thesqual87/blocklist-telefonica-italia/main/data/blocklist.csv",
            // https://github.com/Oros42/phone-blacklist (Unlicense)
            "https://raw.githubusercontent.com/Oros42/phone-blacklist/master/blacklist.csv",
        )

        /** Pianifica aggiornamento settimanale (KEEP = non riprogramma se già attivo). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SpamListUpdater>(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Forza download immediato (es. primo avvio quando la lista è vuota). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SpamListUpdater>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
