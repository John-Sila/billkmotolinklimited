package com.example.billkmotolinkltd.ui

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.view.LayoutInflater
import com.example.billkmotolinkltd.R
import androidx.core.view.isVisible

class TraceWeekAdapter(private val weekList: List<TraceWeek>) : RecyclerView.Adapter<TraceWeekAdapter.WeekViewHolder>() {

    inner class WeekViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvWeekName: TextView = itemView.findViewById(R.id.tvWeekName)
        val rvUsers: RecyclerView = itemView.findViewById(R.id.rvUsers)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.trace_week, parent, false)
        return WeekViewHolder(view)
    }

    override fun onBindViewHolder(holder: WeekViewHolder, position: Int) {
        val week = weekList[position]
        holder.tvWeekName.text = week.weekName
        holder.rvUsers.layoutManager = LinearLayoutManager(holder.itemView.context)
        holder.rvUsers.adapter = TraceUserAdapter(week.users)

        holder.tvWeekName.setOnClickListener {
            val weekView = holder.rvUsers
            if (weekView.isVisible) {
                weekView.visibility = View.GONE
            } else weekView.visibility = View.VISIBLE
        }
    }

    override fun getItemCount(): Int = weekList.size
}
