package com.example.billkmotolinkltd.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.billkmotolinkltd.databinding.FragmentLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth  // Firebase Authentication instance

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val loginViewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        val root: View = binding.root

        auth = FirebaseAuth.getInstance() // Initialize Firebase Auth

        binding.buttonLogin.setOnClickListener {
            val email = binding.editTextEmail.text.toString().trim()
            val password = binding.editTextPassword.text.toString().trim()
            loginUser(email, password)
        }

        return root
    }

    private fun loginUser(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(requireContext(), "Email & Password required", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.buttonLogin.isEnabled = false

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
                        binding.buttonLogin.isEnabled = true
                        Toast.makeText(requireContext(), "User not found.", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    val userDoc = userDocs.documents[0]
                    val isDeleted = userDoc.getBoolean("isDeleted") == true
                    val isActive = userDoc.getBoolean("isActive") == true
                    val thisUserRank = userDoc.getString("userRank") ?: "Regular"

                    // prevent login if company is paused. only ceo to login
                    if (companyStatus == "Paused" && thisUserRank != "CEO") {
                        binding.progressBar.visibility = View.GONE
                        binding.buttonLogin.isEnabled = true
                        Toast.makeText(requireContext(), "Company is currently paused. Login not allowed.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    if (isDeleted) {
                        binding.progressBar.visibility = View.GONE
                        binding.buttonLogin.isEnabled = true
                        Toast.makeText(requireContext(), "Account has been deleted.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    if (!isActive) {
                        binding.progressBar.visibility = View.GONE
                        binding.buttonLogin.isEnabled = true
                        Toast.makeText(requireContext(), "Account is not active. Contact admin.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    // Step 3: If all checks pass, authenticate user
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(requireActivity()) { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(requireContext(), "Login Successful", Toast.LENGTH_SHORT).show()
                                // Navigate to Home or Dashboard
                            } else {
                                binding.progressBar.visibility = View.GONE
                                binding.buttonLogin.isEnabled = true
                                Toast.makeText(requireContext(), "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    binding.buttonLogin.isEnabled = true
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }.addOnFailureListener { e ->
            binding.progressBar.visibility = View.GONE
            binding.buttonLogin.isEnabled = true
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
