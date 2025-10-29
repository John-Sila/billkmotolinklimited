package com.billkmotolink.ltd.ui.complain

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.billkmotolink.ltd.R
import com.billkmotolink.ltd.databinding.FragmentComplainBinding
import com.billkmotolink.ltd.ui.Utility
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ComplainFragment: Fragment() {
    private var _binding: FragmentComplainBinding? = null
    private val binding get() = _binding!!
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentComplainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnPostComplain.setOnClickListener {

            val complainString = binding.complainDescription.text.toString().trim()
            if (complainString.isEmpty()) {
                Toast.makeText(requireContext(), "Please write a valid complaint.", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            val alertDialog = AlertDialog.Builder(requireContext())
            val title = SpannableString("Complaints")
            title.setSpan(ForegroundColorSpan(Color.RED), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            val message = SpannableString("Confirm this action.")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.warn)

            alertDialog.setPositiveButton("Post") { _, _ ->
                postComplain()
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

    private fun ComplainFragment.postComplain() {
        val message = binding.complainDescription.text.toString().trim()
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Disable button to prevent multiple clicks
        binding.btnPostComplain.isEnabled = false
        binding.btnPostComplain.text = "Posting..."

        lifecycleScope.launch {
            try {
                val complainData = hashMapOf(
                    "message" to message,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "uid" to uid
                )

                FirebaseFirestore.getInstance()
                    .collection("complains")
                    .document("user_complains")   // fixed document
                    .collection("all")            // subcollection where all go
                    .add(complainData)
                    .await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Complaint submitted", Toast.LENGTH_SHORT).show()
                    binding.complainDescription.text.clear()
                    binding.btnPostComplain.isEnabled = true
                    binding.btnPostComplain.text = "Post Complaint"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.btnPostComplain.isEnabled = true
                    binding.btnPostComplain.text = "Post Complaint"
                }
            } finally {
                val roles = listOf("Admin", "CEO", "Systems, IT")
                Utility.notifyAdmins("Someone is complaining.", "Complaints", roles)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel() // Clean up coroutines
    }
}

