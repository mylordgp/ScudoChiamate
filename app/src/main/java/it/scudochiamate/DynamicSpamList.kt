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
            .filter { it.count(Char::isDigit) >= 5 }
            .toSet()
    }

    /**
     * Porta il numero in formato E.164 così che lista e chiamata in entrata
     * coincidano indipendentemente dal formato con cui arrivano.
     * 0033612345678 → +33612345678
     * 393401234567  → +393401234567
     * 3401234567    → +393401234567 (nazionale italiano)
     * 0212345678    → +390212345678
     */
    private fun normalize(raw: String): String {
        val n = raw.replace(Regex("[\\s\\-().]+"), "")
        return when {
            n.startsWith("+") -> n
            n.startsWith("00") -> "+" + n.removePrefix("00")
            n.startsWith("39") && n.length > 10 -> "+$n"
            n.startsWith("0") || n.startsWith("3") -> "+39$n"
            else -> n
        }
    }

    companion object {
        private const val PREFS_NAME  = "dynamic_spam_list"
        // Chiave nuova: le voci salvate col vecchio formato (senza "+") vengono
        // ignorate e la lista viene riscaricata al prossimo avvio (count() == 0).
        private const val KEY_NUMBERS = "numbers_e164"
        private const val KEY_UPDATED = "last_updated"
    }
}
