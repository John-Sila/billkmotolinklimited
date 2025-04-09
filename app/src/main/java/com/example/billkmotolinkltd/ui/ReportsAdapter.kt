package com.example.billkmotolinkltd.ui

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentIncidencesBinding
import com.google.firebase.firestore.FirebaseFirestore

class ReportsAdapter(private var reports: List<Map<String, Any>>) : RecyclerView.Adapter<ReportsAdapter.ReportViewHolder>() {

    // Update reports list method
    fun setReports(newReports: List<Map<String, Any>>) {
        reports = newReports
        notifyDataSetChanged()  // Notify the adapter that data has changed
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_report, parent, false)
        return ReportViewHolder(view)
    }


    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        val report = reports[position]

        // Bind the data for each report
        holder.reportUser.text = "Rider: ${report["username"].toString()}"
        holder.reportTitle.text = "Issue: ${report["reportType"].toString()}"
        holder.reportDescription.text = "Description: ${report["reportDescription"].toString()}"
        holder.reportTime.text = "Time: ${report["time"].toString()}"
        holder.reportBike.text = "Bike: ${ report["involvedBike"].toString() }"

    }



    override fun getItemCount(): Int {
        return reports.size
    }

    // ViewHolder to bind data
    class ReportViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val reportUser: TextView = itemView.findViewById(R.id.reportUser)
        val reportTitle: TextView = itemView.findViewById(R.id.reportTitle)
        val reportDescription: TextView = itemView.findViewById(R.id.reportDescription)
        val reportTime: TextView = itemView.findViewById(R.id.reportTime)
        val reportBike: TextView = itemView.findViewById(R.id.reportBike)


    }
}
