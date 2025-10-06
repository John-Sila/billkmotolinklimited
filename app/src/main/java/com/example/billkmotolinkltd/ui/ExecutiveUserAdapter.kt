package com.example.billkmotolinkltd.ui

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.ExecutiveUserItemBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ExecutiveUserAdapter(
    private val users: List<ExecutiveUser>
) : RecyclerView.Adapter<ExecutiveUserAdapter.ExecutiveUserViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExecutiveUserViewHolder {
        val binding = ExecutiveUserItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ExecutiveUserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExecutiveUserViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user)
    }

    override fun getItemCount(): Int = users.size

    class ExecutiveUserViewHolder(private val binding: ExecutiveUserItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: ExecutiveUser) {
            binding.usernameTextView.text = user.userName
            binding.rankTextView.text = user.userRank
            when (user.userRank) {
                "Systems, IT" -> {
                    binding.summaryTextView.visibility = View.GONE
                    binding.summary2TextView.visibility = View.GONE
                    binding.lastClockedDateTextView.text = "~ ICT"
                }
                "HR" -> {
                    binding.summaryTextView.visibility = View.GONE
                    binding.summary2TextView.visibility = View.GONE
                    binding.lastClockedDateTextView.text = "~ Human Resource"
                }
                else -> {
                    // not ICT and definitely not CEO either because we didn't even load their data
                    binding.lastClockedDateTextView.text = user.lastClockDate?.toDate()?.let {
                        SimpleDateFormat("~ dd, MMMM yyyy", Locale.getDefault()).format(it)
                    } ?: "No single clockout"

                    val date = user.lastClockDate?.toDate()
                    val formattedDate = date?.let {
                        val day = SimpleDateFormat("d", Locale.getDefault()).format(it).toInt()
                        val daySuffix = when {
                            day in 11..13 -> "th"
                            day % 10 == 1 -> "st"
                            day % 10 == 2 -> "nd"
                            day % 10 == 3 -> "rd"
                            else -> "th"
                        }

                        val dateFormat = SimpleDateFormat("EEEE, d'$daySuffix' MMMM yyyy 'at' HH:mm:ss 'GMT'", Locale.getDefault())
                        dateFormat.timeZone = TimeZone.getTimeZone("GMT")
                        dateFormat.format(it)
                    } ?: "N/A"
                    binding.summaryTextView.text =
                        when (user.isClockedIn) {
                            true -> {
                                "User is clocked in."
                            }

                            false -> {
                                "$formattedDate, ${user.userName?.substringBefore(" ")}'s clockout recorded a net income of ${formatIncome(user.netIncome ?: 0.0)}."
                            }

                            else -> {
                                "-- No information --"
                            }
                        }

                    binding.summary2TextView.text = if (user.pendingAmount == 0.0) {
                        "There is no income to approve in this account."
                    } else {
                        "You have ${formatIncome(user.pendingAmount ?: 0.0)} to approve in this account."
                    }

                }
            }
        }
    }
}
