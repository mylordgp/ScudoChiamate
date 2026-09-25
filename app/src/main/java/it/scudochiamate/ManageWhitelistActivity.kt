package it.scudochiamate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout

/**
 * Schermata gestione liste numeri.
 * Tab 1 — Prefissi Paese consentiti (es. +44, +33)
 * Tab 2 — Numeri specifici consentiti
 * Tab 3 — Numeri bloccati dall'utente
 */
class ManageWhitelistActivity : AppCompatActivity() {

    private lateinit var whitelist: WhitelistManager
    private lateinit var blacklist: UserBlacklist
    private lateinit var rvList: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var tabs: TabLayout

    private var currentTab = TAB_PREFIXES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_whitelist)

        whitelist = WhitelistManager(this)
        blacklist = UserBlacklist(this)
        rvList   = findViewById(R.id.rvWhitelist)
        tvEmpty  = findViewById(R.id.tvWhitelistEmpty)
        fabAdd   = findViewById(R.id.fabAdd)
        tabs     = findViewById(R.id.tabsWhitelist)

        supportActionBar?.apply {
            title = getString(R.string.whitelist_title)
            setDisplayHomeAsUpEnabled(true)
        }

        rvList.layoutManager = LinearLayoutManager(this)

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                refreshList()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        fabAdd.setOnClickListener { showAddDialog() }

        refreshList()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun refreshList() {
        val items = when (currentTab) {
            TAB_PREFIXES -> whitelist.getAllowedPrefixes()
            TAB_NUMBERS  -> whitelist.getAllowedNumbers()
            else         -> blacklist.getBlockedNumbers()
        }.toList().sorted()

        // il FAB prende il colore della lista: verde consentiti, rosso bloccati
        fabAdd.backgroundTintList = ContextCompat.getColorStateList(this, colorForTab())

        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val label: (String) -> String = { item ->
            val name = nameFor(item)
            if (name.isNotBlank()) "$name\n$item" else item
        }
        rvList.adapter = WhitelistItemAdapter(
            items, label, colorForTab(),
            // solo i numeri hanno un nome modificabile
            onClick = if (currentTab != TAB_PREFIXES) { item -> showEditNameDialog(item) } else null
        ) { item ->
            when (currentTab) {
                TAB_PREFIXES -> whitelist.removeAllowedPrefix(item)
                TAB_NUMBERS  -> whitelist.removeAllowedNumber(item)
                else         -> blacklist.removeNumber(item)
            }
            refreshList()
        }
    }

    @ColorRes
    private fun colorForTab(): Int =
        if (currentTab == TAB_BLOCKED) R.color.red_spam else R.color.green_active

    private fun nameFor(number: String): String = when (currentTab) {
        TAB_NUMBERS -> whitelist.getNameFor(number)
        TAB_BLOCKED -> blacklist.getNameFor(number)
        else        -> ""
    }

    private fun showEditNameDialog(number: String) {
        val input = EditText(this).apply {
            hint = getString(R.string.whitelist_name_hint)
            setText(nameFor(number))
            setSelection(text.length)
            setPadding(48, 24, 48, 24)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.whitelist_edit_name))
            .setMessage(number)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                if (currentTab == TAB_BLOCKED) blacklist.setNameFor(number, input.text.toString())
                else                           whitelist.setNameFor(number, input.text.toString())
                refreshList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddDialog() {
        val isPrefixTab = currentTab == TAB_PREFIXES
        val hint = if (isPrefixTab)
            getString(R.string.whitelist_prefix_hint)
        else
            getString(R.string.whitelist_number_hint)
        val title = when (currentTab) {
            TAB_PREFIXES -> getString(R.string.whitelist_add_prefix)
            TAB_NUMBERS  -> getString(R.string.whitelist_add_number)
            else         -> getString(R.string.blocked_add_number)
        }

        val input = EditText(this).apply {
            this.hint = hint
        }
        val nameInput = EditText(this).apply {
            this.hint = getString(R.string.whitelist_name_hint)
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            addView(input)
            if (!isPrefixTab) addView(nameInput)
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(form)
            .setPositiveButton(R.string.save) { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotBlank()) {
                    // senza nome inserito, usa quello già noto nell'altra lista
                    val name = nameInput.text.toString().ifBlank {
                        blacklist.getNameFor(value).ifBlank { whitelist.getNameFor(value) }
                    }
                    // un numero non può stare in entrambe le liste
                    when (currentTab) {
                        TAB_PREFIXES -> whitelist.addAllowedPrefix(value)
                        TAB_NUMBERS  -> {
                            blacklist.removeNumber(value)
                            whitelist.addAllowedNumber(value, name)
                        }
                        else -> {
                            whitelist.removeAllowedNumber(value)
                            blacklist.addNumber(value, name)
                        }
                    }
                    refreshList()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        private const val TAB_PREFIXES = 0
        private const val TAB_NUMBERS  = 1
        private const val TAB_BLOCKED  = 2
    }
}

/** Adapter semplice per lista whitelist con pulsante eliminazione. */
class WhitelistItemAdapter(
    private val items: List<String>,
    private val label: (String) -> String = { it },
    @ColorRes private val textColor: Int = R.color.green_active,
    private val onClick: ((String) -> Unit)? = null,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<WhitelistItemAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvValue: TextView  = view.findViewById(R.id.tvWhitelistValue)
        val btnDel: ImageButton = view.findViewById(R.id.btnWhitelistDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_whitelist, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvValue.text = label(item)
        holder.tvValue.setTextColor(ContextCompat.getColor(holder.itemView.context, textColor))
        holder.btnDel.setOnClickListener { onDelete(item) }
        holder.itemView.setOnClickListener(onClick?.let { click -> View.OnClickListener { click(item) } })
    }

    override fun getItemCount() = items.size
}
