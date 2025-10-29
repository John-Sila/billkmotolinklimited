package com.billkmotolink.ltd.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.billkmotolink.ltd.R


class CashflowAdapter : RecyclerView.Adapter<CashflowAdapter.CashFlowViewHolder>(), Filterable {
    private var originalCashFlows = mutableListOf<CashFlow>()
    private var filteredCashFlows = mutableListOf<CashFlow>()
    class CashFlowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.textMessage)
        val timeText: TextView = itemView.findViewById(R.id.textTime)
        val transactionText: TextView = itemView.findViewById(R.id.transactionID)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CashFlowViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cash_flow, parent, false)
        return CashFlowViewHolder(view)
    }

    override fun onBindViewHolder(holder: CashFlowViewHolder, position: Int) {
        val item = filteredCashFlows[position]
        holder.messageText.text = item.message
        holder.timeText.text = item.time
        holder.transactionText.text = item.identity

        val color = if (item.isIncremental) {
            ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_dark)
        } else {
            ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark)
        }
        holder.messageText.setTextColor(color)

        holder.transactionText.setOnLongClickListener {
            val context = it.context
            copyToClipboard(context, item.identity)
            vibrate(context)
            true
        }
    }

    override fun getItemCount(): Int = filteredCashFlows.size

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filteredList = if (constraint.isNullOrBlank()) {
                    originalCashFlows
                } else {
                    originalCashFlows.filter { flow ->
                        flow.identity.contains(constraint, ignoreCase = true)
                    }
                }
                return FilterResults().apply {
                    values = filteredList
                    count = filteredList.size
                }
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredCashFlows = results?.values as? MutableList<CashFlow> ?: mutableListOf()
                notifyDataSetChanged()
            }
        }
    }

    fun updateList(newList: List<CashFlow>) {
        originalCashFlows.clear()
        originalCashFlows.addAll(newList)
        filteredCashFlows.clear()
        filteredCashFlows.addAll(newList)
        notifyDataSetChanged()
    }
    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Transaction ID", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Transaction ID copied", Toast.LENGTH_SHORT).show()
    }

    private fun vibrate(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }
}