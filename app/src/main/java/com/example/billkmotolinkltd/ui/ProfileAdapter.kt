package com.example.billkmotolinkltd.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.core.view.isVisible

class ProfileAdapter(
    private val context: Context
) : RecyclerView.Adapter<ProfileAdapter.UserViewHolder>() {

    private val diffCallback = object : DiffUtil.ItemCallback<ProfileUser>() {
        override fun areItemsTheSame(oldItem: ProfileUser, newItem: ProfileUser): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: ProfileUser, newItem: ProfileUser): Boolean {
            return oldItem == newItem
        }
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    fun submitList(list: List<ProfileUser>) {
        val uniqueList = list.distinctBy { it.uid }
        differ.submitList(uniqueList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = differ.currentList[position]

        holder.username?.text = user.userName
        holder.lastClockIn.text = formatDate(user.lastSeenClockInTime)
        holder.lastClockOut.text = formatDate(user.lastClockDate)
        holder.currentBike.text = user.currentBike
        holder.dtbAcc.text = user.bankAcc
        holder.inAppBal.text = user.inAppBalance.toString()
        holder.dTarget.text = user.dailyTarget.toString()
        holder.sTarget.text = user.sundayTarget.toString()
        holder.dateEnrolled.text = formatDate(user.dateCreated)
        holder.userEmail.text = user.email
        holder.fcm.text = user.fcmToken
        holder.gender.text = user.gender
        holder.profileUid.text = user.uid
        holder.hrsPerShift.text = user.hrsPerShift.toString()
        holder.nID.text = user.idNumber
        holder.activeState.text = if (user.isActive) "Active" else "Inactive"
        holder.netClockedLastly.text = user.netClockedLastly.toString()
        holder.clockedInState.text = if (user.isClockedIn) "Clocked In" else "Clocked Out"
        holder.profilePendingAmount.text = user.amountPendingApproval.toString()
        holder.phone.text = user.phoneNumber
        holder.requirements.text = user.requirements.toString()
        holder.rank.text = user.userRank

        // Optionally: Hide requirement section if 0
        holder.reqDiv.visibility = if (user.requirements.toInt() == 0) View.GONE else View.VISIBLE
        holder.username?.setOnClickListener {
            holder.detailsLayout?.let { layout ->
                val isVisible = layout.isVisible
                layout.visibility = if (isVisible) View.GONE else View.VISIBLE
            }
        }

        if (user.userRank in arrayOf("Systems, IT", "Human Resource")) {
            holder.cbDiv.visibility = View.GONE
            holder.iabDiv.visibility = View.GONE
            holder.dtDiv.visibility = View.GONE
            holder.hpsDiv.visibility = View.GONE
            holder.ciDiv.visibility = View.GONE
            holder.lciDiv.visibility = View.GONE
            holder.lcoDIv.visibility = View.GONE
            holder.nclDiv.visibility = View.GONE
            holder.paDiv.visibility = View.GONE
            holder.stDiv.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = differ.currentList.size

    override fun getItemId(position: Int): Long {
        return differ.currentList[position].uid.hashCode().toLong()
    }

    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val username: TextView? = view.findViewById(R.id.tvUsername)
        val detailsLayout: LinearLayout? = view.findViewById(R.id.detailsLayout)
        val lastClockIn: TextView = view.findViewById(R.id.profileLastClockInDate)
        val lastClockOut: TextView = view.findViewById(R.id.profileLastClockDate)
        val currentBike: TextView = view.findViewById(R.id.profileCurrentBike)
        val dtbAcc: TextView = view.findViewById(R.id.profileDTBAcc)
        val inAppBal: TextView = view.findViewById(R.id.profileInAppBalance)
        val dTarget: TextView = view.findViewById(R.id.profileSetTarget)
        val sTarget: TextView = view.findViewById(R.id.profileSundayTarget)
        val dateEnrolled: TextView = view.findViewById(R.id.profileDateCreated)
        val userEmail: TextView = view.findViewById(R.id.profileEmail)
        val fcm: TextView = view.findViewById(R.id.profileFcm)
        val gender: TextView = view.findViewById(R.id.profileGender)
        val profileUid: TextView = view.findViewById(R.id.profileUID)
        val hrsPerShift: TextView = view.findViewById(R.id.profileShiftHrs)
        val nID: TextView = view.findViewById(R.id.profileNationalId)
        val activeState: TextView = view.findViewById(R.id.profileActive)
        val netClockedLastly: TextView = view.findViewById(R.id.profileNetClockedLastly)
        val clockedInState: TextView = view.findViewById(R.id.profileClockedIn)
        val profilePendingAmount: TextView = view.findViewById(R.id.profilePendingAmount)
        val phone: TextView = view.findViewById(R.id.profilePhone)
        val requirements: TextView = view.findViewById(R.id.profileRequirements)
        val rank: TextView = view.findViewById(R.id.profileRank)
        val reqDiv: LinearLayout = view.findViewById(R.id.reqDiv)
        val cbDiv: LinearLayout = view.findViewById(R.id.cbDiv)
        val iabDiv: LinearLayout = view.findViewById(R.id.iabDiv)
        val dtDiv: LinearLayout = view.findViewById(R.id.dtDiv)
        val hpsDiv: LinearLayout = view.findViewById(R.id.hpsDiv)
        val ciDiv: LinearLayout = view.findViewById(R.id.ciDiv)
        val lciDiv: LinearLayout = view.findViewById(R.id.lciDiv)
        val lcoDIv: LinearLayout = view.findViewById(R.id.lcoDiv)
        val nclDiv: LinearLayout = view.findViewById(R.id.nclDiv)
        val paDiv: LinearLayout = view.findViewById(R.id.paDiv)
        val stDiv: LinearLayout = view.findViewById(R.id.stDiv)
    }

    private fun formatDate(timestamp: Timestamp): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return sdf.format(timestamp.toDate())
    }
}


