package com.example.billkmotolinkltd.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.ui.DevUser

class DevUsersAdapter(private val users: List<DevUser>) : RecyclerView.Adapter<DevUsersAdapter.UserViewHolder>() {

    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userName: TextView = view.findViewById(R.id.userName)
        val daysRecycler: RecyclerView = view.findViewById(R.id.daysRecyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.dev_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.userName.text = user.userName
        holder.daysRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = DevDaysAdapter(user.days)
            setHasFixedSize(true)
            isNestedScrollingEnabled = false
        }
    }

    override fun getItemCount(): Int = users.size
}
