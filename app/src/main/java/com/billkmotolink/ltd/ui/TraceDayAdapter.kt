package com.billkmotolink.ltd.ui

import android.animation.ValueAnimator
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.billkmotolink.ltd.R

class TraceDayAdapter(private val dayList: List<TraceDay>) : RecyclerView.Adapter<TraceDayAdapter.DayViewHolder>() {

    inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDayName: TextView = itemView.findViewById(R.id.tvDayName)
        val rvMessages: RecyclerView = itemView.findViewById(R.id.rvMessages)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        Log.d("TraceDayAdapter", "onCreateViewHolder called")
        val view = LayoutInflater.from(parent.context).inflate(R.layout.trace_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val day = dayList[position]
        holder.tvDayName.text = day.day
        holder.rvMessages.layoutManager = LinearLayoutManager(holder.itemView.context)
        holder.rvMessages.adapter = TraceMessageAdapter(day.messages)

        holder.tvDayName.setOnClickListener {
            val messageView = holder.rvMessages

            if (messageView.isVisible) {
                val initialHeight = messageView.height
                val animator = ValueAnimator.ofInt(initialHeight, 0)
                animator.addUpdateListener {
                    val value = it.animatedValue as Int
                    messageView.layoutParams.height = value
                    messageView.layoutParams = messageView.layoutParams // Update without requestLayout()
                }
                animator.duration = 300
                animator.doOnEnd {
                    messageView.visibility = View.GONE
                    messageView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT // reset height
                }
                animator.start()
            } else {
                messageView.visibility = View.VISIBLE
                messageView.measure(
                    View.MeasureSpec.makeMeasureSpec((messageView.parent as View).width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.UNSPECIFIED
                )
                val targetHeight = messageView.measuredHeight
                messageView.layoutParams.height = 0

                val animator = ValueAnimator.ofInt(0, targetHeight)
                animator.addUpdateListener {
                    val value = it.animatedValue as Int
                    messageView.layoutParams.height = value
                    messageView.layoutParams = messageView.layoutParams // Apply without triggering layout pass
                }
                animator.duration = 300
                animator.start()
            }
        }

    }

    override fun getItemCount(): Int = dayList.size
}
