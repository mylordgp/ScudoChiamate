package it.scudochiamate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
        adapter = CallLogAdapter { entry, block ->
            if (block) userBlacklist.addNumber(entry.number)
            else userBlacklist.removeNumber(entry.number)
        }
        binding.rvCallLog.layoutManager = LinearLayoutManager(this)
        binding.rvCallLog.adapter = adapter

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            == PackageManager.PERMISSION_GRANTED) {
            loadCallLog()
        } else {
            requestPermission.launch(Manifest.permission.READ_CALL_LOG)
        }
    }

    private fun loadCallLog() {
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) { readCallLog() }
            adapter.submitList(entries)
        }
    }

    private fun readCallLog(): List<CallLogEntry> {
        val entries = mutableListOf<CallLogEntry>()
        val seen = mutableSetOf<String>()
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        try {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE, CallLog.Calls.DATE),
                null, null,
                "${CallLog.Calls.DATE} DESC"
            )
            cursor?.use {
                val numIdx = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val typeIdx = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
                while (it.moveToNext() && entries.size < 200) {
                    val number = it.getString(numIdx) ?: continue
                    if (seen.contains(number)) continue
                    seen.add(number)
                    val dateStr = fmt.format(Date(it.getLong(dateIdx)))
                    entries.add(CallLogEntry(
                        number = number,
                        name = it.getString(nameIdx) ?: "",
                        type = it.getInt(typeIdx),
                        date = dateStr,
                        blocked = userBlacklist.isBlocked(number)
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return entries
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
