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
