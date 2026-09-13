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
