package com.billkmotolink.ltd.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.billkmotolink.ltd.R

class DevWeeksAdapter(initialWeeks: List<DevWeek> = emptyList()) :
    RecyclerView.Adapter<DevWeeksAdapter.WeekViewHolder>() {

    private val weeks = mutableListOf<DevWeek>().apply { addAll(initialWeeks) }
    private val expandedWeeks = mutableSetOf<String>() // Track expanded/collapsed states

    inner class WeekViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val weekTitle: TextView = view.findViewById(R.id.week_title)
        val usersRecycler: RecyclerView = view.findViewById(R.id.users_recycler_view)
        private val usersAdapter = DevUsersAdapter() // Initialize once

        init {
            usersRecycler.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = usersAdapter
            }

            weekTitle.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val week = weeks[position]
                    if (week.weekName in expandedWeeks) {
                        expandedWeeks.remove(week.weekName)
                        usersRecycler.visibility = View.GONE
                    } else {
                        expandedWeeks.add(week.weekName)
                        usersRecycler.visibility = View.VISIBLE
                    }
                }
            }
        }

        fun bind(week: DevWeek) {
            weekTitle.text = week.weekName
            usersAdapter.submitList(week.users)
            usersRecycler.visibility =
                if (week.weekName in expandedWeeks) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.dev_week, parent, false)
        return WeekViewHolder(view)
    }

    override fun onBindViewHolder(holder: WeekViewHolder, position: Int) {
        holder.bind(weeks[position])
    }

    override fun getItemCount(): Int = weeks.size

    fun submitList(newList: List<DevWeek>) {
        val expandedWeekNames = expandedWeeks.toSet()

        weeks.clear()
        weeks.addAll(newList)

        // Restore expanded states for existing weeks
        expandedWeeks.clear()
        expandedWeeks.addAll(expandedWeekNames.intersect(newList.map { it.weekName }))

        notifyDataSetChanged()
    }
}