package com.example.billkmotolinkltd.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.ActivityChatroomSettingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore

/*this is the actual chatroom Settings
* It's relative*/

class ChatroomSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatroomSettingsBinding
    private val firestore = FirebaseFirestore.getInstance()
    private val rtdb = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatroomSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val chatroomId = intent.getStringExtra("chatroomId") ?: return
        val chatroomName = intent.getStringExtra("chatroomName") ?: return
        val chatroomCreator = intent.getStringExtra("chatroomCreator") ?: return

        val actionBar = supportActionBar
        actionBar?.setDisplayShowCustomEnabled(true)
        actionBar?.setDisplayShowTitleEnabled(false) // Hide default title

        val customView = layoutInflater.inflate(R.layout.custom_action_bar_title, null)
        val titleTextView = customView.findViewById<TextView>(R.id.actionBarTitle)
        titleTextView.text = chatroomName

        actionBar?.customView = customView

        fetchParticipants(chatroomId)

        supportActionBar?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            title = chatroomName
            elevation = 0f
        }
    }

    private fun fetchParticipants(chatroomId: String) {
        rtdb.child("chatrooms").child(chatroomId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val chatroom = snapshot.getValue(Chatroom::class.java)

                    val approvedUIDs = chatroom?.approvedParticipants ?: emptyList()
                    val pendingUIDs = chatroom?.pendingApprovals ?: emptyList()

                    resolveUsernames(approvedUIDs) { approvedUsernames ->
                        resolveUsernames(pendingUIDs) { pendingUsernames ->
                            setupAdapters(approvedUsernames, pendingUsernames)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Log or handle error if needed
                }
            })
    }

    private fun resolveUsernames(
        uids: List<String>,
        callback: (List<UserEntry>) -> Unit
    ) {
        if (uids.isEmpty()) {
            callback(emptyList())
            return
        }

        val users = mutableListOf<UserEntry>()
        val total = uids.size
        var processed = 0

        uids.forEach { uid ->
            firestore.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val name = doc.getString("userName") ?: "Unknown"
                    users.add(UserEntry(uid, name))
                    processed++
                    if (processed == total) callback(users)
                }
                .addOnFailureListener {
                    users.add(UserEntry(uid, "Unknown"))
                    processed++
                    if (processed == total) callback(users)
                }
        }
    }

    private fun setupAdapters(approved: List<UserEntry>, pending: List<UserEntry>) {
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
        val creatorUid = intent.getStringExtra("chatroomCreator")
        val chatroomId = intent.getStringExtra("chatroomId") ?: return

        val isCreator = currentUserUid == creatorUid

        val approvedAdapter = UsernameAdapter(
            approved,
            "Approved",
            isCreator,
            onRemove = { uid ->
                rtdb.child("chatrooms").child(chatroomId)
                    .child("approvedParticipants").get().addOnSuccessListener {
                        val list = it.getValue(object : GenericTypeIndicator<MutableList<String>>() {}) ?: mutableListOf()
                        list.remove(uid)
                        rtdb.child("chatrooms").child(chatroomId).child("approvedParticipants").setValue(list)
                    }
            },
            onApprove = {},
            onDeny = {},
            currentUid = currentUserUid,
            onLeave = { uid ->
                Log.d("Chatroom", "Attempting to leave chatroom for UID: $uid")
                val chatRef = rtdb.child("chatrooms").child(chatroomId)

                chatRef.child("approvedParticipants").get().addOnSuccessListener { appSnap ->
                    val appList = appSnap.getValue(object : GenericTypeIndicator<MutableList<String>>() {}) ?: mutableListOf()
                    if (appList.contains(uid)) {
                        appList.remove(uid)
                        Log.d("Chatroom", "UID found in approved list. Removing and updating DB.")

                        chatRef.child("approvedParticipants").setValue(appList).addOnSuccessListener {
                            Log.d("Chatroom", "Successfully left chatroom. Finishing activity.")
                            finish() // kill this ChatActivity
                        }.addOnFailureListener {
                            Log.e("Chatroom", "Failed to update approvedParticipants", it)
                        }
                    } else {
                        Log.w("Chatroom", "UID not found in approved list. No action taken.")
                    }
                }.addOnFailureListener {
                    Log.e("Chatroom", "Failed to fetch approvedParticipants", it)
                }
            }


        )

        val pendingAdapter = UsernameAdapter(
            pending,
            "Pending",
            isCreator,
            onRemove = {},
            onApprove = { uid ->
                val chatRef = rtdb.child("chatrooms").child(chatroomId)
                chatRef.child("pendingApprovals").get().addOnSuccessListener { pendSnap ->
                    chatRef.child("approvedParticipants").get().addOnSuccessListener { appSnap ->
                        val pendList = pendSnap.getValue(object : GenericTypeIndicator<MutableList<String>>() {}) ?: mutableListOf()
                        val appList = appSnap.getValue(object : GenericTypeIndicator<MutableList<String>>() {}) ?: mutableListOf()
                        pendList.remove(uid)
                        appList.add(uid)
                        chatRef.child("pendingApprovals").setValue(pendList)
                        chatRef.child("approvedParticipants").setValue(appList)
                    }
                }
            },
            onDeny = { uid ->
                val chatRef = rtdb.child("chatrooms").child(chatroomId)
                chatRef.child("pendingApprovals").get().addOnSuccessListener { snap ->
                    val list = snap.getValue(object : GenericTypeIndicator<MutableList<String>>() {}) ?: mutableListOf()
                    list.remove(uid)
                    chatRef.child("pendingApprovals").setValue(list)
                }
            },
            currentUid = currentUserUid,
            onLeave = {}
        )

        binding.recyclerApproved.apply {
            layoutManager = LinearLayoutManager(this@ChatroomSettingsActivity)
            adapter = approvedAdapter
        }

        binding.recyclerPending.apply {
            layoutManager = LinearLayoutManager(this@ChatroomSettingsActivity)
            adapter = pendingAdapter
        }
    }

}
