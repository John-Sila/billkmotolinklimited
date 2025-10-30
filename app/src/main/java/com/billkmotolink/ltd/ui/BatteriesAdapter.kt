package com.billkmotolink.ltd.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.billkmotolink.ltd.R
import java.text.SimpleDateFormat
import java.util.*

class BatteriesAdapter(
    private val currentUserName: String,
    private var list: MutableList<BatteryModel> = mutableListOf(),
    private val onShowTraces: (BatteryModel) -> Unit
) : RecyclerView.Adapter<BatteriesAdapter.VH>() {

    fun updateData(newList: List<BatteryModel>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.battery_item, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount(): Int = list.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tvBatteryName)
        private val detailsLayout: LinearLayout = view.findViewById(R.id.detailsLayout)
        private val tvAssignedBike: TextView = view.findViewById(R.id.tvAssignedBike)
        private val tvAssignedRider: TextView = view.findViewById(R.id.tvAssignedRider)
        private val tvLocation: TextView = view.findViewById(R.id.tvBatteryLocation)
        private val tvOffTime: TextView = view.findViewById(R.id.tvOffTime)
        private val btnMoreInfo: Button = view.findViewById(R.id.btnMoreInfo)

        fun bind(item: BatteryModel) {
            tvName.text = item.batteryName
            tvAssignedBike.text = item.assignedBike
            tvAssignedRider.text = if (item.assignedRider.isNullOrBlank()) "Unassigned" else item.assignedRider
            tvLocation.text = item.batteryLocation

            // offTime formatting
            val offTimeStr = item.offTime?.toDate()?.let { date ->
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date)
            } ?: "N/A"
            tvOffTime.text = offTimeStr

            // color logic
            val ctx = itemView.context
            when {
                item.assignedRider.equals(currentUserName, ignoreCase = true) -> {
                    tvName.setTextColor(ContextCompat.getColor(ctx, android.R.color.holo_green_dark))
                }
                !item.assignedRider.isNullOrBlank() && item.assignedRider != "None" -> {
                    tvName.setTextColor(ContextCompat.getColor(ctx, android.R.color.holo_red_dark))
                }
            }

            // toggle details when clicking the card root or name
            itemView.setOnClickListener {
                detailsLayout.visibility = if (detailsLayout.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }

            btnMoreInfo.setOnClickListener { onShowTraces(item) }
        }
    }
}
