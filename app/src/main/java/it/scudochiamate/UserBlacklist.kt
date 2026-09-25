package it.scudochiamate

import android.content.Context

/**
 * Lista nera personale — numeri bloccati manualmente dall'utente
 * tramite la cronologia chiamate.
 */
class UserBlacklist(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Blocca un numero; il nome (es. dalla cronologia) viene mostrato nelle chiamate bloccate. */
    fun addNumber(number: String, name: String = "") {
        val current = getBlockedNumbers().toMutableSet()
        current.add(normalize(number))
        val editor = prefs.edit().putStringSet(KEY_NUMBERS, current)
        if (name.isNotBlank()) editor.putString(NAME_PREFIX + normalize(number), name.trim())
        editor.apply()
    }

    /** Sblocca un numero; il nome resta salvato e ricompare se viene bloccato di nuovo. */
    fun removeNumber(number: String) {
        val current = getBlockedNumbers().toMutableSet()
        current.remove(normalize(number))
        prefs.edit().putStringSet(KEY_NUMBERS, current).apply()
    }

    /** Imposta o cambia il nome di un numero bloccato; un nome vuoto lo cancella. */
    fun setNameFor(number: String, name: String) {
        val key = NAME_PREFIX + normalize(number)
        if (name.isBlank()) prefs.edit().remove(key).apply()
        else prefs.edit().putString(key, name.trim()).apply()
    }

    /** Nome associato al numero bloccato, oppure stringa vuota. */
    fun getNameFor(number: String): String =
        prefs.getString(NAME_PREFIX + normalize(number), "") ?: ""

    fun isBlocked(number: String): Boolean =
        getBlockedNumbers().contains(normalize(number))

    fun getBlockedNumbers(): Set<String> =
        prefs.getStringSet(KEY_NUMBERS, emptySet()) ?: emptySet()

    companion object {
    fun normalize(raw: String): String = raw.replace(Regex("[\\s\\-().]+"), "")
    private const val PREFS_NAME = "user_blacklist"
    private const val KEY_NUMBERS = "blocked_numbers"
    private const val NAME_PREFIX = "name_"
}
    fun exportAsJson(): String {
        return org.json.JSONArray(getBlockedNumbers()).toString()
    }

    /** Importa i numeri dal JSON; restituisce quanti sono nuovi, -1 se il file non è valido. */
    fun importFromJson(json: String): Int {
        return try {
            val array = org.json.JSONArray(json)
            val imported = (0 until array.length())
                .map { normalize(array.getString(it)) }
                .filter { it.isNotBlank() }
                .toSet()
            val current = getBlockedNumbers()
            val added = imported - current
            if (added.isNotEmpty()) {
                prefs.edit().putStringSet(KEY_NUMBERS, current + added).apply()
            }
            added.size
        } catch (e: Exception) { -1 }
    }
}
