package com.example.billkmotolinkltd.ui.chatrooms

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.billkmotolinkltd.databinding.FragmentChatroomsBinding
import com.example.billkmotolinkltd.ui.Chatroom
import com.example.billkmotolinkltd.ui.ChatroomAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore

class ChatroomsFragment : Fragment() {
    private var _binding: FragmentChatroomsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ChatroomAdapter
    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatroomsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupCreateRoomButton()
        loadChatrooms()
        checkExpiredRooms()
    }

    private fun setupRecyclerView() {
        adapter = ChatroomAdapter(
            requireContext()
        )
        binding.chatroomsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ChatroomsFragment.adapter
        }
    }

    private fun setupCreateRoomButton() {
        binding.createRoomButton.setOnClickListener {
            showCreateRoomDialog()
        }
    }

    private fun showCreateRoomDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "Enter room name"
            setSingleLine(true)
        }

        MaterialAlertDialogBuilder(requireContext()).apply {
            setTitle("Create New Chatroom")
            setView(editText)
            setPositiveButton("Create") { _, _ ->
                val roomName = editText.text.toString().trim()
                if (roomName.isNotEmpty()) {
                    createNewRoom(roomName)
                } else {
                    showToast("Room name cannot be empty")
                }
            }
            setNegativeButton("Cancel", null)
            show()
        }
    }

    private val firestore = FirebaseFirestore.getInstance()
    private fun createNewRoom(roomName: String) {
        val currentUser = auth.currentUser ?: return showToast("Please sign in first")
        val uid = currentUser.uid

        // Step 1: Check total number of rooms
        database.child("chatrooms").get().addOnSuccessListener { snapshot ->
            if (snapshot.childrenCount >= 10) {
                showToast("Cannot create more than 10 rooms")
                return@addOnSuccessListener
            }

            // Step 2: Check if room with same name exists
            val nameExists = snapshot.children.any {
                it.child("name").value?.toString()?.equals(roomName, ignoreCase = true) == true
            }

            if (nameExists) {
                showToast("Room name already exists")
                return@addOnSuccessListener
            }

            // Step 3: Fetch username from Firestore
            firestore.collection("users").document(uid).get().addOnSuccessListener { userSnapshot ->
                val userName = userSnapshot.getString("userName") ?: "Unknown"

                // Step 4: Create room object and push to RTDB
                val newRoomRef = database.child("chatrooms").push()
                val roomId = newRoomRef.key ?: return@addOnSuccessListener

                val newRoom = Chatroom(
                    id = roomId,
                    name = roomName,
                    creatorUID = uid,
                    createdBy = userName,
                    approvedParticipants = listOf(uid),
                    participantCount = 1
                )

                newRoomRef.setValue(newRoom).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        showToast("Room '$roomName' created successfully")
                    } else {
                        showToast("Failed to create room: ${task.exception?.message}")
                    }
                }

            }.addOnFailureListener {
                showToast("Failed to retrieve username: ${it.message}")
            }

        }.addOnFailureListener {
            showToast("Failed to check room availability: ${it.message}")
        }
    }

    private fun loadChatrooms() {
        val chatroomsRef = FirebaseDatabase.getInstance().getReference("chatrooms")
        val currentTime = System.currentTimeMillis()

        chatroomsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val validRooms = mutableListOf<Chatroom>()

                for (roomSnapshot in snapshot.children) {
                    val chatroom = roomSnapshot.getValue(Chatroom::class.java)
                    val roomId = roomSnapshot.key ?: continue

                    if (chatroom != null) {
                        if (chatroom.expiresAt <= currentTime) {
                            // Expired room – delete it
                            chatroomsRef.child(roomId).removeValue()
                        } else {
                            // Valid room – collect for display
                            validRooms.add(chatroom.copy(id = roomId))
                        }
                    }
                }

                // Sort by createdAt descending (newest first)
                val sortedRooms = validRooms.sortedByDescending { it.createdAt }

                adapter.submitList(sortedRooms)
            }

            // Modify your showToast function:
            private fun showToast(message: String) {
                if (!isAdded || context == null) return
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }

            // Then keep your original onCancelled:
            override fun onCancelled(error: DatabaseError) {
                showToast("Failed to load chatrooms: ${error.message}") // Now safe
            }
        })
    }
    private fun checkExpiredRooms() {
        // Get the database reference
        val database = FirebaseDatabase.getInstance().reference

        // Create the query
        val query = database.child("chatrooms")
            .orderByChild("expiresAt")
            .endAt(System.currentTimeMillis().toDouble())  // Convert to Double for Firebase

        // Add the single value event listener
        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val deleteUpdates = hashMapOf<String, Any?>()

                snapshot.children.forEach { roomSnapshot ->
                    deleteUpdates["chatrooms/${roomSnapshot.key}"] = null
                }

                if (deleteUpdates.isNotEmpty()) {
                    database.updateChildren(deleteUpdates)
                        .addOnSuccessListener {
                            Log.d("ChatroomsFragment", "Cleaned ${deleteUpdates.size} expired rooms")
                        }
                        .addOnFailureListener { e ->
                            Log.e("ChatroomsFragment", "Failed to delete expired rooms", e)
                        }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatroomsFragment", "Failed to check expired rooms", error.toException())
            }
        })
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


