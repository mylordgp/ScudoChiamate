package it.scudochiamate

import android.content.Context
import android.content.SharedPreferences

/**
 * Legge e scrive le preferenze dell'app usando SharedPreferences.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isBlockForeignEnabled(): Boolean =
        prefs.getBoolean(KEY_BLOCK_FOREIGN, true)

    fun setBlockForeignEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_BLOCK_FOREIGN, enabled).apply()

    fun isBlockSpamEnabled(): Boolean =
        prefs.getBoolean(KEY_BLOCK_SPAM, true)

    fun setBlockSpamEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_BLOCK_SPAM, enabled).apply()

    fun isNotifyOnBlockEnabled(): Boolean =
        prefs.getBoolean(KEY_NOTIFY_ON_BLOCK, true)

    fun setNotifyOnBlockEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_NOTIFY_ON_BLOCK, enabled).apply()

    companion object {
        private const val PREFS_NAME = "scudo_chiamate_prefs"
        const val KEY_BLOCK_FOREIGN = "block_foreign"
        const val KEY_BLOCK_SPAM    = "block_spam"
        const val KEY_NOTIFY_ON_BLOCK = "notify_on_block"
    }
}
