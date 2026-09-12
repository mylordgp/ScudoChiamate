package it.scudochiamate

import android.content.Context
import android.provider.BlockedNumberContract
import android.util.Log
import it.scudochiamate.database.AppDatabase
import it.scudochiamate.database.BlockedCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Importa automaticamente i numeri già bloccati dal sistema (MIUI / Android).
 *
 * Fonte: BlockedNumberContract.BlockedNumbers — lo stesso database usato
 * dall'app Telefono di sistema per il blocco nativo.
 *
 * PERMESSI RICHIESTI:
 *   android.permission.READ_BLOCKED_NUMBERS
 *   Disponibile solo se l'app ha il ruolo CALL_SCREENING (già richiesto).
 *
 * Se il permesso non è concesso, l'import restituisce ImportResult.PermissionDenied
 * e l'app continua a funzionare normalmente senza crash.
 */
object SystemImporter {

    private const val TAG = "ScudoImporter"

    sealed class ImportResult {
        data class Success(val imported: Int, val skipped: Int) : ImportResult()
        object PermissionDenied : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    /**
     * Legge il registro blocchi di sistema e aggiunge i numeri al DB interno
     * come blocchi di tipo "SYSTEM_IMPORT" se non già presenti.
     *
     * Va chiamato su thread IO (usa suspend + withContext).
     */
    suspend fun importFromSystem(context: Context): ImportResult = withContext(Dispatchers.IO) {
        // Verifica se abbiamo il permesso
        if (!BlockedNumberContract.canCurrentUserBlockNumbers(context)) {
            Log.w(TAG, "Permesso READ_BLOCKED_NUMBERS non disponibile")
            return@withContext ImportResult.PermissionDenied
        }

        return@withContext try {
            val db  = AppDatabase.getInstance(context)
            val dao = db.blockedCallDao()

            // Numeri già nel nostro DB (per non duplicare)
            val existing = dao.getAllNumbers().toHashSet()

            val uri = BlockedNumberContract.BlockedNumbers.CONTENT_URI
            val projection = arrayOf(
                BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
                BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER
            )

            val cursor = context.contentResolver.query(uri, projection, null, null, null)
                ?: return@withContext ImportResult.Error("Cursor nullo — accesso negato")

            var imported = 0
            var skipped  = 0

            cursor.use {
                val colOrig = it.getColumnIndexOrThrow(
                    BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
                val colE164 = it.getColumnIndex(
                    BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER)

                while (it.moveToNext()) {
                    val number = it.getString(colOrig)
                        ?: (if (colE164 >= 0) it.getString(colE164) else null)
                        ?: continue

                    if (number in existing) {
                        skipped++
                        continue
                    }

                    dao.insert(
                        BlockedCall(
                            phoneNumber = number,
                            reason      = "SYSTEM_IMPORT",
                            timestamp   = Date()
                        )
                    )
                    existing.add(number)
                    imported++
                }
            }

            Log.i(TAG, "Import completato: $imported importati, $skipped già presenti")
            ImportResult.Success(imported, skipped)

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException durante import: ${e.message}")
            ImportResult.PermissionDenied
        } catch (e: Exception) {
            Log.e(TAG, "Errore import: ${e.message}")
            ImportResult.Error(e.message ?: "Errore sconosciuto")
        }
    }
}
