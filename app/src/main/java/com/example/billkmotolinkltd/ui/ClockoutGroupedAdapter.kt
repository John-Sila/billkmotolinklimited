package com.example.billkmotolinkltd.ui

import android.animation.ValueAnimator
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil

class ClockoutGroupedAdapter : RecyclerView.Adapter<ClockoutGroupedAdapter.GroupViewHolder>() {

    private val expandedGroups = mutableSetOf<String>()

    private val differ = AsyncListDiffer(
        this,
        object : DiffUtil.ItemCallback<ClockoutGroup>() {
            override fun areItemsTheSame(oldItem: ClockoutGroup, newItem: ClockoutGroup) =
                oldItem.userId == newItem.userId

            override fun areContentsTheSame(oldItem: ClockoutGroup, newItem: ClockoutGroup) =
                oldItem == newItem
        }
    )

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return differ.currentList[position].userId.hashCode().toLong()
    }


    fun updateData(newList: List<ClockoutGroup>) {
        val expandedUsers = expandedGroups.toSet()
        differ.submitList(newList) {
            expandedGroups.clear()
            expandedGroups.addAll(expandedUsers)
        }
    }

    inner class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userNameTextView: TextView = itemView.findViewById(R.id.textUserName)
        val nestedRecyclerView: RecyclerView = itemView.findViewById(R.id.nestedRecyclerView)
        private val entryAdapter = ClockoutEntryAdapter(mutableListOf())

        init {
            nestedRecyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = entryAdapter
            }

            userNameTextView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val group = differ.currentList.getOrNull(position)
                    group?.let {
                        if (it.userName in expandedGroups) {
                            expandedGroups.remove(it.userName)
                            nestedRecyclerView.visibility = View.GONE
                        } else {
                            expandedGroups.add(it.userName)
                            nestedRecyclerView.visibility = View.VISIBLE
                            nestedRecyclerView.isNestedScrollingEnabled = true
                        }

                        // Run the background animation
                        userNameTextView.post {
                            val bg = userNameTextView.background
                            if (bg is LayerDrawable) {
                                val progressDrawable = bg.findDrawableByLayerId(R.id.progress)
                                val anim = ValueAnimator.ofInt(0, userNameTextView.width)
                                anim.duration = 8000
                                anim.addUpdateListener { va ->
                                    val value = va.animatedValue as Int
                                    progressDrawable.setBounds(0, 0, value, userNameTextView.height)
                                    userNameTextView.invalidate()
                                }
                                anim.start()
                            }
                        }
                    }
                }
            }
        }

        fun bind(group: ClockoutGroup) {
            entryAdapter.updateEntries(
                group.entries.sortedByDescending { it.postedAt }
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clockout_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = differ.currentList[position]
        holder.userNameTextView.text = group.userName
        holder.bind(group)
        holder.nestedRecyclerView.visibility =
            if (group.userName in expandedGroups) View.VISIBLE else View.GONE

    }

    override fun getItemCount(): Int = differ.currentList.size
}
