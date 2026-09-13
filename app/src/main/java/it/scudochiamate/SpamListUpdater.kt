package it.scudochiamate

import android.content.Context
import android.util.Log
import androidx.work.*
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Worker periodico — scarica automaticamente la lista spam comunitaria.
 *
 * Fonte: https://github.com/Oros42/phone-blacklist
 * Lista mantenuta dalla community GitHub, nessuna registrazione o chiave API richiesta.
 * Aggiornamento automatico ogni 7 giorni in background.
 */
class SpamListUpdater(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            val content = URL(SPAM_LIST_URL).readText(Charsets.UTF_8)
            if (content.length > 50) {
                val list = DynamicSpamList(applicationContext)
                list.update(content)
                Log.i(TAG, "Lista spam aggiornata: \${list.count()} voci")
                Result.success()
            } else {
                Log.w(TAG, "Risposta troppo corta — retry")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore download lista spam: \${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SpamListUpdater"
        private const val WORK_NAME = "spam_list_update"

        // Lista spam comunitaria — aggiornata automaticamente dalla community
        // Nessun intervento manuale richiesto all'utente
        // https://github.com/Oros42/phone-blacklist
        private const val SPAM_LIST_URL =
            "https://raw.githubusercontent.com/Oros42/phone-blacklist/master/blacklist.csv"

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
