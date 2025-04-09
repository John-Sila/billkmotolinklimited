package com.example.billkmotolinkltd.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R
import java.util.Calendar
import androidx.core.graphics.toColorInt
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale


// this is for approvals ONLY
class UsersAdapter : ListAdapter<User, UsersAdapter.UserViewHolder>(UserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = getItem(position)
        holder.bind(user)
    }

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewName: TextView = itemView.findViewById(R.id.textViewName)
        private val textViewActiveStatus: TextView = itemView.findViewById(R.id.textViewActiveStatus)
        private val textViewSundayText: TextView = itemView.findViewById(R.id.textViewSundayText)
        private val amountPendingApproval: TextView = itemView.findViewById(R.id.amountPendingApproval)
        private val textViewDailyTarget: TextView = itemView.findViewById(R.id.textViewDailyTarget)
        private val textViewCurrentInAppBal: TextView = itemView.findViewById(R.id.textViewCurrentInAppBal)
        private val textViewSundayTarget: TextView = itemView.findViewById(R.id.textViewSundayTarget)


         fun bind(user: User) {
             val calendar = Calendar.getInstance()
             val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
             val daysUntilSunday = (Calendar.SUNDAY - currentDayOfWeek + 7) % 7
             calendar.add(Calendar.DAY_OF_YEAR, daysUntilSunday)
             val dayDescription = when (daysUntilSunday) {
                 0 -> "today"
                 1 -> "tomorrow"
                 else -> "this sunday"
             }
             var sundayText = ""
            if (user.isWorkingOnSunday) {
                sundayText = "User is set to work $dayDescription"
            } else {
                sundayText = "User will not be working $dayDescription"
            }
             fun formatIncome(amount: Double): String {
                 val symbols = DecimalFormatSymbols(Locale("en", "KE")).apply {
                     currencySymbol = "Ksh. "
                 }
                 val formatter = DecimalFormat("¤#,##0.00", symbols)
                 return formatter.format(amount)
             }
             var activeText = ""
             activeText = if (user.isActive == true) { "Active" } else "Inactive"

            textViewName.text = user.userName
            textViewActiveStatus.text = activeText
            textViewSundayText.text = sundayText
            amountPendingApproval.text = formatIncome(user.pendingAmount)
            textViewDailyTarget.text = formatIncome(user.dailyTarget)
            textViewCurrentInAppBal.text = formatIncome(user.currentInAppBalance)
             textViewSundayTarget.text = formatIncome(user.sundayTarget)
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            // Implement your logic to compare items
            return oldItem.email == newItem.email
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            // Implement your logic to compare item contents
            return oldItem == newItem
        }
    }
}