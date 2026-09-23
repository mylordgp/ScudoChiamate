package it.scudochiamate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.BlockedNumberContract
import android.provider.CallLog
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import it.scudochiamate.databinding.ActivityCallLogBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class CallLogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCallLogBinding
    private lateinit var adapter: CallLogAdapter
    private lateinit var userBlacklist: UserBlacklist
    private lateinit var whitelist: WhitelistManager

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadCallLog()
        else Toast.makeText(this, getString(R.string.call_log_permission_denied), Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.call_log_title)
        userBlacklist = UserBlacklist(this)
        whitelist = WhitelistManager(this)
        adapter = CallLogAdapter(
            onToggleBlock = { entry, block ->
                if (block) userBlacklist.addNumber(entry.number)
                else userBlacklist.removeNumber(entry.number)
            },
            onWhitelist = { entry ->
                userBlacklist.removeNumber(entry.number)
                whitelist.addAllowedNumber(entry.number)
                Toast.makeText(this, "Numero aggiunto alla whitelist", Toast.LENGTH_SHORT).show()
                loadCallLog()
            }
        )
        binding.rvCallLog.layoutManager = LinearLayoutManager(this)
        binding.rvCallLog.adapter = adapter
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            loadCallLog()
        } else {
            requestPermission.launch(Manifest.permission.READ_CALL_LOG)
        }
    }

    private fun loadCallLog() {
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                val blocked = userBlacklist.getBlockedNumbers()
                val system = readSystemBlockedNumbers()
                readCallLog(blocked, system)
            }
            adapter.submitList(entries)
        }
    }

    private fun readSystemBlockedNumbers(): Set<String> {
        val result = mutableSetOf<String>()
        try {
            contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                null, null, null
            )?.use {
                val col = it.getColumnIndex(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
                while (it.moveToNext()) {
                    val n = it.getString(col) ?: continue
                    result.add(n.replace(Regex("[\\s\\-().]+"), ""))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return result
    }

    private fun readCallLog(blocked: Set<String>, system: Set<String>): List<CallLogEntry> {
        val entries = mutableListOf<CallLogEntry>()
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        try {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE, CallLog.Calls.DATE),
                null, null, "${CallLog.Calls.DATE} DESC"
            )?.use {
                val ni = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val mi = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val ti = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val di = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
                while (it.moveToNext() && entries.size < 200) {
                    val number = it.getString(ni) ?: continue
                    val callType = it.getInt(ti)
                    if (callType == CallLog.Calls.OUTGOING_TYPE) continue
                    val norm = UserBlacklist.normalize(number)
                    entries.add(CallLogEntry(
                        number = number,
                        name = it.getString(mi) ?: "",
                        type = callType,
                        date = fmt.format(Date(it.getLong(di))),
                        blocked = blocked.contains(norm) || system.contains(norm)
                    ))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return entries
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
