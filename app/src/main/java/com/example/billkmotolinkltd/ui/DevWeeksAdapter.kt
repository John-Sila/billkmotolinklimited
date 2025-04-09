package com.example.billkmotolinkltd.ui

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R

class DevWeeksAdapter(private var weeks: List<DevWeek>) : RecyclerView.Adapter<DevWeeksAdapter.WeekViewHolder>() {

    inner class WeekViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val weekTitle: TextView = view.findViewById(R.id.week_title)
        val usersRecycler: RecyclerView = view.findViewById(R.id.users_recycler_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.dev_week, parent, false)
        return WeekViewHolder(view)
    }

    // In DevWeeksAdapter
    override fun onBindViewHolder(holder: WeekViewHolder, position: Int) {
        val week = weeks[position]
        Log.d("Adapter", "Binding week: ${week.weekName} with ${week.users.size} users")
        holder.weekTitle.text = week.weekName
        holder.usersRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = DevUsersAdapter(week.users)
        }
    }

    override fun getItemCount(): Int = weeks.size

    fun submitList(newList: List<DevWeek>) {
        weeks = newList
        notifyDataSetChanged()
    }
}
