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
            b.tvNumber.text = if (entry.name != null) "\${entry.name}\n\${entry.number}"
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
