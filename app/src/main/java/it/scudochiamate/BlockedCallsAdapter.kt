package it.scudochiamate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import it.scudochiamate.database.BlockedCall
import java.text.SimpleDateFormat
import java.util.Locale

class BlockedCallsAdapter(
    private val calls: List<BlockedCall>,
    private val onDelete: (BlockedCall) -> Unit,
    private val onWhitelist: (BlockedCall) -> Unit,
    private val nameFor: (String) -> String = { "" }
) : RecyclerView.Adapter<BlockedCallsAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY)

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumber: TextView        = view.findViewById(R.id.tvNumber)
        val tvReason: TextView        = view.findViewById(R.id.tvReason)
        val tvDate: TextView          = view.findViewById(R.id.tvDate)
        val btnDelete: ImageButton    = view.findViewById(R.id.btnDelete)
        val btnWhitelist: ImageButton = view.findViewById(R.id.btnWhitelist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blocked_call, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val call = calls[position]
        val name = nameFor(call.phoneNumber)
        holder.tvNumber.text = if (name.isNotBlank()) "$name
${call.phoneNumber}" else call.phoneNumber
        holder.tvReason.text = when (call.reason) {
            "FOREIGN_PREFIX" -> holder.itemView.context.getString(R.string.reason_foreign)
            "KNOWN_SPAM"     -> holder.itemView.context.getString(R.string.reason_spam)
            "TIME_BLOCK"     -> holder.itemView.context.getString(R.string.reason_time_block)
            "SYSTEM_IMPORT"  -> holder.itemView.context.getString(R.string.reason_system)
            else             -> call.reason
        }
        holder.tvDate.text = dateFormat.format(call.timestamp)
        holder.btnDelete.setOnClickListener { onDelete(call) }
        holder.btnWhitelist.setOnClickListener { onWhitelist(call) }
    }

    override fun getItemCount() = calls.size
}
