package com.billkmotolink.ltd.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.billkmotolink.ltd.R
import com.billkmotolink.ltd.ui.BudgetItemRow
import com.billkmotolink.ltd.ui.formatIncome

class BudgetAdapter(private val rows: List<BudgetItemRow>) :
    RecyclerView.Adapter<BudgetAdapter.BudgetViewHolder>() {

    inner class BudgetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemName: TextView = itemView.findViewById(R.id.item_name)
        val itemCount: TextView = itemView.findViewById(R.id.item_count)
        val status: TextView = itemView.findViewById(R.id.status)
        val totalCost: TextView = itemView.findViewById(R.id.cost)
        val postedAt: TextView = itemView.findViewById(R.id.posted_at)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_budget_row, parent, false)
        return BudgetViewHolder(view)
    }

    override fun onBindViewHolder(holder: BudgetViewHolder, position: Int) {
        val row = rows[position]
        holder.itemName.text = row.itemsFormatted
        holder.itemCount.text = row.itemCount.toString()
        holder.status.text = row.budgetStatus
        holder.totalCost.text = formatIncome(row.totalCost).toString()

        holder.postedAt.text = row.postedAt.toString()
    }

    override fun getItemCount(): Int = rows.size
}
