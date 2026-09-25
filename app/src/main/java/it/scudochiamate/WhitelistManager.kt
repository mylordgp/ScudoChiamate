package it.scudochiamate

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestisce la whitelist di prefissi internazionali e numeri specifici
 * che devono SEMPRE essere consentiti, anche se esteri o spam.
 *
 * Prefisso internazionale: stringa che inizia con "+" es. "+44", "+33", "+49"
 * Numero specifico: numero completo es. "+447700900123"
 */
class WhitelistManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // -----------------------------------------------------------------------
    // Prefissi Paese consentiti (es. "+44" per UK)
    // -----------------------------------------------------------------------

    fun getAllowedPrefixes(): Set<String> =
        prefs.getStringSet(KEY_PREFIXES, emptySet()) ?: emptySet()

    fun addAllowedPrefix(prefix: String) {
        val clean = normalizePrefix(prefix)
        if (clean.isBlank()) return
        val updated = getAllowedPrefixes().toMutableSet().apply { add(clean) }
        prefs.edit().putStringSet(KEY_PREFIXES, updated).apply()
    }

    fun removeAllowedPrefix(prefix: String) {
        val clean = normalizePrefix(prefix)
        val updated = getAllowedPrefixes().toMutableSet().apply { removeAll { normalizePrefix(it) == clean } }
        prefs.edit().putStringSet(KEY_PREFIXES, updated).apply()
    }

    // -----------------------------------------------------------------------
    // Numeri specifici consentiti
    // -----------------------------------------------------------------------

    fun getAllowedNumbers(): Set<String> =
        prefs.getStringSet(KEY_NUMBERS, emptySet()) ?: emptySet()

    /** Aggiunge un numero; il nome (es. dalla cronologia) viene mostrato nella lista. */
    fun addAllowedNumber(number: String, name: String = "") {
        val clean = normalizeNumber(number)
        if (clean.isBlank()) return
        val updated = getAllowedNumbers().toMutableSet().apply { add(clean) }
        val editor = prefs.edit().putStringSet(KEY_NUMBERS, updated)
        if (name.isNotBlank()) editor.putString(NAME_PREFIX + clean, name.trim())
        editor.apply()
    }

    fun removeAllowedNumber(number: String) {
        val clean = normalizeNumber(number)
        val updated = getAllowedNumbers().toMutableSet().apply { removeAll { normalizeNumber(it) == clean } }
        prefs.edit().putStringSet(KEY_NUMBERS, updated)
            .remove(NAME_PREFIX + clean).apply()
    }

    /** Nome associato al numero in whitelist, oppure stringa vuota. */
    fun getNameFor(number: String): String =
        prefs.getString(NAME_PREFIX + normalizeNumber(number), "") ?: ""

    // -----------------------------------------------------------------------
    // Controllo se un numero è in whitelist
    // -----------------------------------------------------------------------

    /**
     * Restituisce true se il numero raw è in whitelist:
     *   - corrisponde esattamente a un numero salvato, OPPURE
     *   - inizia con uno dei prefissi consentiti
     */
    fun isWhitelisted(raw: String): Boolean {
        val n = normalizeNumber(raw)

        // Numero esatto
        if (getAllowedNumbers().any { normalizeNumber(it) == n }) return true

        // Prefisso Paese
        val prefixes = getAllowedPrefixes()
        if (prefixes.isEmpty()) return false
        return prefixes.any { p -> n.startsWith(normalizePrefix(p)) }
    }

    // -----------------------------------------------------------------------
    // Utilità
    // -----------------------------------------------------------------------

    /** Normalizza prefisso Paese: "44" → "+44", "+44 " → "+44" */
    private fun normalizePrefix(raw: String): String {
        val stripped = raw.trim().replace(Regex("[\\s\\-()]"), "")
        return if (stripped.startsWith("+")) stripped else "+$stripped"
    }

    /** Rimuove spazi e caratteri non numerici tranne "+" iniziale. */
    private fun normalizeNumber(raw: String): String =
        raw.trim().replace(Regex("[\\s\\-().]+"), "")

    companion object {
        private const val PREFS_NAME = "scudo_whitelist"
        const val KEY_PREFIXES = "allowed_prefixes"
        const val KEY_NUMBERS  = "allowed_numbers"
        private const val NAME_PREFIX = "name_"
    }
}
