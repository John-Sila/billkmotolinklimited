package com.billkmotolink.ltd.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.billkmotolink.ltd.R
import java.text.SimpleDateFormat
import java.util.*

class TraceMessageAdapter(private val messageList: List<TraceMessage>) : RecyclerView.Adapter<TraceMessageAdapter.MessageViewHolder>() {

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.trace_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messageList[position]
        holder.tvMessage.text = message.message
        holder.tvTime.text = message.timestamp?.let {
            try {
                // Convert Timestamp to Date
                val date = Date(it.seconds * 1000)  // Multiply by 1000 to convert to milliseconds

                // Format the Date to "HH:mm:ss"
                val outputFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                outputFormat.format(date)
            } catch (e: Exception) {
                "No time"
            }
        } ?: "No time"
    }

    override fun getItemCount(): Int = messageList.size
}
