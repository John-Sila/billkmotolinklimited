package com.example.billkmotolinkltd.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R

class EmojiAdapter(
    private val emojis: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<EmojiAdapter.EmojiVH>() {

    inner class EmojiVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tv: TextView = itemView.findViewById(R.id.emojiText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_emoji, parent, false)
        return EmojiVH(v)
    }

    override fun onBindViewHolder(holder: EmojiVH, position: Int) {
        val emoji = emojis[position]
        holder.tv.text = emoji
        holder.tv.setOnClickListener { onClick(emoji) }
    }

    override fun getItemCount() = emojis.size
}
