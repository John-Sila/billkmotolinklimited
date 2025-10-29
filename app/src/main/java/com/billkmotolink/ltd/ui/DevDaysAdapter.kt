package com.billkmotolink.ltd.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.billkmotolink.ltd.R
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

class DevDaysAdapter(initialDays: List<DevDayData> = emptyList()) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
        const val TYPE_FOOTER = 2
    }

    private val days = mutableListOf<DevDayData>().apply { addAll(initialDays) }
    private val sortedDays get() = days.sortedBy { dayOrder[it.dayName] ?: Int.MAX_VALUE }
    private val dayOrder = mapOf(
        "Monday" to 0,
        "Tuesday" to 1,
        "Wednesday" to 2,
        "Thursday" to 3,
        "Friday" to 4,
        "Saturday" to 5,
        "Sunday" to 6
    )

    // ViewHolder classes
    inner class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayText: TextView = view.findViewById(R.id.dayText)
        val netIncome: TextView = view.findViewById(R.id.netIncome)
        val grossIncome: TextView = view.findViewById(R.id.grossIncome)
        val grossDeviation: TextView = view.findViewById(R.id.grossDeviation)
        val netGrossDifference: TextView = view.findViewById(R.id.netGrossDifference)
    }

    inner class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayText: TextView = view.findViewById(R.id.dayTextFooter)
        val netIncome: TextView = view.findViewById(R.id.netIncomeFooter)
        val grossIncome: TextView = view.findViewById(R.id.grossIncomeFooter)
        val grossDeviation: TextView = view.findViewById(R.id.grossDeviationFooter)
        val netGrossDifference: TextView = view.findViewById(R.id.netGrossDifferenceFooter)
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
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.dev_day_header, parent, false)
                object : RecyclerView.ViewHolder(view) {}
            }
            TYPE_FOOTER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.dev_day_footer, parent, false)
                FooterViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.dev_day, parent, false)
                DayViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DayViewHolder -> bindDayViewHolder(holder, position)
            is FooterViewHolder -> bindFooterViewHolder(holder)
        }
    }

    private fun bindDayViewHolder(holder: DayViewHolder, position: Int) {
        val day = sortedDays[position - 1] // Adjust for header
        holder.dayText.text = day.dayName

        val numberFormat = NumberFormat.getInstance().apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 2
        }

        holder.netIncome.text = numberFormat.format(day.netIncome)
        holder.grossIncome.text = numberFormat.format(day.grossIncome)
        holder.grossDeviation.text = numberFormat.format(day.grossDeviation)
        holder.netGrossDifference.text = numberFormat.format(day.netGrossDifference)

        // Apply conditional coloring
        fun TextView.setColorForValue(value: Double) {
            when {
                value < 0 -> setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_light))
                value < 100 -> setTextColor(ContextCompat.getColor(context, android.R.color.holo_blue_bright))
                value > 3500 -> setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_light))
            }
        }


        with(holder) {
            netIncome.setColorForValue(day.netIncome)
            grossIncome.setColorForValue(day.grossIncome)
            grossDeviation.setColorForValue(day.grossDeviation)
            netGrossDifference.setColorForValue(day.netGrossDifference)
        }
    }

    private fun bindFooterViewHolder(holder: FooterViewHolder) {
        val sums = sortedDays.fold(DevDayData("Total", 0.0, 0.0, 0.0, 0.0)) { acc, day ->
            DevDayData(
                "Σ M → S",
                acc.netIncome + day.netIncome,
                acc.grossIncome + day.grossIncome,
                acc.grossDeviation + day.grossDeviation,
                acc.netGrossDifference + day.netGrossDifference
            )
        }

        val redColor = ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light)

        with(holder) {
            dayText.text = sums.dayName
            dayText.setTextColor(redColor)

            netIncome.text = String.format("%,.2f", sums.netIncome)
            netIncome.setTextColor(redColor)

            grossIncome.text = String.format("%,.2f", sums.grossIncome)
            grossIncome.setTextColor(redColor)

            grossDeviation.text = String.format("%,.2f", sums.grossDeviation)
            grossDeviation.setTextColor(redColor)

            netGrossDifference.text = String.format("%,.2f", sums.netGrossDifference)
            netGrossDifference.setTextColor(redColor)
        }
    }

    fun submitList(newDays: List<DevDayData>) {
        days.clear()
        days.addAll(newDays)
        notifyDataSetChanged()
    }
}

