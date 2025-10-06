package com.example.billkmotolinkltd.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.PictureDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ImageSpan
import android.view.MotionEvent
import android.widget.TextView
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.ActivityChatBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.FirebaseDatabase
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.GridLayoutManager
import kotlin.properties.Delegates
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import com.caverock.androidsvg.SVG


/*this is the actual chatroom UI*/

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var chatAdapter: ChatMessageAdapter
    private lateinit var chatroomId: String
    private lateinit var chatroomCreatorUID: String

    private lateinit var chatroomName: String
    private var chatroomExpiry by Delegates.notNull<Long>()
    private lateinit var currentUserId: String
    private lateinit var currentUserName: String
    private val messages = mutableListOf<ChatMessage>()

    private val dbRef by lazy { FirebaseDatabase.getInstance().getReference("chatrooms") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatroomId = intent.getStringExtra("chatroom_id") ?: return finish()
        chatroomName = intent.getStringExtra("chatroom_name") ?: "Chatroom"
        chatroomExpiry = intent.getLongExtra("chatroom_expiry", 0L)
        chatroomCreatorUID = intent.getStringExtra("chatroom_creator") ?: ""

        val actionBar = supportActionBar
        actionBar?.setDisplayShowCustomEnabled(true)
        actionBar?.setDisplayShowTitleEnabled(false) // Hide default title

        val customView = layoutInflater.inflate(R.layout.custom_action_bar_title, null)
        val titleTextView = customView.findViewById<TextView>(R.id.actionBarTitle)
        titleTextView.text = chatroomName

        actionBar?.customView = customView

        customView.setOnClickListener {
            val intent = Intent(this, ChatroomSettingsActivity::class.java)
            intent.putExtra("chatroomId", chatroomId)
            intent.putExtra("chatroomName", chatroomName)
            intent.putExtra("chatroomCreator", chatroomCreatorUID)
            startActivity(intent)
        }

        // supportActionBar?.title = chatroomName
        supportActionBar?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            title = chatroomName
            elevation = 0f
        }

        setupStatusBar()

        currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return finish()

        // Fetch username before proceeding
        val firestoreRef = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        firestoreRef.collection("users").document(currentUserId).get()
            .addOnSuccessListener { document ->
                currentUserName = document.getString("userName") ?: "Unknown"
                setupRecyclerView()     // ✅ Initialize after getting user details
                setupListeners()
                listenForMessages()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load user info", Toast.LENGTH_SHORT).show()
                finish()
            }

        // setRandomChatBackground()

        // If user taps the EditText, hide emoji panel and show keyboard
        @SuppressLint("ClickableViewAccessibility")
        binding.messageInput.setOnTouchListener { v, event ->
            if (binding.emojiPanel.isVisible) {
                showKeyboard(binding.messageInput)
                v.performClick() // Important for accessibility
                true
            } else {
                false
            }
        }
    }

    private fun showKeyboard(editText: EditText) {
        editText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun setupStatusBar() {
        supportActionBar?.title = chatroomName
        val isDarkTheme = when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }

        window.statusBarColor = ContextCompat.getColor(this, R.color.brown4)

        val decorView = window.decorView
        if (isDarkTheme) {
            // Dark mode → light icons
            decorView.systemUiVisibility = 0
        } else {
            // Light mode → dark icons (black)
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

//    private fun setRandomChatBackground() {
//        val svg = SVG.getFromResource(this, R.raw.chat_bg) // put chat_bg.svg in res/raw
//        val drawable = PictureDrawable(svg.renderToPicture())
//        binding.chatRoot.background = drawable
//    }

    private fun setupRecyclerView() {
        chatAdapter = ChatMessageAdapter(
            messages,
            currentUserId,
            currentUserName,
        )
        binding.recyclerViewMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }


    private fun setupListeners() {
        binding.sendButton.setOnClickListener {
            val messageText = binding.messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
            }
        }
    }

    private fun sendMessage(messageText: String) {
        val chatroomRef = dbRef.child(chatroomId)
        val currentUID = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Step 1: Check if chatroom still exists
        chatroomRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    AlertDialog.Builder(this@ChatActivity)
                        .setTitle("Chatroom Closed")
                        .setMessage("This chatroom no longer exists.")
                        .setPositiveButton("OK") { _, _ -> finish() }
                        .setCancelable(false)
                        .show()
                    return
                }

                // Step 2: Check if current user is in approvedParticipants
                val approvedListSnapshot = snapshot.child("approvedParticipants")
                val isApproved = approvedListSnapshot.children.any { it.getValue(String::class.java) == currentUID }

                if (!isApproved) {
                    AlertDialog.Builder(this@ChatActivity)
                        .setTitle("Access Denied")
                        .setMessage("You are no longer a member of this chatroom.")
                        .setPositiveButton("OK") { _, _ -> finish() }
                        .setCancelable(false)
                        .show()
                    return
                }

                // Step 3: Send the message
                val messageId = chatroomRef.child("messages").push().key ?: return

                val message = ChatMessage(
                    messageId = messageId,
                    senderId = currentUID,
                    senderName = currentUserName,
                    message = messageText
                )

                chatroomRef.child("messages").child(messageId).setValue(message)
                    .addOnSuccessListener {
                        binding.messageInput.setText("")
                    }
                    .addOnFailureListener {
                        Toast.makeText(this@ChatActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ChatActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun listenForMessages() {
        dbRef.child(chatroomId).child("messages").orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    messages.clear()
                    for (messageSnap in snapshot.children) {
                        val message = messageSnap.getValue(ChatMessage::class.java)
                        if (message != null) messages.add(message)
                    }
                    chatAdapter.notifyDataSetChanged()
                    binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@ChatActivity, "Error loading messages", Toast.LENGTH_SHORT).show()
                }
            })
    }
}
