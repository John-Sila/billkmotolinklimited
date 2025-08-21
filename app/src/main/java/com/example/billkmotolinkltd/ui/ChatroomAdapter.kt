package com.example.billkmotolinkltd.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.view.isGone
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R

import androidx.recyclerview.widget.ListAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.firebase.database.ValueEventListener

import com.google.firebase.database.GenericTypeIndicator
// ChatroomAdapter.kt
class ChatroomAdapter(
    private val context: Context
) : ListAdapter<Chatroom, ChatroomAdapter.ViewHolder>(DiffCallback()) {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val roomName: TextView = view.findViewById(R.id.roomName)
        val creatorText: TextView = view.findViewById(R.id.creatorText)
        val expiresText: TextView = view.findViewById(R.id.expiresText)
        val pendingRequests: TextView = view.findViewById(R.id.pendingRequests)
        val joinButton: Button = view.findViewById(R.id.joinButton)

        val leaveButton: Button = view.findViewById(R.id.leaveButton)
        val cancelButton: Button = view.findViewById(R.id.cancelButton)
        val destroyButton: Button = view.findViewById(R.id.destroyButton)
        val chatButton: Button = view.findViewById(R.id.chatButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chatroom, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chatroom = getItem(position)

        holder.roomName.text = chatroom.name
        holder.creatorText.text = "Created by ${chatroom.createdBy}"
        holder.expiresText.text = "Expires on ${
            SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
            .format(Date(chatroom.expiresAt))}"
        val weArePending = chatroom.pendingApprovals.contains(currentUserId)
        val weAreInside = chatroom.approvedParticipants.contains(currentUserId)
        val weAreCreator = currentUserId == chatroom.creatorUID
        val pendingApprovals = chatroom.pendingApprovals.size
        val chatroomId = chatroom.id

        holder.leaveButton.visibility = View.GONE
        holder.cancelButton.visibility = View.GONE
        holder.joinButton.visibility = View.GONE
        holder.destroyButton.visibility = View.GONE
        holder.chatButton.visibility = View.GONE
        holder.joinButton.isEnabled = true

        if (weArePending) {
            holder.joinButton.isEnabled = false
            holder.joinButton.text = "Waiting..."
            holder.joinButton.visibility = View.VISIBLE
            holder.cancelButton.visibility = View.VISIBLE
        }
        else if (weAreInside) {
            if (weAreCreator) {
                holder.destroyButton.visibility = View.VISIBLE
                holder.chatButton.visibility = View.VISIBLE
                holder.pendingRequests.visibility = View.VISIBLE
                holder.pendingRequests.text = "$pendingApprovals pending request${if (pendingApprovals == 1) "" else "s"}."
            } else {
                holder.leaveButton.visibility = View.VISIBLE
                holder.chatButton.visibility = View.VISIBLE
            }
        }
        else {
            holder.joinButton.visibility = View.VISIBLE
        }


        fun showToast(string: String) {
            Toast.makeText(context, string, Toast.LENGTH_SHORT).show()
        }

        holder.destroyButton.setOnClickListener {
            AlertDialog.Builder(holder.itemView.context)
                .setTitle("Delete Chatroom")
                .setMessage("Are you sure you want to permanently delete this chatroom?")
                .setPositiveButton("Yes") { _, _ ->
                    holder.destroyButton.visibility = View.GONE

                    val chatroomId = chatroom.id
                    val chatroomRef = FirebaseDatabase.getInstance()
                        .getReference("chatrooms")
                        .child(chatroomId)

                    chatroomRef.removeValue()
                        .addOnSuccessListener {
                            Toast.makeText(holder.itemView.context, "Chatroom deleted", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { error ->
                            Toast.makeText(holder.itemView.context, "Failed to delete: ${error.message}", Toast.LENGTH_SHORT).show()
                            holder.destroyButton.visibility = View.VISIBLE
                        }
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }

        holder.joinButton.setOnClickListener {
            val currentUserUID = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val chatroomRef = FirebaseDatabase.getInstance().getReference("chatrooms").child(chatroom.id)

            chatroomRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val chatroom = snapshot.getValue(Chatroom::class.java) ?: return

                    if (chatroom.pendingApprovals.contains(currentUserUID) ||
                        chatroom.approvedParticipants.contains(currentUserUID)) {
                        showToast("You have already requested to join or are already approved.")
                        return
                    }

                    val updatedPending = chatroom.pendingApprovals.toMutableList().apply {
                        add(currentUserUID)
                    }

                    chatroomRef.child("pendingApprovals").setValue(updatedPending)
                        .addOnSuccessListener {
                            showToast("Join request sent.")
                        }
                        .addOnFailureListener {
                            showToast("Failed to send join request: ${it.message}")
                        }
                }

                override fun onCancelled(error: DatabaseError) {
                    showToast("Error: ${error.message}")
                }

            })
        }

        holder.leaveButton.setOnClickListener {
            val chatroomRef = FirebaseDatabase.getInstance()
                .getReference("chatrooms")
                .child(chatroomId) // Make sure you have the current room's ID

            val currentUID = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener

            chatroomRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val chatroom = snapshot.getValue(Chatroom::class.java)
                    if (chatroom != null) {
                        val updatedList = chatroom.approvedParticipants.toMutableList()
                        if (updatedList.remove(currentUID)) {
                            chatroomRef.child("approvedParticipants").setValue(updatedList)
                                .addOnSuccessListener {
                                    showToast("You’ve left the chatroom.")
                                }
                                .addOnFailureListener {
                                    showToast("Failed to leave the chatroom.")
                                }
                        } else {
                            showToast("You’re not in this chatroom.")
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    showToast("Error: ${error.message}")
                }
            })
        }

        holder.cancelButton.setOnClickListener {
            val chatroomRef = FirebaseDatabase.getInstance()
                .getReference("chatrooms")
                .child(chatroomId) // Make sure you have the current room's ID

            val currentUID = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener

            chatroomRef.child("pendingApprovals").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val pendingList = snapshot.getValue(object : GenericTypeIndicator<MutableList<String>>() {}) ?: mutableListOf()

                    if (pendingList.remove(currentUID)) {
                        chatroomRef.child("pendingApprovals").setValue(pendingList)
                            .addOnSuccessListener {
                                showToast("You’ve left the lobby.")
                            }
                            .addOnFailureListener {
                                showToast("Failed to leave the lobby.")
                            }
                    } else {
                        showToast("You’re not in this lobby.")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    showToast("Database error: ${error.message}")
                }
            })
        }


        holder.chatButton.setOnClickListener {
            val intent = Intent(context, ChatActivity::class.java)
            intent.putExtra("chatroom_id", chatroom.id)
            intent.putExtra("chatroom_name", chatroom.name)
            intent.putExtra("chatroom_creator", chatroom.creatorUID)
            intent.putExtra("chatroom_expiry", chatroom.expiresAt)
            context.startActivity(intent)
        }
    }


    class DiffCallback : DiffUtil.ItemCallback<Chatroom>() {
        override fun areItemsTheSame(oldItem: Chatroom, newItem: Chatroom) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Chatroom, newItem: Chatroom) = oldItem == newItem
    }
}