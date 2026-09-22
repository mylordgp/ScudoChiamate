package it.scudochiamate

import android.content.Context

/**
 * Lista nera personale — numeri bloccati manualmente dall'utente
 * tramite la cronologia chiamate.
 */
class UserBlacklist(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun addNumber(number: String) {
        val current = getBlockedNumbers().toMutableSet()
        current.add(normalize(number))
        prefs.edit().putStringSet(KEY_NUMBERS, current).apply()
    }

    fun removeNumber(number: String) {
        val current = getBlockedNumbers().toMutableSet()
        current.remove(normalize(number))
        prefs.edit().putStringSet(KEY_NUMBERS, current).apply()
    }

    fun isBlocked(number: String): Boolean =
        getBlockedNumbers().contains(normalize(number))

    fun getBlockedNumbers(): Set<String> =
        prefs.getStringSet(KEY_NUMBERS, emptySet()) ?: emptySet()

    companion object {
    fun normalize(raw: String): String = raw.replace(Regex("[\\s\\-().]+"), "")
    private const val PREFS_NAME = "user_blacklist"
    private const val KEY_NUMBERS = "blocked_numbers"
}
    fun exportAsJson(): String {
        return org.json.JSONArray(getBlockedNumbers()).toString()
    }

    fun importFromJson(json: String): Int {
        return try {
            val array = org.json.JSONArray(json)
            var count = 0
            for (i in 0 until array.length()) {
                val num = normalize(array.getString(i))
                if (addNumber(num)) count++
            }
            count
        } catch (e: Exception) { -1 }
    }
}
