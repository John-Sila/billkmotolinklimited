package com.billkmotolink.ltd.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.billkmotolink.ltd.R
import com.billkmotolink.ltd.ui.Battery

class BatteryAdapter(private val batteryList: List<Battery>) :
    RecyclerView.Adapter<BatteryAdapter.BatteryViewHolder>() {

    inner class BatteryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvBatteryName: TextView = itemView.findViewById(R.id.tvBatteryName)
        val tvLocation: TextView = itemView.findViewById(R.id.tvLocation)
        val tvBike: TextView = itemView.findViewById(R.id.tvBike)
        val tvRider: TextView = itemView.findViewById(R.id.tvRider)
        val tvOffTime: TextView = itemView.findViewById(R.id.tvOffTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BatteryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_battery, parent, false)
        return BatteryViewHolder(view)
    }

    override fun onBindViewHolder(holder: BatteryViewHolder, position: Int) {
        val battery = batteryList[position]
        holder.tvBatteryName.text = battery.batteryName
        holder.tvLocation.text = battery.batteryLocation
        if (battery.batteryLocation.toString().contains("Billk", ignoreCase = false)) {
            holder.tvLocation.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_light))
        }

        holder.tvBike.text = battery.assignedBike
        holder.tvRider.text = battery.assignedRider

        val offTime = battery.offTime?.toDate()
        if (offTime != null) {
            val now = System.currentTimeMillis()
            val diffMillis = now - offTime.time

            val minutes = (diffMillis / (1000 * 60)) % 60
            val hours = (diffMillis / (1000 * 60 * 60)) % 24
            val days = (diffMillis / (1000 * 60 * 60 * 24)) % 30
            val months = (diffMillis / (1000L * 60 * 60 * 24 * 30)) % 12
            val years = (diffMillis / (1000L * 60 * 60 * 24 * 365))

            val timeAgo = when {
                years > 0 -> "${years}y ${months}m ago"
                months > 0 -> "${months}m ${days}d ago"
                days > 0 -> "${days}d ${hours}h ago"
                hours > 0 -> "${hours}h ${minutes}m ago"
                minutes > 0 -> "${minutes}m ago"
                else -> "Just now"
            }

            holder.tvOffTime.text = timeAgo
            if (days > 0 || years > 0) {
                holder.tvOffTime.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light)) // Correct reference to built-in color
            }
            if (years.toInt() == 0 && days.toInt() == 0 && hours < 3) {
                holder.tvOffTime.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_blue_bright)) // Correct reference to built-in color
            }
        } else {
            holder.tvOffTime.text = "N/A"
        }
    }

    override fun getItemCount(): Int = batteryList.size
}
