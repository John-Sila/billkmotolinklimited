package com.example.billkmotolinkltd.ui

import android.text.Spannable
import android.text.SpannableString
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.graphics.Paint
import android.graphics.Canvas
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.core.graphics.withTranslation

class ChatMessageAdapter(
    private val messages: List<ChatMessage>,
    private val currentUserId: String,
    private val currentUserName: String,
    // private val emojiList: List<ChatActivity.Emoji> // Pass your emoji list here
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

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

        val screenWidth = holder.itemView.resources.displayMetrics.widthPixels
        val maxBubbleWidth = (screenWidth * 0.8).toInt() // 80% of screen

        if (holder is SentMessageViewHolder) {
            displayMessageWithEmojis(message.message, holder.messageText)
            holder.senderName.text = "Me"
            holder.messageTime.text = formatTimestamp(message.timestamp)

            // Adjust bubble width
            val params = holder.messageBubble.layoutParams
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
            holder.messageBubble.layoutParams = params


            holder.messageBubble.layoutParams = params
            holder.messageBubble.post {
                if (holder.messageBubble.width > maxBubbleWidth) {
                    holder.messageBubble.layoutParams.width = maxBubbleWidth
                    holder.messageBubble.requestLayout()
                }
            }

        } else if (holder is ReceivedMessageViewHolder) {
            displayMessageWithEmojis(message.message, holder.messageText)
            holder.senderName.text = message.senderName
            holder.messageTime.text = formatTimestamp(message.timestamp)

            val params = holder.messageBubble.layoutParams
            params.width = LinearLayout.LayoutParams.WRAP_CONTENT
            holder.messageBubble.layoutParams = params
            holder.messageBubble.post {
                if (holder.messageBubble.width > maxBubbleWidth) {
                    holder.messageBubble.layoutParams.width = maxBubbleWidth
                    holder.messageBubble.requestLayout()
                }
            }
        }
    }


    override fun getItemCount(): Int = messages.size

    // --- ViewHolders ---
    class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val senderName: TextView = itemView.findViewById(R.id.senderName)
        val messageTime: TextView = itemView.findViewById(R.id.messageTime)
        val messageBubble: androidx.cardview.widget.CardView = itemView.findViewById(R.id.messageBubble)
    }

    class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val senderName: TextView = itemView.findViewById(R.id.senderName)
        val messageTime: TextView = itemView.findViewById(R.id.messageTime)
        val messageBubble: androidx.cardview.widget.CardView = itemView.findViewById(R.id.messageBubble)
    }


    // --- Timestamp formatting ---
    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMillis = now - timestamp

        val messageDate = Calendar.getInstance().apply { timeInMillis = timestamp }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }

        fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }

        return when {
            isSameDay(messageDate, today) -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
            isSameDay(messageDate, yesterday) -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp)) + " (yest...)"
            else -> SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }

    // --- Emoji replacement function ---
    private fun displayMessageWithEmojis(message: String, textView: TextView) {
        val spannable = SpannableString(message)

        textView.text = spannable
    }

}
