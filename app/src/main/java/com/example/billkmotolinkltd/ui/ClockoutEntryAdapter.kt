package com.example.billkmotolinkltd.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R
import java.text.SimpleDateFormat
import java.util.*

class ClockoutEntryAdapter(private val entries: MutableList<ClockoutEntry>) :
    RecyclerView.Adapter<ClockoutEntryAdapter.EntryViewHolder>() {
    fun updateEntries(newEntries: List<ClockoutEntry>) {
        entries.clear()
        entries.addAll(newEntries)
        notifyDataSetChanged()
    }
    class EntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val date: TextView = itemView.findViewById(R.id.DRDate)
        val net: TextView = itemView.findViewById(R.id.DRNetIncome)
        val gross: TextView = itemView.findViewById(R.id.DRGrossIncome)
        val iab: TextView = itemView.findViewById(R.id.DRIABal)
        val ci_mileage: TextView = itemView.findViewById(R.id.DRClockinMileage)
        val co_mileage: TextView = itemView.findViewById(R.id.DRClockoutMileage)
        val mileage_diff: TextView = itemView.findViewById(R.id.DRMileageDiff)
        val iad: TextView = itemView.findViewById(R.id.DRIADifference)
        val et: TextView = itemView.findViewById(R.id.DRElapsedTime)
        val postTime: TextView = itemView.findViewById(R.id.DRPostTime)

        val expensesContainer: LinearLayout = itemView.findViewById(R.id.expensesContainer)
        val expensesTitle: TextView = itemView.findViewById(R.id.expensesTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clockout_entry, parent, false)
        return EntryViewHolder(view)
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        val entry = entries[position]
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy, HH:mm:ss", Locale.getDefault())

        holder.date.text = entry.date
        holder.net.text = "Ksh.${String.format(" %,.2f", entry.netIncome)}"
        holder.gross.text = "Ksh.${String.format(" %,.2f", entry.grossIncome)}"
        holder.iab.text = "Ksh.${String.format(" %,.2f", entry.inAppBal)}"
        holder.iad.text = "Ksh.${String.format(" %,.2f", entry.inAppDiff)}"
        holder.ci_mileage.text = "${entry.clockinMileage} KMs"
        holder.co_mileage.text = "${entry.clockoutMileage} KMs"
        holder.mileage_diff.text = "${entry.mileageDifference} KMs"
        holder.et.text = entry.elapsedTime
        holder.postTime.text = entry.postedAt?.toDate()?.let { dateFormatter.format(it) } ?: "Unknown"

        // Clear any previous expense views
        holder.expensesContainer.removeAllViews()

        // If there are expenses, add them as rows
        if (entry.expenses.isNotEmpty()) {
            holder.expensesTitle.visibility = View.VISIBLE
            for ((label, value) in entry.expenses) {
                val expenseView = TextView(holder.itemView.context)
                expenseView.text = "$label: Ksh. ${String.format("%,.2f", value)}"
                expenseView.setPadding(0, 4, 0, 0)
                holder.expensesContainer.addView(expenseView)
            }
        } else {
            holder.expensesTitle.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = entries.size
}
