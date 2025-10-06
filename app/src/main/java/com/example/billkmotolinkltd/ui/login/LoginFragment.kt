package com.example.billkmotolinkltd.ui.login

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentLoginBinding
import com.example.billkmotolinkltd.ui.Utility
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import android.view.Gravity

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth  // Firebase Authentication instance

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        ViewModelProvider(this)[LoginViewModel::class.java]

        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        val root: View = binding.root

        auth = FirebaseAuth.getInstance() // Initialize Firebase Auth

        binding.buttonLogin.setOnClickListener {
            val email = binding.editTextEmail.text.toString().trim()
            val password = binding.editTextPassword.text.toString().trim()
            loginUser(email, password)
        }

        binding.forgotPasswordLink.setOnClickListener {
            binding.loginModal.visibility = View.GONE
            binding.forgotPasswordModal.visibility = View.VISIBLE
            binding.forgotPasswordLink.visibility = View.GONE
        }

        binding.goBack.setOnClickListener {
            binding.loginModal.visibility = View.VISIBLE
            binding.forgotPasswordModal.visibility = View.GONE
            binding.forgotPasswordLink.visibility = View.VISIBLE
        }

        binding.buttonSendLink.setOnClickListener {
            val email = binding.forgotPwdEmail.text.toString().trim()
            if (email.isEmpty() || email.length < 4) {
                Toast.makeText(requireContext(), "Email invalid.", Toast.LENGTH_SHORT).show()
            } else {
                val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

                // Custom title with red color
                val title = SpannableString("Confirm")
                title.setSpan(
                    ForegroundColorSpan(Color.GREEN),
                    0,
                    title.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                // Custom message with black color
                val message =
                    SpannableString("Reset your password")
                message.setSpan(
                    ForegroundColorSpan(Color.GRAY),
                    0,
                    message.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                alertDialog.setTitle(title)
                alertDialog.setMessage(message)
                alertDialog.setIcon(R.drawable.success)

                alertDialog.setPositiveButton("Send link") { _, _ ->
                    resetPassword(email)
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

        return root
    }

    private fun resetPassword(email: String) {
        if (!isAdded || view == null) return // Prevent crash
        binding.fpProgressBar.visibility = View.VISIBLE
        binding.buttonSendLink.visibility = View.GONE

        // Check Firestore first
        val db = FirebaseFirestore.getInstance()
        db.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    // Email exists in users collection -> proceed
                    auth.sendPasswordResetEmail(email)
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Check your Google® mail inbox.", Toast.LENGTH_LONG).show()
                            binding.loginModal.visibility = View.VISIBLE
                            binding.forgotPasswordModal.visibility = View.GONE
                            binding.forgotPasswordLink.visibility = View.VISIBLE

                            binding.fpProgressBar.visibility = View.GONE
                            binding.buttonSendLink.visibility = View.VISIBLE

                            lifecycleScope.launch {
                                Utility.postTrace("$email is trying to reset account password.")
                            }
                        }
                        .addOnFailureListener {
                            binding.fpProgressBar.visibility = View.GONE
                            binding.buttonSendLink.visibility = View.VISIBLE
                            Toast.makeText(requireContext(), "Failed to send reset email", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    // No such email in users collection
                    binding.fpProgressBar.visibility = View.GONE
                    binding.buttonSendLink.visibility = View.VISIBLE
                    val toast = Toast.makeText(requireContext(), "This email is not registered in BILLK", Toast.LENGTH_LONG)
                    toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 0)
                    toast.show()
                }
            }
            .addOnFailureListener { e ->
                binding.fpProgressBar.visibility = View.GONE
                binding.buttonSendLink.visibility = View.VISIBLE
                Snackbar.make(
                    requireView(),
                    "We couldn't check your account! Try again later.",
                    Snackbar.LENGTH_LONG
                ).setAction("OK") {
                    // Handle OK action
                }.show()

            }
    }


    private fun loginUser(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(requireContext(), "Email & Password required", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.buttonLogin.visibility = View.GONE

        val firestore = FirebaseFirestore.getInstance()

        // Step 1: Check company status
        val companyRef = firestore.collection("general").document("general_variables")
        companyRef.get().addOnSuccessListener { companyDoc ->
            val companyStatus = companyDoc.getString("companyState") ?: "Unknown"

            // Step 2: Get user details from Firestore where email == email
            firestore.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener { userDocs ->
                    if (userDocs.isEmpty) {
                        binding.progressBar.visibility = View.GONE
                        binding.buttonLogin.visibility = View.VISIBLE
                        Toast.makeText(requireContext(), "User not found.", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    val userDoc = userDocs.documents[0]
                    val isDeleted = userDoc.getBoolean("isDeleted") == true
                    val isActive = userDoc.getBoolean("isActive") == true
                    val thisUserRank = userDoc.getString("userRank") ?: "Regular"
                    val thisUserName = userDoc.getString("userName") ?: "An unidentified user"

                    // prevent login if company is paused. only ceo to login
                    if (companyStatus == "Paused" && thisUserRank != "CEO") {
                        binding.progressBar.visibility = View.GONE
                        binding.buttonLogin.visibility = View.VISIBLE
                        Toast.makeText(requireContext(), "Company is currently paused. Login not allowed.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    if (isDeleted) {
                        binding.progressBar.visibility = View.GONE
                        binding.buttonLogin.visibility = View.VISIBLE
                        Toast.makeText(requireContext(), "Account has been deleted.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    if (!isActive) {
                        binding.progressBar.visibility = View.GONE
                        binding.buttonLogin.visibility = View.VISIBLE
                        Toast.makeText(requireContext(), "Account is not active. Contact admin.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    // Step 3: If all checks pass, authenticate user
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(requireActivity()) { task ->
                            if (task.isSuccessful) {
                                val currentUser = FirebaseAuth.getInstance().currentUser
                                if (currentUser != null) {

                                    lifecycleScope.launch {
                                        Utility.postTrace("Logged in.")
                                        val roles = listOf("Admin", "CEO", "Systems, IT")
                                        Utility.notifyAdmins("$thisUserName just logged in.", "BML Authenticator", roles)
                                    }
                                    Toast.makeText(requireContext(), "Login Successful", Toast.LENGTH_SHORT).show()
                                } else {
                                    Log.e("AuthState", "User is still not available!")
                                }
                            } else {
                                binding.progressBar.visibility = View.GONE
                                binding.buttonLogin.visibility = View.VISIBLE
                                Toast.makeText(requireContext(), "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    binding.buttonLogin.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }.addOnFailureListener { e ->
            binding.progressBar.visibility = View.GONE
            binding.buttonLogin.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
