package it.scudochiamate

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

/**
 * Gestisce il blocco per fasce orarie.
 *
 * Quando attivo, durante la fascia configurata vengono bloccate
 * tutte le chiamate da numeri NON in whitelist, indipendentemente
 * dal prefisso o dallo stato spam.
 *
 * Esempio d'uso tipico: silenzio notturno 22:00 – 08:00.
 * La fascia può attraversare la mezzanotte (es. start=22 end=8).
 */
class TimeBlockManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // -----------------------------------------------------------------------
    // Abilitazione
    // -----------------------------------------------------------------------

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()

    // -----------------------------------------------------------------------
    // Orari
    // -----------------------------------------------------------------------

    fun getStartHour(): Int   = prefs.getInt(KEY_START_HOUR, 22)
    fun getStartMinute(): Int = prefs.getInt(KEY_START_MIN, 0)
    fun getEndHour(): Int     = prefs.getInt(KEY_END_HOUR, 8)
    fun getEndMinute(): Int   = prefs.getInt(KEY_END_MIN, 0)

    fun setStart(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_START_HOUR, hour)
            .putInt(KEY_START_MIN, minute)
            .apply()
    }

    fun setEnd(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_END_HOUR, hour)
            .putInt(KEY_END_MIN, minute)
            .apply()
    }

    /** Stringa leggibile dell'orario di inizio, es. "22:00". */
    fun startLabel(): String = "%02d:%02d".format(getStartHour(), getStartMinute())

    /** Stringa leggibile dell'orario di fine, es. "08:00". */
    fun endLabel(): String = "%02d:%02d".format(getEndHour(), getEndMinute())

    // -----------------------------------------------------------------------
    // Controllo runtime
    // -----------------------------------------------------------------------

    /**
     * Restituisce true se l'ora corrente cade nella fascia di blocco.
     * Gestisce correttamente fasce che attraversano la mezzanotte.
     */
    fun isCurrentlyInBlockPeriod(): Boolean {
        if (!isEnabled()) return false

        val now = Calendar.getInstance()
        val nowMins  = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMins = getStartHour() * 60 + getStartMinute()
        val endMins   = getEndHour()   * 60 + getEndMinute()

        return if (startMins <= endMins) {
            // Fascia nello stesso giorno: es. 09:00–18:00
            nowMins in startMins..endMins
        } else {
            // Fascia che attraversa mezzanotte: es. 22:00–08:00
            nowMins >= startMins || nowMins <= endMins
        }
    }

    companion object {
        private const val PREFS_NAME = "scudo_timeblock"
        const val KEY_ENABLED     = "time_block_enabled"
        const val KEY_START_HOUR  = "start_hour"
        const val KEY_START_MIN   = "start_min"
        const val KEY_END_HOUR    = "end_hour"
        const val KEY_END_MIN     = "end_min"
    }
}
