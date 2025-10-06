package com.example.billkmotolinkltd.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.LogoutLoadingActivity
import com.example.billkmotolinkltd.R
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class PollsAdapter(
    private val currentUserId: String?,
    private val currentUserRank: String?,
    private val currentUserName: String?
) : RecyclerView.Adapter<PollsAdapter.PollViewHolder>() {

    private val polls = mutableListOf<Poll>()

    fun submitList(newList: List<Poll>) {
        polls.clear()
        polls.addAll(newList)
        notifyDataSetChanged()
    }

    private fun getTimeRemaining(expiresAt: Timestamp?): String {
        if (expiresAt == null) return "No expiry"

        val now = System.currentTimeMillis()
        val expiryMillis = expiresAt.toDate().time

        val diff = expiryMillis - now
        if (diff <= 0) {
            return "Expired"
        }

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "$days day${if (days.toInt() == 1) "" else "s"} ${hours % 24} hour${if ((hours % 24).toInt() == 1) "" else "s"} remaining"
            hours > 0 -> "$hours hour${if (hours.toInt() == 1) "" else "s"} ${minutes % 60} minute${if ((minutes % 60).toInt() == 1) "" else "s"} remaining"
            minutes > 0 -> "$minutes minute${if (minutes.toInt() == 1) "" else "s"} remaining"
            else -> "$seconds second${if (seconds.toInt() == 1) "" else "s"} remaining"
        }
    }

    inner class PollViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.pollTitle)
        private val description: TextView = itemView.findViewById(R.id.pollDescription)
        // private val optionsText: TextView = itemView.findViewById(R.id.optionsText)
        private val optionsContainer: LinearLayout = itemView.findViewById(R.id.optionsContainer)
        private val pollExpired: TextView = itemView.findViewById(R.id.pollExpired)
        private val pollNotExpired: TextView = itemView.findViewById(R.id.pollNotExpired)
        private val alreadyVoted: TextView = itemView.findViewById(R.id.alreadyVoted)

        private val leftBar: View = itemView.findViewById(R.id.leftBar)

        private val pollAllowance: TextView = itemView.findViewById(R.id.pollAllowance)

        private val voters: TextView = itemView.findViewById(R.id.voters)

        fun bind(poll: Poll, currentUserId: String?, currentUserRank: String?, currentUserName: String?) {
            title.text = poll.title
            description.text = poll.description
            val ctx = itemView.context

            // Clear old views before adding new ones
            optionsContainer.removeAllViews()

            if (getTimeRemaining(poll.expiresAt) == "Expired") {
                pollExpired.visibility = View.VISIBLE
                pollExpired.text = "This poll is no longer active"
                leftBar.setBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.l3)
                )
            }
            else {
                pollNotExpired.visibility = View.VISIBLE
                pollNotExpired.text = getTimeRemaining(poll.expiresAt)

                if (currentUserRank in poll.allowedVoters) {
                    leftBar.setBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.green)
                    )
                } else {
                    pollAllowance.text = "You are not allowed to vote on this poll"
                    pollAllowance.visibility = View.VISIBLE
                    leftBar.setBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.color4)
                    )
                }
            }

            // Dynamically add each option
            poll.options.forEach { option ->
                val optionLayout = LinearLayout(itemView.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 16, 0, 16)
                }

                val optionText = TextView(itemView.context).apply {
                    val fullText = "${option.name} (${option.votes} vote${if (option.votes == 1) "" else "s"})"
                    val spannable = SpannableString(fullText)

                    // Find start and end of the votes part
                    val start = fullText.indexOf("(")
                    val end = fullText.length

                    spannable.setSpan(
                        ForegroundColorSpan(ContextCompat.getColor(context, R.color.teal_700)),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    text = spannable
                    textSize = 16f
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }

                val voteButton = Button(itemView.context).apply {
                    text = "Vote"
                    setPadding(32, 16, 32, 16)
                    background = ContextCompat.getDrawable(context, R.drawable.rounded_inputs)
                    isAllCaps = false
                    setBackgroundColor(resources.getColor(R.color.color4, null))
                    setTextColor(resources.getColor(android.R.color.white, null))
                    textSize = 14f

                    // TODO: Hook up the voting action
                    setOnClickListener {
                        // Handle voting for this option
                        val alertDialog = AlertDialog.Builder(ctx)

                        // Custom title with red color
                        val title = SpannableString("Polls")
                        title.setSpan(
                            ForegroundColorSpan(Color.RED),
                            0,
                            title.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                        // Custom message with black color
                        val message =
                            SpannableString("Voting can only be done once and cannot be reversed.")
                        message.setSpan(
                            ForegroundColorSpan(Color.GRAY),
                            0,
                            message.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                        alertDialog.setTitle(title)
                        alertDialog.setMessage(message)
                        alertDialog.setIcon(R.drawable.warn)

                        alertDialog.setPositiveButton("Confirm") { _, _ ->
                            showVotingDialog(ctx, "Placing your vote")
                            voteForThisOption(
                                ctx,
                                option.name,
                                poll.id,
                                currentUserId,
                                currentUserName
                            )
                        }

                        alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss() // Dismiss dialog if user cancels
                        }

                        alertDialog.create().apply {
                            window?.setBackgroundDrawableResource(R.drawable.rounded_black)
                            show()

                            // Change button text colors after showing
                            getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.GREEN)  // confirm button
                            getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.RED)    // cancel button
                        }
                    }
                }

                optionLayout.addView(optionText)
                if ((currentUserRank in poll.allowedVoters) && currentUserId !in poll.votedUIDs && getTimeRemaining(poll.expiresAt) != "Expired") {
                    /*we are allowed, we haven't voted yet and it's not expired*/
                    optionLayout.addView(voteButton)
                }
                optionsContainer.addView(optionLayout)
            }

            if (currentUserId in poll.votedUIDs && getTimeRemaining(poll.expiresAt) !== "Expired") {
                alreadyVoted.visibility = View.VISIBLE
            }

            if (currentUserRank in listOf("CEO", "Systems, IT", "Admin")) {
                voters.text = if (poll.votedUserNames.isEmpty()) {
                    "No votes yet"
                } else {
                    "Voted: ${poll.votedUserNames.joinToString(", ")}"
                }
                voters.visibility = View.VISIBLE
            }
        }
    }

    private fun voteForThisOption(
        ctx: Context,
        optionName: String,
        pollId: String,
        currentUserId: String?,
        currentUserName: String?
    ) {
        val db = FirebaseFirestore.getInstance()
        val pollRef = db.collection("polls").document("billk_polls")

        db.runTransaction { transaction ->
            val snapshot = transaction.get(pollRef)
            val pollData = snapshot.get(pollId) as? Map<*, *> ?: throw Exception("Poll not found")

            val options = (pollData["options"] as? List<Map<String, Any>>)?.toMutableList()
                ?: throw Exception("Options not found")

            // Rebuild updated options list
            val updatedOptions = options.map { option ->
                if (option["name"] == optionName) {
                    option.toMutableMap().apply {
                        val currentVotes = (this["votes"] as? Long) ?: 0L
                        this["votes"] = currentVotes + 1
                    }
                } else {
                    option
                }
            }

            // Update options field
            transaction.update(pollRef, "$pollId.options", updatedOptions)

            // Append to arrays without overwriting
            if (currentUserId != null) {
                transaction.update(pollRef, "$pollId.votedUIDs", FieldValue.arrayUnion(currentUserId))
            }
            if (!currentUserName.isNullOrEmpty()) {
                transaction.update(pollRef, "$pollId.votedUserNames", FieldValue.arrayUnion(currentUserName))
            }
        }.addOnSuccessListener {
            Toast.makeText(ctx, "Vote recorded successfully.", Toast.LENGTH_LONG).show()
            dismissVotingDialog(ctx)
        }.addOnFailureListener { e ->
            Toast.makeText(ctx, "Voting failed with a critical error: ${e.message}", Toast.LENGTH_LONG).show()
            dismissVotingDialog(ctx)
        }
    }

    private fun showVotingDialog(context: Context, message: String) {
        val intent = Intent(context, LogoutLoadingActivity::class.java).apply {
            putExtra("EXTRA_MESSAGE", message)
        }
        context.startActivity(intent)
    }

    private fun dismissVotingDialog(context: Context) {
        val intent = Intent(context, LogoutLoadingActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        context.startActivity(intent)
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PollViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_poll, parent, false)
        return PollViewHolder(view)
    }

    override fun onBindViewHolder(holder: PollViewHolder, position: Int) {
        holder.bind(polls[position], currentUserId, currentUserRank, currentUserName)
    }

    override fun getItemCount() = polls.size
}
