package com.example.billkmotolinkltd.ui.users

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.billkmotolinkltd.LoadingActivity
import com.example.billkmotolinkltd.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.billkmotolinkltd.databinding.FragmentUsersBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider

class UsersFragment : Fragment() {

    private var _binding: FragmentUsersBinding? = null
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var previousUserId: String? = auth.currentUser?.uid
    private var previousUserEmail: String? = null
    private var previousUserPassword: String? = null
    private var loadingDialog: AlertDialog? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spinner: Spinner = binding.userRank
        val spinner2: Spinner = binding.actionForUser
        val spinner3: Spinner = binding.userGender

        // Define options
        val options = arrayOf("Rider", "Admin", "HR")
        val options2 = arrayOf("Activate", "Deactivate", "Discontinue")
        val options3 = arrayOf("Male", "Female")

        // Create an adapter
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, options)
        val adapter2 = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, options2)
        val adapter3 = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, options3)

        // Set the adapter to the spinner
        spinner.adapter = adapter // ranks
        spinner2.adapter = adapter2 // action
        spinner3.adapter = adapter3 // gender

        // Handle selection events
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                // Hide all layouts first
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        fun isNumeric(value: String): Boolean {
            return value.all { it.isDigit() }
        }

        binding.btnAddUser.setOnClickListener {
            val fullName = binding.inputFullName.text.toString().trim()
            val email = binding.inputEmail.text.toString().trim()
            val password = binding.inputPassword.text.toString().trim()
            val idNumber = binding.inputIDNumber.text.toString().trim()
            val phoneNumber = binding.inputPhoneNumber.text.toString().trim()
            val userRank = spinner.selectedItem.toString()
            val userGender = spinner3.selectedItem.toString()
            // ✅ Check if all fields are filled
            if (fullName.isBlank() || email.isBlank() || password.isBlank() || idNumber.isBlank() || phoneNumber.isBlank() || userRank.isBlank() || userGender.isBlank()) {
                Toast.makeText(requireContext(), "All fields are required", Toast.LENGTH_SHORT).show()
            } else if (fullName.trim().split("\\s+".toRegex()).size < 2) {
                Toast.makeText(requireContext(), "Full name must have at least two words", Toast.LENGTH_SHORT).show()
            } else if (!isNumeric(idNumber) || !isNumeric(phoneNumber)) {
                Toast.makeText(requireContext(), "Both ID number and phone number must be in digit form", Toast.LENGTH_SHORT).show()
            } else {
                val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

                // Custom title with red color
                val title = SpannableString("Create User")
                title.setSpan(ForegroundColorSpan(Color.BLUE), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                // Custom message with black color
                val message = SpannableString("Please confirm user creation.")
                message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                alertDialog.setTitle(title)
                alertDialog.setMessage(message)
                alertDialog.setIcon(R.drawable.success)

                alertDialog.setPositiveButton("Yes") { _, _ ->
                    showPasswordModal(requireContext())
                }

                alertDialog.setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss() // Dismiss dialog if user cancels
                }

                val dialog = alertDialog.create()
                dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

                dialog.show()
            }
        }

        binding.btnUpdateUserStatus.setOnClickListener {
            val selectedUser = binding.userToManage.selectedItem as? String
            val selectedAction = binding.actionForUser.selectedItem as? String

            if (selectedUser.isNullOrEmpty() || selectedAction.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Please select a user and an action", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Confirm Action")
            title.setSpan(ForegroundColorSpan(Color.RED), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("Engage this action.")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.warn)

            alertDialog.setPositiveButton("Yes") { _, _ ->
                updateUserStatus(selectedUser, selectedAction)
            }

            alertDialog.setNegativeButton("No") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
            }

            val dialog = alertDialog.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

            dialog.show()


        }
        loadUserNames()
    }

    private fun updateUserStatus(userName: String, action: String) {
        binding.btnUpdateUserStatus.isEnabled = false
        val db = FirebaseFirestore.getInstance()
        val usersRef = db.collection("users")

        // Find the user document by userName
        usersRef.whereEqualTo("userName", userName).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                for (document in documents) {
                    val userDocRef = usersRef.document(document.id) // Get document ID

                    val updates = when (action) {
                        "Activate" -> mapOf("isActive" to true)
                        "Deactivate" -> mapOf("isActive" to false)
                        "Discontinue" -> mapOf("isActive" to false, "isDeleted" to true)
                        else -> return@addOnSuccessListener
                    }

                    userDocRef.update(updates)
                        .addOnSuccessListener {
                            if (!isAdded || view == null || _binding == null) return@addOnSuccessListener // Prevent crash

                            Toast.makeText(requireContext(), "User status updated", Toast.LENGTH_SHORT).show()
                            binding.btnUpdateUserStatus.isEnabled = true
                        }
                        .addOnFailureListener { e ->
                            if (!isAdded || view == null || _binding == null) return@addOnFailureListener // Prevent crash

                            Toast.makeText(requireContext(), "Failed to update: ${e.message}", Toast.LENGTH_SHORT).show()
                            binding.btnUpdateUserStatus.isEnabled = true
                        }
                }
            }
            .addOnFailureListener addOnSuccessListener@{ e ->
                if (!isAdded || view == null || _binding == null) return@addOnSuccessListener // Prevent crash

                Toast.makeText(requireContext(), "Error fetching user: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.btnUpdateUserStatus.isEnabled = true
            }
    }


    private fun loadUserNames() {
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users")

        userRef.get()
            .addOnSuccessListener { documents ->
                if (_binding == null) return@addOnSuccessListener  // Prevent crash

                val userNames = mutableListOf<String>()
                for (document in documents) {
                    document.getString("userName")?.let { userNames.add(it) }
                }

                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, userNames)
                _binding?.userToManage?.adapter = adapter  // Use safe access
            }
            .addOnFailureListener { exception ->
                if (_binding != null) {
                    Toast.makeText(requireContext(), "Failed to load users: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }

        val cRef = db.collection("general").document("general_variables")
        cRef.get().addOnSuccessListener { companyDoc ->
            val companyStatus = companyDoc.getString("companyState") ?: "Unknown"
            if (!isAdded || view == null || _binding == null) return@addOnSuccessListener // Prevent crash
            if (companyStatus == "Paused") {
                binding.btnOperationsPaused1.visibility = View.VISIBLE
                binding.btnAddUser.visibility = View.GONE

                binding.btnOperationsPaused2.visibility = View.VISIBLE
                binding.btnUpdateUserStatus.visibility = View.GONE
            } else if (companyStatus == "Continuing") {
                binding.btnOperationsPaused1.visibility = View.GONE
                binding.btnAddUser.visibility = View.VISIBLE

                binding.btnOperationsPaused2.visibility = View.GONE
                binding.btnUpdateUserStatus.visibility = View.VISIBLE
            }
        }
    }

    private fun showLoadingDialog(context: Context) {
        val intent = Intent(context, LoadingActivity::class.java)
        context.startActivity(intent)
    }

    private fun dismissLoadingDialog(context: Context) {
        val intent = Intent(context, LoadingActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        context.startActivity(intent)
    }

    private fun showPasswordModal(context: Context) {
        val builder = AlertDialog.Builder(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_password_input, null)
        val inputPassword = view.findViewById<EditText>(R.id.editTextPassword)

        builder.setView(view)
        builder.setTitle("Confirm Your Identity")
        builder.setMessage("Enter your password to create a new user.")
        builder.setPositiveButton("Confirm") { _, _ ->
            val password = inputPassword.text.toString().trim()
            if (password.isEmpty()) {
                Toast.makeText(context, "Password is required", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            reAuthenticateUser(password, context)
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun reAuthenticateUser(password: String, context: Context) {
        showLoadingDialog(context)
        val currentUser = auth.currentUser ?: return
        val email = currentUser.email ?: return

        val credential = EmailAuthProvider.getCredential(email, password)
        currentUser.reauthenticate(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Store the current user’s email and password for later login
                    previousUserEmail = email
                    previousUserPassword = password

                    // Now proceed with user registration
                    createNewUser(context)
                } else {
                    dismissLoadingDialog(context)
                    findNavController().navigate(R.id.nav_user)
                    Toast.makeText(context, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun createNewUser(context: Context) {
        val fullName = binding.inputFullName.text.toString().trim()
        val email = binding.inputEmail.text.toString().trim()
        val password = binding.inputPassword.text.toString().trim()
        val idNumber = binding.inputIDNumber.text.toString().trim()
        val phoneNumber = binding.inputPhoneNumber.text.toString().trim()
        val userRank = binding.userRank.selectedItem.toString()
        val userGender = binding.userGender.selectedItem.toString()

        // Check if all fields are filled
        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || idNumber.isEmpty() || phoneNumber.isEmpty() || userRank.isEmpty() || userGender.isBlank() ) {
            Toast.makeText(context, "All fields are required", Toast.LENGTH_SHORT).show()
            dismissLoadingDialog(context)
            return
        }

        // Check if full name has at least two words
        if (fullName.split("\\s+".toRegex()).size < 2) {
            Toast.makeText(context, "Full name must have at least two words", Toast.LENGTH_SHORT).show()
            dismissLoadingDialog(context)
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val newUserId = auth.currentUser?.uid ?: return@addOnCompleteListener
                    saveUserToFirestore(context, newUserId, fullName, email, idNumber, phoneNumber, userRank, userGender)
                } else {
                    dismissLoadingDialog(context)
                    Toast.makeText(context, "User creation failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun saveUserToFirestore(context: Context, userId: String, fullName: String, email: String, idNumber: String, phoneNumber: String, userRank: String, userGender: String) {
        val user = hashMapOf(
            "userName" to fullName,
            "email" to email,
            "idNumber" to idNumber,
            "phoneNumber" to phoneNumber,
            "userRank" to userRank,
            "createdAt" to Timestamp.now(),
            "isVerified" to false,
            "isDeleted" to false,
            "isActive" to true,
            "lastClockDate" to Timestamp.now(),
            "netClockedLastly" to 0,
            "pendingAmount" to 0,
            "unpushedAmount" to 0,
            "currentInAppBalance" to 0,
            "dailyTarget" to 2200,
            "gender" to userGender,
            "sundayTarget" to 670,
            "isWorkingOnSunday" to false
        )

        db.collection("users").document(userId)
            .set(user)
            .addOnSuccessListener {
                Toast.makeText(context, "User Registered Successfully", Toast.LENGTH_SHORT).show()
                if (!isAdded || view == null || _binding == null) return@addOnSuccessListener
                binding.inputFullName.setText("")
                binding.inputEmail.setText("")
                binding.inputPassword.setText("")
                binding.inputIDNumber.setText("")
                binding.inputPhoneNumber.setText("")
                restorePreviousSession(context)
            }
            .addOnFailureListener { e ->
                dismissLoadingDialog(context)
                Toast.makeText(context, "Failed to save user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun restorePreviousSession(context: Context) {
        if (previousUserEmail != null && previousUserPassword != null) {
            auth.signOut()

            auth.signInWithEmailAndPassword(previousUserEmail!!, previousUserPassword!!)
                .addOnCompleteListener { task ->
                    requireActivity().runOnUiThread {
                        dismissLoadingDialog(context)
                        findNavController().navigate(R.id.nav_user)
                        if (task.isSuccessful) {
                            Toast.makeText(context, "Session Restored", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to restore session", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        } else {
            requireActivity().runOnUiThread {
                dismissLoadingDialog(context)
                Toast.makeText(context, "Previous session could not be restored. Please login.", Toast.LENGTH_SHORT).show()
                FirebaseAuth.getInstance().signOut()
            }
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}