package com.billkmotolink.ltd.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.billkmotolink.ltd.R
import android.widget.TextView
import android.widget.Button

class UsernameAdapter(
    private val users: List<UserEntry>,
    private val type: String,
    private val isCreator: Boolean,
    private val currentUid: String?,
    private val onRemove: (String) -> Unit,
    private val onApprove: (String) -> Unit,
    private val onDeny: (String) -> Unit,
    private val onLeave: (String) -> Unit,
) : RecyclerView.Adapter<UsernameAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name = view.findViewById<TextView>(R.id.username)
        val removeBtn = view.findViewById<Button>(R.id.removeBtn)
        val letInBtn = view.findViewById<Button>(R.id.letInBtn)
        val denyBtn = view.findViewById<Button>(R.id.denyBtn)
        val leaveBtn = view.findViewById<Button>(R.id.leaveBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_entry, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = users.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = users[position]
        holder.name.text = entry.userName

        if (entry.uid == currentUid) {
            // Don't show any buttons for the current user
            holder.removeBtn.visibility = View.GONE
            holder.letInBtn.visibility = View.GONE
            holder.denyBtn.visibility = View.GONE
            holder.leaveBtn.visibility = View.VISIBLE
            return
        }

        if (type == "Approved") {
            holder.removeBtn.isEnabled = isCreator
            holder.removeBtn.setOnClickListener {
                onRemove(entry.uid)
            }

            holder.leaveBtn.setOnClickListener {
                onLeave(entry.uid)
            }

            holder.removeBtn.visibility = View.VISIBLE
            holder.letInBtn.visibility = View.GONE
            holder.denyBtn.visibility = View.GONE

        } else if (type == "Pending") {
            holder.letInBtn.isEnabled = isCreator
            holder.denyBtn.isEnabled = isCreator

            holder.letInBtn.setOnClickListener {
                onApprove(entry.uid)
            }

            holder.denyBtn.setOnClickListener {
                onDeny(entry.uid)
            }

            holder.removeBtn.visibility = View.GONE
            holder.letInBtn.visibility = View.VISIBLE
            holder.denyBtn.visibility = View.VISIBLE
        }
    }
}
