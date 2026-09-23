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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            == PackageManager.PERMISSION_GRANTED)
