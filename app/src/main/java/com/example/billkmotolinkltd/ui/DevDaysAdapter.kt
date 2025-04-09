package com.example.billkmotolinkltd.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.ui.DevDayData
import java.text.NumberFormat


private val dayOrder = mapOf(
    "Monday" to 1,
    "Tuesday" to 2,
    "Wednesday" to 3,
    "Thursday" to 4,
    "Friday" to 5,
    "Saturday" to 6,
    "Sunday" to 7
)

class DevDaysAdapter(private val days: List<DevDayData>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
        const val TYPE_FOOTER = 2
    }
    private val sortedDays = days.sortedBy { dayOrder[it.dayName] ?: Int.MAX_VALUE }

    // ViewHolder classes
    inner class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // ... existing view declarations ...
        val dayText: TextView = view.findViewById(R.id.dayText)
        val netIncome: TextView = view.findViewById(R.id.netIncome)
        val grossIncome: TextView = view.findViewById(R.id.grossIncome)
        val netDeviation: TextView = view.findViewById(R.id.netDeviation)
        val grossDeviation: TextView = view.findViewById(R.id.grossDeviation)
        val netGrossDifference: TextView = view.findViewById(R.id.netGrossDifference)
    }

    inner class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayText: TextView = view.findViewById(R.id.dayText)
        val netIncome: TextView = view.findViewById(R.id.netIncome)
        val grossIncome: TextView = view.findViewById(R.id.grossIncome)
        val netDeviation: TextView = view.findViewById(R.id.netDeviation)
        val grossDeviation: TextView = view.findViewById(R.id.grossDeviation)
        val netGrossDifference: TextView = view.findViewById(R.id.netGrossDifference)
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            position == 0 -> TYPE_HEADER
            position == days.size + 1 -> TYPE_FOOTER
            else -> TYPE_ITEM
        }
    }

    override fun getItemCount() = days.size + 2 // +1 for header, +1 for footer

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.dev_day_header, parent, false)
                object : RecyclerView.ViewHolder(view) {}
            }
            TYPE_FOOTER -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.dev_day_footer, parent, false)
                FooterViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.dev_day, parent, false)
                DayViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DayViewHolder -> {
                val day = sortedDays[position - 1] // Adjust for header
                // ... existing binding code ...
                holder.dayText.text = day.dayName
                val numberFormat = NumberFormat.getInstance()

                holder.netIncome.text = String.format("%,.2f", day.netIncome)
                holder.grossIncome.text = String.format("%,.2f", day.grossIncome)
                holder.netDeviation.text = String.format("%,.2f", day.netDeviation)
                holder.grossDeviation.text = String.format("%,.2f", day.grossDeviation)
                holder.netGrossDifference.text = String.format("%,.2f", day.netGrossDifference)

                // Apply color logic based on the values
                if (day.netIncome < 0) {
                    holder.netIncome.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light))
                } else if (day.netIncome > 5000) {
                    holder.netIncome.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_light))
                }

                if (day.grossIncome < 0) {
                    holder.grossIncome.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light))
                } else if (day.grossIncome > 5000) {
                    holder.grossIncome.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_light))
                }

                if (day.netDeviation < 0) {
                    holder.netDeviation.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light))
                } else if (day.netDeviation > 5000) {
                    holder.netDeviation.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_light))
                }

                if (day.grossDeviation < 0) {
                    holder.grossDeviation.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light))
                } else if (day.grossDeviation > 5000) {
                    holder.grossDeviation.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_light))
                }

                if (day.netGrossDifference < 0) {
                    holder.netGrossDifference.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light))
                } else if (day.netGrossDifference > 5000) {
                    holder.netGrossDifference.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_light))
                }
            }
            is FooterViewHolder -> {
                // Calculate sums
                val sums = days.fold(DevDayData("Total", 0.0, 0.0, 0.0, 0.0, 0.0)) { acc, day ->
                    DevDayData(
                        "Σ m->s",
                        acc.netIncome + day.netIncome,
                        acc.grossIncome + day.grossIncome,
                        acc.netDeviation + day.netDeviation,
                        acc.grossDeviation + day.grossDeviation,
                        acc.netGrossDifference + day.netGrossDifference
                    )
                }

                // Bind sums to footer
                holder.dayText.text = sums.dayName
                holder.dayText.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light))

                holder.netIncome.text = String.format("%,.2f", sums.netIncome)
                holder.netIncome.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light))

                holder.grossIncome.text = String.format("%,.2f", sums.grossIncome)
                holder.grossIncome.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light))

                holder.netDeviation.text = String.format("%,.2f", sums.netDeviation)
                holder.netDeviation.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light))

                holder.grossDeviation.text = String.format("%,.2f", sums.grossDeviation)
                holder.grossDeviation.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light))

                holder.netGrossDifference.text = String.format("%,.2f", sums.netGrossDifference)
                holder.netGrossDifference.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light))

            }
        }
    }
}