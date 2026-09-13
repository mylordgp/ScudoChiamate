package it.scudochiamate

import android.content.Context
import android.content.SharedPreferences

/**
 * Lista spam scaricata automaticamente da fonti comunitarie.
 *
 * Fonte: https://github.com/Oros42/phone-blacklist
 * Lista mantenuta dalla community, aggiornata automaticamente,
 * NESSUNA manutenzione richiesta all'utente.
 */
class DynamicSpamList(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Aggiorna la lista con il contenuto grezzo scaricato.
     * Supporta CSV (prende la prima colonna) e liste semplici (un numero per riga).
     */
    fun update(rawContent: String) {
        val numbers = parseNumbers(rawContent)
        prefs.edit()
            .putStringSet(KEY_NUMBERS, numbers)
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .apply()
    }

    /** True se il numero è nella lista dinamica. */
    fun isSpam(number: String): Boolean {
        val n = normalize(number)
        val numbers = prefs.getStringSet(KEY_NUMBERS, emptySet()) ?: return false
        return numbers.contains(n)
    }

    /** Numero di voci nella lista (0 se non ancora scaricata). */
    fun count(): Int =
        (prefs.getStringSet(KEY_NUMBERS, emptySet()) ?: emptySet()).size

    // -------------------------------------------------------------------------

    private fun parseNumbers(raw: String): Set<String> {
        return raw.lines()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                line.split(",", ";", "\t").firstOrNull()?.trim()
            }
            .map { normalize(it) }
            .filter { it.length >= 5 }
            .toSet()
    }

    private fun normalize(raw: String): String =
        raw.replace(Regex("[\\s\\-().+]+"), "")

    companion object {
        private const val PREFS_NAME  = "dynamic_spam_list"
        private const val KEY_NUMBERS = "numbers"
        private const val KEY_UPDATED = "last_updated"
    }
}
