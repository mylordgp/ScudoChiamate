package it.scudochiamate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout

/**
 * Schermata gestione whitelist.
 * Tab 1 — Prefissi Paese consentiti (es. +44, +33)
 * Tab 2 — Numeri specifici consentiti
 */
class ManageWhitelistActivity : AppCompatActivity() {

    private lateinit var whitelist: WhitelistManager
    private lateinit var rvList: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var tabs: TabLayout

    private var currentTab = 0  // 0 = prefissi, 1 = numeri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_whitelist)

        whitelist = WhitelistManager(this)
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
        val items = if (currentTab == 0)
            whitelist.getAllowedPrefixes().toList().sorted()
        else
            whitelist.getAllowedNumbers().toList().sorted()

        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val label: (String) -> String = { item ->
            val name = if (currentTab == 1) whitelist.getNameFor(item) else ""
            if (name.isNotBlank()) "$name\n$item" else item
        }
        rvList.adapter = WhitelistItemAdapter(
            items, label,
            // solo i numeri hanno un nome modificabile
            onClick = if (currentTab == 1) { item -> showEditNameDialog(item) } else null
        ) { item ->
            if (currentTab == 0) whitelist.removeAllowedPrefix(item)
            else                 whitelist.removeAllowedNumber(item)
            refreshList()
        }
    }

    private fun showEditNameDialog(number: String) {
        val input = EditText(this).apply {
            hint = getString(R.string.whitelist_name_hint)
            setText(whitelist.getNameFor(number))
            setSelection(text.length)
            setPadding(48, 24, 48, 24)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.whitelist_edit_name))
            .setMessage(number)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                whitelist.setNameFor(number, input.text.toString())
                refreshList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddDialog() {
        val isPrefixTab = currentTab == 0
        val hint = if (isPrefixTab)
            getString(R.string.whitelist_prefix_hint)
        else
            getString(R.string.whitelist_number_hint)
        val title = if (isPrefixTab)
            getString(R.string.whitelist_add_prefix)
        else
            getString(R.string.whitelist_add_number)

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
                    if (isPrefixTab) whitelist.addAllowedPrefix(value)
                    else             whitelist.addAllowedNumber(value, nameInput.text.toString())
                    refreshList()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

/** Adapter semplice per lista whitelist con pulsante eliminazione. */
class WhitelistItemAdapter(
    private val items: List<String>,
    private val label: (String) -> String = { it },
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
        holder.btnDel.setOnClickListener { onDelete(item) }
        holder.itemView.setOnClickListener(onClick?.let { click -> View.OnClickListener { click(item) } })
    }

    override fun getItemCount() = items.size
}
