package com.example.billkmotolinkltd.ui

import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentIncidencesBinding
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
        holder.reportUser.text = "${report["username"].toString()}"
        holder.reportTitle.text = "${report["reportType"].toString()}"
        holder.reportDescription.text = "${report["reportDescription"].toString()}"
        holder.reportBike.text = "${report["involvedBike"].toString() }"

        val timestamp = report["time"] as? Timestamp
        timestamp?.toDate()?.let { date ->
            val calendar = Calendar.getInstance().apply { time = date }
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val daySuffix = when {
                day in 11..13 -> "th"
                day % 10 == 1 -> "st"
                day % 10 == 2 -> "nd"
                day % 10 == 3 -> "rd"
                else -> "th"
            }

            val sdf = SimpleDateFormat("MMMM yyyy 'at' HH:mm:ss", Locale.getDefault())
            val formattedDate = "${day}$daySuffix, ${sdf.format(date)}"

            val reportCal = Calendar.getInstance().apply { time = date }
            val nowCal = Calendar.getInstance()

            val dayDifference = nowCal.get(Calendar.DAY_OF_YEAR) - reportCal.get(Calendar.DAY_OF_YEAR)
            val yearDifference = nowCal.get(Calendar.YEAR) - reportCal.get(Calendar.YEAR)

            val daysAgo = if (yearDifference == 0) {
                dayDifference
            } else {
                // Approximate fallback
                ((nowCal.timeInMillis - reportCal.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
            }

            val bracketText = when (daysAgo) {
                0 -> "(Today)"
                1 -> "(Yesterday)"
                else -> "($daysAgo days ago)"
            }

            val fullText = "$formattedDate $bracketText"
            val spannable = SpannableString(fullText)

            // Set the red color span only for the bracket text
            val start = fullText.indexOf(bracketText)
            val end = start + bracketText.length
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light)),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            holder.reportTime.text = spannable
        } ?: run {
            holder.reportTime.text = "N/A"
        }



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
