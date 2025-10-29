package com.billkmotolink.ltd.ui

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.billkmotolink.ltd.R

class TraceUserAdapter(private val userList: List<TraceUser>) : RecyclerView.Adapter<TraceUserAdapter.UserViewHolder>() {

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        val rvDays: RecyclerView = itemView.findViewById(R.id.rvDays)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        Log.d("TraceUserAdapter", "onCreateViewHolder called")
        val view = LayoutInflater.from(parent.context).inflate(R.layout.trace_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        Log.d("TraceUserAdapter", "Binding user at $position with ${userList[position].days.size} days")
        val user = userList[position]

        holder.tvUserName.text = user.userName
        holder.rvDays.layoutManager = LinearLayoutManager(holder.itemView.context)
        holder.rvDays.adapter = TraceDayAdapter(user.days)

        holder.tvUserName.setOnClickListener {
            val dayView = holder.rvDays
            if (dayView.isVisible) {
                dayView.visibility = View.GONE
            } else dayView.visibility = View.VISIBLE
        }
    }

    override fun getItemCount(): Int = userList.size
}
