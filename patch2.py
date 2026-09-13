#!/usr/bin/env python3
"""
patch2.py — Scudo Chiamate v1.2
Aggiunge:
  • Aggiornamento spam automatico (fonte: community GitHub, nessuna manutenzione)
  • Blocco numeri dalla cronologia chiamate
Sovrascrive i file precedenti se esistono.
Eseguire dalla ROOT del repo: python3 patch2.py
"""

import os, subprocess
from pathlib import Path

BASE = Path(".")

def write(rel, content):
    p = BASE / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8")
    print(f"  ✅ {rel}")

# ─────────────────────────────────────────────
# NUOVI FILE KOTLIN
# ─────────────────────────────────────────────

write("app/src/main/java/it/scudochiamate/UserBlacklist.kt", """\
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

    private fun normalize(raw: String): String =
        raw.replace(Regex("[\\\\s\\\\-().]+"), "")

    companion object {
        private const val PREFS_NAME = "user_blacklist"
        private const val KEY_NUMBERS = "blocked_numbers"
    }
}
""")

write("app/src/main/java/it/scudochiamate/DynamicSpamList.kt", """\
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
                line.split(",", ";", "\\t").firstOrNull()?.trim()
            }
            .map { normalize(it) }
            .filter { it.length >= 5 }
            .toSet()
    }

    private fun normalize(raw: String): String =
        raw.replace(Regex("[\\\\s\\\\-().+]+"), "")

    companion object {
        private const val PREFS_NAME  = "dynamic_spam_list"
        private const val KEY_NUMBERS = "numbers"
        private const val KEY_UPDATED = "last_updated"
    }
}
""")

write("app/src/main/java/it/scudochiamate/SpamListUpdater.kt", """\
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
""")

write("app/src/main/java/it/scudochiamate/CallLogEntry.kt", """\
package it.scudochiamate

data class CallLogEntry(
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    var blocked: Boolean
)
""")

write("app/src/main/java/it/scudochiamate/CallLogAdapter.kt", """\
package it.scudochiamate

import android.content.res.ColorStateList
import android.provider.CallLog
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import it.scudochiamate.databinding.ItemCallLogBinding
import java.text.SimpleDateFormat
import java.util.*

class CallLogAdapter(
    private val onToggleBlock: (CallLogEntry, Boolean) -> Unit
) : RecyclerView.Adapter<CallLogAdapter.VH>() {

    private val items = mutableListOf<CallLogEntry>()

    fun submitList(list: List<CallLogEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemCallLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class VH(private val b: ItemCallLogBinding) : RecyclerView.ViewHolder(b.root) {

        private val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.ITALY)

        fun bind(entry: CallLogEntry) {
            b.tvNumber.text = if (entry.name != null) "\${entry.name}\\n\${entry.number}"
                              else entry.number
            b.tvDate.text = typeIcon(entry.type) + fmt.format(Date(entry.date))

            applyBlockStyle(entry.blocked)

            b.btnBlock.setOnClickListener {
                val newBlocked = !entry.blocked
                entry.blocked = newBlocked
                onToggleBlock(entry, newBlocked)
                applyBlockStyle(newBlocked)
            }
        }

        private fun typeIcon(type: Int) = when (type) {
            CallLog.Calls.INCOMING_TYPE -> "📞 "
            CallLog.Calls.OUTGOING_TYPE -> "📤 "
            CallLog.Calls.MISSED_TYPE   -> "📵 "
            else                         -> "📋 "
        }

        private fun applyBlockStyle(blocked: Boolean) {
            val ctx = b.root.context
            b.btnBlock.text = ctx.getString(
                if (blocked) R.string.call_log_unblock else R.string.call_log_block
            )
            b.btnBlock.backgroundTintList = ColorStateList.valueOf(
                ctx.getColor(if (blocked) R.color.red_inactive else R.color.primary)
            )
        }
    }
}
""")

write("app/src/main/java/it/scudochiamate/CallLogActivity.kt", """\
package it.scudochiamate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CallLog
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import it.scudochiamate.databinding.ActivityCallLogBinding

/**
 * Mostra la cronologia chiamate recenti e permette di bloccare/sbloccare
 * singoli numeri nella blacklist personale.
 */
class CallLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallLogBinding
    private lateinit var blacklist: UserBlacklist
    private lateinit var adapter: CallLogAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadCallLog()
        else Toast.makeText(
            this, getString(R.string.call_log_permission_denied), Toast.LENGTH_LONG
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.call_log_title)

        blacklist = UserBlacklist(this)
        adapter = CallLogAdapter { entry, blocked ->
            if (blocked) blacklist.addNumber(entry.number)
            else blacklist.removeNumber(entry.number)
        }
        binding.rvCallLog.layoutManager = LinearLayoutManager(this)
        binding.rvCallLog.adapter = adapter

        checkPermissionAndLoad()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun checkPermissionAndLoad() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            == PackageManager.PERMISSION_GRANTED
        ) {
            loadCallLog()
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }
    }

    private fun loadCallLog() {
        val calls = mutableListOf<CallLogEntry>()
        val seen  = mutableSetOf<String>()

        contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE
            ),
            null, null,
            "\${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val numIdx  = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)

            while (cursor.moveToNext() && calls.size < 200) {
                val number = cursor.getString(numIdx) ?: continue
                if (number in seen) continue
                seen.add(number)
                calls.add(
                    CallLogEntry(
                        number  = number,
                        name    = cursor.getString(nameIdx)?.takeIf { it.isNotEmpty() },
                        type    = cursor.getInt(typeIdx),
                        date    = cursor.getLong(dateIdx),
                        blocked = blacklist.isBlocked(number)
                    )
                )
            }
        }
        adapter.submitList(calls)
    }
}
""")

# ─────────────────────────────────────────────
# FILE MODIFICATI
# ─────────────────────────────────────────────

write("app/src/main/java/it/scudochiamate/SpamCallScreeningService.kt", """\
package it.scudochiamate

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import it.scudochiamate.database.AppDatabase
import it.scudochiamate.database.BlockedCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

/**
 * SpamCallScreeningService — cuore dell'app.
 *
 * Ordine di priorità (dalla più alta alla più bassa):
 *
 *   0. USER BLACKLIST — numero bloccato manualmente dall'utente
 *   1. WHITELIST      — numero/prefisso in whitelist → sempre consentito
 *   2. TIME BLOCK     — fascia oraria di silenzio → blocca
 *   3. FOREIGN        — prefisso non italiano → blocca (se toggle attivo)
 *   4. SPAM           — spam statico + lista comunitaria → blocca (se toggle attivo)
 *   5. DEFAULT        — lascia passare
 */
class SpamCallScreeningService : CallScreeningService() {

    private val tag   = "ScudoChiamate"
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val rawNumber = callDetails.handle?.schemeSpecificPart ?: ""

        val settings    = SettingsManager(this)
        val whitelist   = WhitelistManager(this)
        val timeMgr     = TimeBlockManager(this)
        val userBl      = UserBlacklist(this)
        val dynamicSpam = DynamicSpamList(this)

        Log.d(tag, "Chiamata in entrata da: \$rawNumber")

        // 0. USER BLACKLIST → blocca sempre
        if (userBl.isBlocked(rawNumber)) {
            Log.i(tag, "Blacklist utente — bloccata: \$rawNumber")
            onCallBlocked(rawNumber, BlockReason.USER_BLACKLIST)
            respondToCall(callDetails, buildBlockResponse())
            return
        }

        // 1. WHITELIST → passa sempre
        if (whitelist.isWhitelisted(rawNumber)) {
            Log.d(tag, "Whitelist — consentita: \$rawNumber")
            respondToCall(callDetails, buildAllowResponse())
            return
        }

        // 2. FASCIA ORARIA → blocca tutto
        if (timeMgr.isCurrentlyInBlockPeriod()) {
            Log.i(tag, "Time-block attivo — bloccata: \$rawNumber")
            onCallBlocked(rawNumber, BlockReason.TIME_BLOCK)
            respondToCall(callDetails, buildBlockResponse())
            return
        }

        // 3–4. Filtri prefisso/spam
        val blockReason: BlockReason? = when {
            settings.isBlockForeignEnabled() && SpamChecker.isForeignNumber(rawNumber) ->
                BlockReason.FOREIGN_PREFIX
            settings.isBlockSpamEnabled() &&
                (SpamChecker.isKnownSpam(rawNumber) || dynamicSpam.isSpam(rawNumber)) ->
                BlockReason.KNOWN_SPAM
            else -> null
        }

        if (blockReason != null) {
            Log.i(tag, "BLOCCATA: \$rawNumber (motivo: \$blockReason)")
            onCallBlocked(rawNumber, blockReason)
            respondToCall(callDetails, buildBlockResponse())
        } else {
            Log.d(tag, "Consentita: \$rawNumber")
            respondToCall(callDetails, buildAllowResponse())
        }
    }

    // -------------------------------------------------------------------------
    // Azioni al blocco
    // -------------------------------------------------------------------------

    private fun onCallBlocked(number: String, reason: BlockReason) {
        NotificationHelper(this).showBlockedCallNotification(number, reason)
        scope.launch {
            AppDatabase.getInstance(applicationContext)
                .blockedCallDao()
                .insert(BlockedCall(phoneNumber = number, reason = reason.name, timestamp = Date()))
        }
    }

    // -------------------------------------------------------------------------
    // Risposte CallScreening
    // -------------------------------------------------------------------------

    private fun buildBlockResponse(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSilenceCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(true)
            .build()

    private fun buildAllowResponse(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(false)
            .build()
}

/** Motivo del blocco — usato per notifiche, log e statistiche. */
enum class BlockReason {
    FOREIGN_PREFIX,
    KNOWN_SPAM,
    TIME_BLOCK,
    SYSTEM_IMPORT,   // importato dalla blacklist di sistema
    USER_BLACKLIST   // bloccato manualmente dall'utente
}
""")

write("app/src/main/java/it/scudochiamate/NotificationHelper.kt", """\
package it.scudochiamate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * Gestisce le notifiche di chiamate bloccate.
 */
class NotificationHelper(private val context: Context) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showBlockedCallNotification(number: String, reason: BlockReason) {
        val settings = SettingsManager(context)
        if (!settings.isNotifyOnBlockEnabled()) return

        val title = context.getString(R.string.notif_blocked_title)
        val reasonStr = when (reason) {
            BlockReason.FOREIGN_PREFIX -> context.getString(R.string.reason_foreign)
            BlockReason.KNOWN_SPAM     -> context.getString(R.string.reason_spam)
            BlockReason.TIME_BLOCK     -> context.getString(R.string.reason_time_block)
            BlockReason.SYSTEM_IMPORT  -> context.getString(R.string.reason_system)
            BlockReason.USER_BLACKLIST -> context.getString(R.string.reason_user)
        }
        val body = context.getString(R.string.notif_blocked_body, number, reasonStr)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notif)
    }

    companion object {
        const val CHANNEL_ID = "blocked_calls_channel"
    }
}
""")

write("app/src/main/java/it/scudochiamate/MainActivity.kt", """\
package it.scudochiamate

import android.app.TimePickerDialog
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import it.scudochiamate.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsManager
    private lateinit var timeMgr: TimeBlockManager
    private val viewModel: MainViewModel by viewModels()

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            updateServiceStatus(active = true)
            Toast.makeText(this, getString(R.string.role_granted), Toast.LENGTH_SHORT).show()
            runSystemImport()
        } else {
            updateServiceStatus(active = false)
            Toast.makeText(this, getString(R.string.role_denied), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = SettingsManager(this)
        timeMgr  = TimeBlockManager(this)

        setupToggles()
        setupTimeBlock()
        setupWhitelistButton()
        setupCallLogButton()
        setupRecyclerView()
        observeViewModel()
        checkServiceRole()

        // Aggiornamento automatico lista spam (primo avvio = download immediato)
        SpamListUpdater.schedule(this)
        if (DynamicSpamList(this).count() == 0) {
            SpamListUpdater.runNow(this)
        }
    }

    override fun onResume() {
        super.onResume()
        updateTimeBlockLabels()
    }

    // -------------------------------------------------------------------------
    // Toggle impostazioni base
    // -------------------------------------------------------------------------
    private fun setupToggles() {
        binding.switchForeign.isChecked = settings.isBlockForeignEnabled()
        binding.switchSpam.isChecked    = settings.isBlockSpamEnabled()
        binding.switchNotify.isChecked  = settings.isNotifyOnBlockEnabled()

        binding.switchForeign.setOnCheckedChangeListener { _, checked ->
            settings.setBlockForeignEnabled(checked)
        }
        binding.switchSpam.setOnCheckedChangeListener { _, checked ->
            settings.setBlockSpamEnabled(checked)
        }
        binding.switchNotify.setOnCheckedChangeListener { _, checked ->
            settings.setNotifyOnBlockEnabled(checked)
        }

        binding.btnActivate.setOnClickListener { requestScreeningRole() }

        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_confirm_title)
                .setMessage(R.string.clear_confirm_msg)
                .setPositiveButton(R.string.confirm) { _, _ -> viewModel.clearHistory() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    // -------------------------------------------------------------------------
    // Fascia oraria di blocco
    // -------------------------------------------------------------------------
    private fun setupTimeBlock() {
        binding.switchTimeBlock.isChecked = timeMgr.isEnabled()
        updateTimeBlockLabels()

        binding.switchTimeBlock.setOnCheckedChangeListener { _, checked ->
            timeMgr.setEnabled(checked)
            binding.timeBlockControls.visibility = if (checked) View.VISIBLE else View.GONE
        }

        binding.timeBlockControls.visibility =
            if (timeMgr.isEnabled()) View.VISIBLE else View.GONE

        binding.btnStartTime.setOnClickListener {
            TimePickerDialog(this,
                { _, h, m -> timeMgr.setStart(h, m); updateTimeBlockLabels() },
                timeMgr.getStartHour(), timeMgr.getStartMinute(), true
            ).show()
        }

        binding.btnEndTime.setOnClickListener {
            TimePickerDialog(this,
                { _, h, m -> timeMgr.setEnd(h, m); updateTimeBlockLabels() },
                timeMgr.getEndHour(), timeMgr.getEndMinute(), true
            ).show()
        }
    }

    private fun updateTimeBlockLabels() {
        binding.btnStartTime.text = getString(R.string.time_from, timeMgr.startLabel())
        binding.btnEndTime.text   = getString(R.string.time_to,   timeMgr.endLabel())
    }

    // -------------------------------------------------------------------------
    // Pulsante gestione whitelist
    // -------------------------------------------------------------------------
    private fun setupWhitelistButton() {
        binding.btnManageWhitelist.setOnClickListener {
            startActivity(Intent(this, ManageWhitelistActivity::class.java))
        }
    }

    // -------------------------------------------------------------------------
    // Pulsante cronologia chiamate
    // -------------------------------------------------------------------------
    private fun setupCallLogButton() {
        binding.btnCallLog.setOnClickListener {
            startActivity(Intent(this, CallLogActivity::class.java))
        }
    }

    // -------------------------------------------------------------------------
    // RecyclerView chiamate bloccate
    // -------------------------------------------------------------------------
    private fun setupRecyclerView() {
        binding.rvBlockedCalls.layoutManager = LinearLayoutManager(this)
    }

    private fun observeViewModel() {
        viewModel.totalCount.observe(this) { count ->
            binding.tvTotalCount.text = count.toString()
            binding.emptyState.visibility = if (count == 0) View.VISIBLE else View.GONE
        }
        viewModel.foreignCount.observe(this) { binding.tvForeignCount.text = it.toString() }
        viewModel.spamCount.observe(this)    { binding.tvSpamCount.text    = it.toString() }
        viewModel.allCalls.observe(this) { calls ->
            binding.rvBlockedCalls.adapter = BlockedCallsAdapter(calls) { viewModel.deleteCall(it) }
        }
    }

    // -------------------------------------------------------------------------
    // Ruolo CallScreening + import automatico
    // -------------------------------------------------------------------------
    private fun checkServiceRole() {
        val rm = getSystemService(RoleManager::class.java)
        val hasRole = rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        updateServiceStatus(hasRole)
        if (hasRole) runSystemImport()
    }

    private fun requestScreeningRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            roleRequestLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        } else {
            Toast.makeText(this, R.string.role_not_available, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateServiceStatus(active: Boolean) {
        binding.tvServiceStatus.text = getString(
            if (active) R.string.service_active else R.string.service_inactive
        )
        val color = getColor(if (active) R.color.green_active else R.color.red_inactive)
        binding.tvServiceStatus.setTextColor(color)
        binding.statusIndicator.setBackgroundColor(color)
        binding.btnActivate.visibility = if (active) View.GONE else View.VISIBLE
    }

    private fun runSystemImport() {
        lifecycleScope.launch {
            when (val result = SystemImporter.importFromSystem(this@MainActivity)) {
                is SystemImporter.ImportResult.Success -> {
                    if (result.imported > 0) {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.import_success, result.imported),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                is SystemImporter.ImportResult.PermissionDenied -> { /* silenzioso */ }
                is SystemImporter.ImportResult.Error -> { /* silenzioso */ }
            }
        }
    }
}
""")

# ─────────────────────────────────────────────
# MANIFEST
# ─────────────────────────────────────────────

write("app/src/main/AndroidManifest.xml", """\
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Permessi necessari -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.READ_CALL_LOG" />
    <uses-permission android:name="android.permission.CALL_PHONE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.READ_BLOCKED_NUMBERS" />

    <application
        android:allowBackup="true"
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@drawable/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.ScudoChiamate">

        <!-- Activity principale -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Activity gestione whitelist -->
        <activity
            android:name=".ManageWhitelistActivity"
            android:exported="false"
            android:parentActivityName=".MainActivity" />

        <!-- Activity cronologia chiamate -->
        <activity
            android:name=".CallLogActivity"
            android:exported="false"
            android:parentActivityName=".MainActivity" />

        <!--
            CallScreeningService — intercetta chiamate PRIMA che il telefono suoni.
            Su MIUI (Poco F3):
            App Telefono → ⋮ → Impostazioni → Blocco chiamate e spam → Scudo Chiamate
        -->
        <service
            android:name=".SpamCallScreeningService"
            android:exported="true"
            android:permission="android.permission.BIND_SCREENING_SERVICE">
            <intent-filter>
                <action android:name="android.telecom.CallScreeningService" />
            </intent-filter>
        </service>

    </application>

</manifest>
""")

# ─────────────────────────────────────────────
# BUILD.GRADLE — aggiunge WorkManager
# ─────────────────────────────────────────────

write("app/build.gradle", """\
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'org.jetbrains.kotlin.kapt'
}

android {
    namespace 'it.scudochiamate'
    compileSdk 34

    defaultConfig {
        applicationId "it.scudochiamate"
        minSdk 29          // Android 10 — minimo per CallScreeningService role
        targetSdk 34
        versionCode 2
        versionName "1.2.0"

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
        debug {
            applicationIdSuffix ".debug"
            debuggable true
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = '1.8'
    }

    buildFeatures {
        viewBinding true
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
    implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.7.0'
    implementation 'androidx.room:room-runtime:2.6.1'
    implementation 'androidx.room:room-ktx:2.6.1'
    kapt 'androidx.room:room-compiler:2.6.1'
    implementation 'androidx.preference:preference-ktx:1.2.1'
    implementation 'androidx.work:work-runtime-ktx:2.9.0'

    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}
""")

# ─────────────────────────────────────────────
# LAYOUT — activity_call_log.xml
# ─────────────────────────────────────────────

write("app/src/main/res/layout/activity_call_log.xml", """\
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/background">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp"
        android:text="@string/call_log_desc"
        android:textSize="13sp"
        android:textColor="@color/text_secondary" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rvCallLog"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:clipToPadding="false"
        android:paddingBottom="16dp" />

</LinearLayout>
""")

# ─────────────────────────────────────────────
# LAYOUT — item_call_log.xml
# ─────────────────────────────────────────────

write("app/src/main/res/layout/item_call_log.xml", """\
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="16dp"
    android:paddingEnd="16dp"
    android:paddingTop="10dp"
    android:paddingBottom="10dp">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/tvNumber"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="14sp"
            android:textColor="@color/text_primary"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tvDate"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="12sp"
            android:textColor="@color/text_secondary" />

    </LinearLayout>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnBlock"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textSize="12sp"
        android:textColor="@android:color/white"
        style="@style/Widget.MaterialComponents.Button" />

</LinearLayout>
""")

# ─────────────────────────────────────────────
# LAYOUT — activity_main.xml (aggiunge card cronologia)
# ─────────────────────────────────────────────

# Leggiamo e inseriamo la card prima della sezione CRONOLOGIA
main_xml = (BASE / "app/src/main/res/layout/activity_main.xml").read_text(encoding="utf-8")

call_log_card = """
        <!-- CARD BLOCCA DA CRONOLOGIA -->
        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            app:cardCornerRadius="12dp"
            app:cardElevation="4dp"
            app:cardBackgroundColor="@color/card_background">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:padding="16dp">

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:orientation="vertical">
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/call_log_card_title"
                        android:textSize="14sp"
                        android:textStyle="bold"
                        android:textColor="@color/text_primary" />
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/call_log_card_desc"
                        android:textSize="12sp"
                        android:textColor="@color/text_secondary" />
                </LinearLayout>

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnCallLog"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/btn_manage"
                    android:textSize="12sp"
                    style="@style/Widget.MaterialComponents.Button.OutlinedButton"
                    app:strokeColor="@color/orange_foreign"
                    app:strokeWidth="1.5dp" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

"""

MARKER = "        <!-- CRONOLOGIA -->"
if MARKER not in main_xml:
    # fallback: cerca stringa alternativa
    MARKER = "        <!-- CRONOLOGIA"

if MARKER in main_xml:
    main_xml = main_xml.replace(MARKER, call_log_card + MARKER)
    (BASE / "app/src/main/res/layout/activity_main.xml").write_text(main_xml, encoding="utf-8")
    print("  ✅ app/src/main/res/layout/activity_main.xml (card cronologia aggiunta)")
else:
    print("  ⚠️  activity_main.xml: marker non trovato, card non inserita (da fare manualmente)")

# ─────────────────────────────────────────────
# STRINGS — aggiunge le nuove stringhe
# ─────────────────────────────────────────────

strings_xml = (BASE / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")

new_strings = """
    <!-- Cronologia chiamate (blocco manuale) -->
    <string name="call_log_card_title">📞 Blocca dalla cronologia</string>
    <string name="call_log_card_desc">Blocca o sblocca numeri dalla tua cronologia chiamate</string>
    <string name="call_log_title">Cronologia chiamate</string>
    <string name="call_log_desc">Tocca Blocca per aggiungere un numero alla tua lista nera personale.</string>
    <string name="call_log_block">Blocca</string>
    <string name="call_log_unblock">Sblocca</string>
    <string name="call_log_permission_denied">Permesso cronologia chiamate non concesso</string>

    <!-- Motivo blocco aggiuntivo -->
    <string name="reason_user">🚫 Bloccato dall\'utente</string>

"""

if "call_log_card_title" not in strings_xml:
    strings_xml = strings_xml.replace("</resources>", new_strings + "</resources>")
    (BASE / "app/src/main/res/values/strings.xml").write_text(strings_xml, encoding="utf-8")
    print("  ✅ app/src/main/res/values/strings.xml (nuove stringhe aggiunte)")
else:
    print("  ℹ️  strings.xml: stringhe già presenti, skip")

# ─────────────────────────────────────────────
# COMMIT & PUSH
# ─────────────────────────────────────────────

print("\n🔄 Commit e push su GitHub...")

cmds = [
    ["git", "add", "-A"],
    ["git", "commit", "-m", "feat: aggiornamento spam automatico (community) + blocco da cronologia\n\nCo-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"],
    ["git", "push"],
]

for cmd in cmds:
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print(f"  ⚠️  {' '.join(cmd)}\n{r.stderr.strip()}")
    else:
        print(f"  ✅ {' '.join(cmd)}")

print("""
╔══════════════════════════════════════════════════════════╗
║  Patch applicata con successo!                          ║
║                                                          ║
║  Vai su GitHub → Actions per monitorare la build.       ║
║  Quando è ✅ scarica l'APK e installa sul telefono.     ║
╚══════════════════════════════════════════════════════════╝
""")
cd /workspaces/ScudoChiamate

