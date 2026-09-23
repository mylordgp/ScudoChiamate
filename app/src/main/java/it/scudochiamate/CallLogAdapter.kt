package it.scudochiamate

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.scudochiamate.databinding.ItemCallLogBinding

class CallLogAdapter(
    private val onToggleBlock: (CallLogEntry, Boolean) -> Unit,
    private val onWhitelist: (CallLogEntry) -> Unit = {}
) : ListAdapter<CallLogEntry, CallLogAdapter.VH>(DIFF) {

    inner class VH(val b: ItemCallLogBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCallLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = getItem(position)
        holder.b.tvNumber.text = if (entry.name.isNotBlank()) "${entry.name}\n${entry.number}" else entry.number
        holder.b.tvDate.text = entry.date
        applyBlockStyle(holder, entry.blocked)

        holder.b.btnBlock.setOnClickListener {
            val newBlocked = !entry.blocked
            val updated = entry.copy(blocked = newBlocked)
            onToggleBlock(updated, newBlocked)
            val newList = currentList.toMutableList()
            newList[holder.adapterPosition] = updated
            submitList(newList)
        }

        holder.b.btnWhitelistLog.setOnClickListener {
            onWhitelist(entry)
        }
    }

    private fun applyBlockStyle(holder: VH, blocked: Boolean) {
        val ctx = holder.itemView.context
        holder.b.btnBlock.text = ctx.getString(
            if (blocked) R.string.call_log_unblock else R.string.call_log_block
        )
        holder.b.btnBlock.setBackgroundColor(
            if (blocked) Color.parseColor("#F44336") else Color.parseColor("#4CAF50")
        )
        // mostra il pulsante whitelist solo se il numero è bloccato
        holder.b.btnWhitelistLog.visibility = if (blocked) View.VISIBLE else View.GONE
        holder.b.btnWhitelistLog.setBackgroundColor(Color.parseColor("#2196F3"))
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CallLogEntry>() {
            override fun areItemsTheSame(a: CallLogEntry, b: CallLogEntry) = a.number == b.number
            override fun areContentsTheSame(a: CallLogEntry, b: CallLogEntry) = a == b
        }
    }
}
