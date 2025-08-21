package com.example.billkmotolinkltd.ui

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ChatMessageAdapter(private val messages: List<ChatMessage>,
                         private val currentUserId: String,
                         private val currentUserName: String,) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_sent, parent, false)
            SentMessageViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_received, parent, false)
            ReceivedMessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is SentMessageViewHolder) {
            holder.messageText.text = message.message
            holder.senderName.text = "Me"
            holder.messageTime.text = formatTimestamp(message.timestamp)

        } else if (holder is ReceivedMessageViewHolder) {
            holder.messageText.text = message.message
            holder.senderName.text = message.senderName
            holder.messageTime.text = formatTimestamp(message.timestamp)

        }
    }

    fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMillis = now - timestamp
        val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)

        val messageDate = Calendar.getInstance().apply { timeInMillis = timestamp }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }

        fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }

        return when {
            diffMinutes < 1 -> {
                "just now"
            }
            diffMinutes < 45 -> {
                "$diffMinutes m ago"
            }
            isSameDay(messageDate, today) -> {
                "today ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))}"
            }
            isSameDay(messageDate, yesterday) -> {
                "${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))} (yest...)"
            }
            else -> {
                // Optional fallback: e.g., 03 Aug 16:12
                SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }


    override fun getItemCount(): Int = messages.size

    class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val senderName: TextView = itemView.findViewById(R.id.senderName)
        val messageTime: TextView = itemView.findViewById(R.id.messageTime)
    }

    class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val senderName: TextView = itemView.findViewById(R.id.senderName)
        val messageTime: TextView = itemView.findViewById(R.id.messageTime)
    }
}
