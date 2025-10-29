package com.billkmotolink.ltd.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.billkmotolink.ltd.R
import com.billkmotolink.ltd.ui.complaints.ComplaintsFragment
import java.text.SimpleDateFormat
import java.util.Locale

class ComplaintsAdapter(private val complaintsList: List<ComplaintsFragment.Complaint>) :
    RecyclerView.Adapter<ComplaintsAdapter.ComplaintViewHolder>() {

    class ComplaintViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val complaintText: TextView = itemView.findViewById(R.id.complaintText)
        val complaintTime: TextView = itemView.findViewById(R.id.complaintTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComplaintViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_complaint, parent, false)
        return ComplaintViewHolder(view)
    }

    override fun onBindViewHolder(holder: ComplaintViewHolder, position: Int) {
        val complaint = complaintsList[position]
        holder.complaintText.text = complaint.message

        // Only format if timestamp is available
        complaint.timestamp?.let { ts ->
            val sdf = SimpleDateFormat("hh:mm a - MMM dd, yyyy", Locale.getDefault())
            holder.complaintTime.text = sdf.format(ts.toDate()) // convert Firestore Timestamp -> Date
        } ?: run {
            holder.complaintTime.text = "Pending..." // fallback if timestamp is null
        }
    }


    override fun getItemCount(): Int = complaintsList.size
}
