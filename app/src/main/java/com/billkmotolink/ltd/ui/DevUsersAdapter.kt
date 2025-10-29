package com.billkmotolink.ltd.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.billkmotolink.ltd.R

class DevUsersAdapter(initialUsers: List<DevUser> = emptyList()) :
    RecyclerView.Adapter<DevUsersAdapter.UserViewHolder>() {

    private val users = mutableListOf<DevUser>().apply { addAll(initialUsers) }
    private val expandedUsers = mutableSetOf<String>() // Track expanded/collapsed states

    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userName: TextView = view.findViewById(R.id.userName)
        val daysRecycler: RecyclerView = view.findViewById(R.id.daysRecyclerView)
        private val daysAdapter = DevDaysAdapter() // Initialize once

        init {
            daysRecycler.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = daysAdapter
                setHasFixedSize(true)
                isNestedScrollingEnabled = false
            }

            userName.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val user = users[position]
                    if (user.userName in expandedUsers) {
                        expandedUsers.remove(user.userName)
                        daysRecycler.visibility = View.GONE
                    } else {
                        expandedUsers.add(user.userName)
                        daysRecycler.visibility = View.VISIBLE
                    }
                }
            }
        }

        fun bind(user: DevUser) {
            userName.text = user.userName
            daysAdapter.submitList(user.days)
            daysRecycler.visibility =
                if (user.userName in expandedUsers) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.dev_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size

    fun submitList(newUsers: List<DevUser>) {
        val expandedUserNames = expandedUsers.toSet()

        users.clear()
        users.addAll(newUsers)

        // Restore expanded states for existing users
        expandedUsers.clear()
        expandedUsers.addAll(expandedUserNames.intersect(newUsers.map { it.userName }))

        notifyDataSetChanged()
    }
}