package com.example.billkmotolinkltd.ui.settings

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentReportsBinding
import com.example.billkmotolinkltd.databinding.FragmentSettingsBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding // Direct access without !! (null safety)
    private var userNameModal: Boolean = false
    private var pwModal: Boolean = false
    private var companyIncModal: Boolean = false
    private var companyIncModal2: Boolean = false
    private var commisionModal: Boolean = false
    private var companyInc: Double = 0.0
    private var companyStatus: String = "Unknown"
    private var lastDestinationId: Int? = null  // Track last visited screen

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding?.root // Use safe call (?)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fetchUserInfo()
        getCompanyIncome()


        binding?.btnChangeUsername?.setOnClickListener {
            userNameModal = !getUserModalStatus()

            if (userNameModal) {
                binding?.newUserNameLayout?.visibility = View.VISIBLE
                binding?.btnChangeUsername?.text = "Proceed"
            } else {
                binding?.newUserNameLayout?.visibility = View.GONE
                binding?.btnChangeUsername?.text = "Change Username"

                val enteredUsername = binding?.inputNewUsername?.text.toString().trim()
                val wordCount = enteredUsername.split("\\s+".toRegex()).size
                if (enteredUsername.isEmpty() || wordCount < 2) {
                    Toast.makeText(requireContext(), "Username is invalid", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                } else {
                    changeUsername()
                }
            }
        }

        binding?.btnChangePassword?.setOnClickListener {
            pwModal = !getPWStatus()

            if (pwModal) {
                binding?.changePasswordsModal?.visibility = View.VISIBLE
                binding?.btnChangePassword?.text = "Proceed"
            } else {
                binding?.changePasswordsModal?.visibility = View.GONE
                binding?.btnChangePassword?.text = "Change Password"

                val enteredNewPw = binding?.newPwInput?.text.toString().trim()
                val enteredOldPw = binding?.oldPwInput?.text.toString().trim()
                if (enteredNewPw.isEmpty() || enteredOldPw.isEmpty()) {
                    Toast.makeText(requireContext(), "Both passwords are required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                } else {
                    changePassword()
                }
            }
        }

        binding?.btnWithdrawCompanyMoney?.setOnClickListener {
            companyInc = getCInc()
            companyIncModal = !getCompanyIncModal()

            if (companyIncModal) {
                binding?.withdrawalLayout?.visibility = View.VISIBLE
                binding?.btnWithdrawCompanyMoney?.text = "Withdraw"
            } else {
                binding?.withdrawalLayout?.visibility = View.GONE
                binding?.btnWithdrawCompanyMoney?.text = "Withdraw Funds from Company to Personal Account"

                val enteredAmount = binding?.withdrawalInput?.text.toString().toDoubleOrNull() ?: 0.0
                if (enteredAmount > getCInc()) {
                    Toast.makeText(requireContext(), "You are withdrawing more than is available in the company.", Toast.LENGTH_SHORT).show()
                } else if (enteredAmount.toInt() == 0) {
                    // Show an error message or handle the case where the input is invalid
                    Toast.makeText(requireContext(), "Withdrawal amount is 0", Toast.LENGTH_SHORT).show()
                }
                else {

                    val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

                    // Custom title with red color
                    val title = SpannableString("Withdraw Company Funds")
                    title.setSpan(ForegroundColorSpan(Color.RED), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    // Custom message with black color
                    val message = SpannableString("Please confirm this action")
                    message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    alertDialog.setTitle(title)
                    alertDialog.setMessage(message)
                    alertDialog.setIcon(R.drawable.warn)

                    alertDialog.setPositiveButton("Yes") { _, _ ->
                        withdrawIncome(getCInc() - enteredAmount)
                    }

                    alertDialog.setNegativeButton("No") { dialog, _ ->
                        dialog.dismiss() // Dismiss dialog if user cancels
                    }

                    val dialog = alertDialog.create()
                    dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

                    dialog.show()
                }
            }
        }

        binding?.btnAddCompanyMoney?.setOnClickListener {
            companyIncModal2 = !getCompanyIncModal2()

            if (companyIncModal2) {
                binding?.addingLayout?.visibility = View.VISIBLE
                binding?.btnAddCompanyMoney?.text = "Add"
            } else {
                binding?.addingLayout?.visibility = View.GONE
                binding?.btnAddCompanyMoney?.text = "Transfer Funds from Personal to Company Account"

                val enteredAmount = binding?.addInput?.text.toString().toDoubleOrNull() ?: 0.0
                if (enteredAmount.toInt() == 0) {
                    // Show an error message or handle the case where the input is invalid
                    Toast.makeText(requireContext(), "You are adding a 0 amount", Toast.LENGTH_SHORT).show()
                } else {

                    val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

                    // Custom title with red color
                    val title = SpannableString("Add Money")
                    title.setSpan(ForegroundColorSpan(Color.GREEN), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    // Custom message with black color
                    val message = SpannableString("This money will be available for use in the company.")
                    message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    alertDialog.setTitle(title)
                    alertDialog.setMessage(message)
                    alertDialog.setIcon(R.drawable.success)

                    alertDialog.setPositiveButton("Yes") { _, _ ->
                        addIncome(getCInc() + enteredAmount)
                    }

                    alertDialog.setNegativeButton("No") { dialog, _ ->
                        dialog.dismiss() // Dismiss dialog if user cancels
                    }

                    val dialog = alertDialog.create()
                    dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

                    dialog.show()
                }
            }
        }

        binding?.btnPauseOperations?.setOnClickListener {
            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Caution")
            title.setSpan(ForegroundColorSpan(Color.RED), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString(
                "While this restriction persists, :\n\n" +
                        "1. All logged in users will stay logged in.\n\n" +
                        "2. Once a user logs out, they cannot log back in.\n\n" +
                        "3. Only CEOs will be allowed to log in.\n\n" +
                        "4. No clockouts can be done.\n\n" +
                        "5. No HR budgets can be submitted.\n\n" +
                        "6. No bikes or users can be added, deleted, discontinued, activated or deactivated whatsoever."
            )
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.warn)

            alertDialog.setPositiveButton("Pause") { _, _ ->
                pauseCompanyOperations()
            }

            alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
            }

            val dialog = alertDialog.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

            dialog.show()
        }

        binding?.btnContinueOperations?.setOnClickListener {
            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Confirm Action")
            title.setSpan(ForegroundColorSpan(Color.GREEN), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("Resume Operations")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.warn)

            alertDialog.setPositiveButton("Resume") { _, _ ->
                continueCompanyOperations()
            }

            alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
            }

            val dialog = alertDialog.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

            dialog.show()
        }

        binding?.btnChangeCConstant?.setOnClickListener {
            commisionModal = !getCommissionModal()

            if (commisionModal) {
                binding?.commissionCLayout?.visibility = View.VISIBLE
                binding?.btnChangeCConstant?.text = "Change"
            }
            else {
                binding?.commissionCLayout?.visibility = View.GONE
                binding?.btnChangeCConstant?.text = "Change Commission Constant"

                val enteredCommission = binding?.commissionInput?.text.toString().toDoubleOrNull() ?: 0.0
                if (enteredCommission > 0) {
                    val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

                    // Custom title with red color
                    val title = SpannableString("Change Constant")
                    title.setSpan(ForegroundColorSpan(Color.RED), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    // Custom message with black color
                    val message = SpannableString("This will be evaluated as a percentage out of 100%")
                    message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    alertDialog.setTitle(title)
                    alertDialog.setMessage(message)
                    alertDialog.setIcon(R.drawable.warn)

                    alertDialog.setPositiveButton("Change Commission") { _, _ ->
                        changeCommissionConstant(enteredCommission)
                    }

                    alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss() // Dismiss dialog if user cancels
                    }

                    val dialog = alertDialog.create()
                    dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

                    dialog.show()

                } else {
                    // Show an error message or handle the case where the input is invalid
                    Toast.makeText(requireContext(), "Invalid commission constant", Toast.LENGTH_SHORT).show()
                }
            }

        }
    }


    fun getUserModalStatus(): Boolean {
        return userNameModal
    }
    fun getPWStatus(): Boolean {
        return pwModal
    }
    fun getCompanyIncModal(): Boolean {
        return companyIncModal
    }
    fun getCompanyIncModal2(): Boolean {
        return companyIncModal2
    }
    fun getCInc(): Double {
        return companyInc
    }
    fun getCommissionModal(): Boolean {
        return commisionModal
    }

    private fun changeCommissionConstant(commissionAmount: Double) {
        binding?.cProgressBar?.visibility = View.VISIBLE
        binding?.btnChangeCConstant?.visibility = View.GONE
        binding?.commissionInput?.setText("")

        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("general").document("general_variables")

        docRef.get().addOnSuccessListener { document ->
            if (document.exists()) {

                docRef.update("commissionPercentage", commissionAmount)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Commission constant updated!", Toast.LENGTH_SHORT).show()
                        binding?.cProgressBar?.visibility = View.GONE
                        binding?.btnChangeCConstant?.visibility = View.VISIBLE
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Failed to make changes: ${e.message}", Toast.LENGTH_SHORT).show()
                        binding?.cProgressBar?.visibility = View.GONE
                        binding?.btnChangeCConstant?.visibility = View.VISIBLE
                    }
            } else {
                Toast.makeText(requireContext(), "Couldn't change constant!", Toast.LENGTH_SHORT).show()
                binding?.cProgressBar?.visibility = View.GONE
                binding?.btnChangeCConstant?.visibility = View.VISIBLE
            }
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Error setting constant: ${e.message}", Toast.LENGTH_SHORT).show()

            binding?.cProgressBar?.visibility = View.GONE
            binding?.btnChangeCConstant?.visibility = View.VISIBLE
        }
    }


    private fun pauseCompanyOperations() {
        binding?.progressBar5?.visibility = View.VISIBLE
        binding?.btnPauseOperations?.visibility = View.GONE

        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("general").document("general_variables")

        docRef.get().addOnSuccessListener { document ->
            if (document.exists()) {

                docRef.update("companyState", "Paused")
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Company status updated!", Toast.LENGTH_SHORT).show()

                        binding?.progressBar5?.visibility = View.GONE
                        binding?.btnContinueOperations?.visibility = View.VISIBLE
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Failed to add status: ${e.message}", Toast.LENGTH_SHORT).show()

                        binding?.progressBar5?.visibility = View.GONE
                        binding?.btnContinueOperations?.visibility = View.VISIBLE
                    }
            } else {
                Toast.makeText(requireContext(), "Couldn't pause operations!", Toast.LENGTH_SHORT).show()

                binding?.progressBar5?.visibility = View.GONE
                binding?.btnContinueOperations?.visibility = View.VISIBLE
            }
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Error setting state: ${e.message}", Toast.LENGTH_SHORT).show()

            binding?.progressBar5?.visibility = View.GONE
            binding?.btnContinueOperations?.visibility = View.VISIBLE
        }
    }

    private fun continueCompanyOperations() {
        binding?.progressBar5?.visibility = View.VISIBLE
        binding?.btnContinueOperations?.visibility = View.GONE

        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("general").document("general_variables")

        docRef.get().addOnSuccessListener { document ->
            if (document.exists()) {

                docRef.update("companyState", "Continuing")
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Company status updated!", Toast.LENGTH_SHORT).show()

                        binding?.progressBar5?.visibility = View.GONE
                        binding?.btnPauseOperations?.visibility = View.VISIBLE
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Failed to add status: ${e.message}", Toast.LENGTH_SHORT).show()

                        binding?.progressBar5?.visibility = View.GONE
                        binding?.btnPauseOperations?.visibility = View.VISIBLE
                    }
            } else {
                Toast.makeText(requireContext(), "Couldn't pause operations!", Toast.LENGTH_SHORT).show()

                binding?.progressBar5?.visibility = View.GONE
                binding?.btnPauseOperations?.visibility = View.VISIBLE
            }
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Error setting state: ${e.message}", Toast.LENGTH_SHORT).show()

            binding?.progressBar5?.visibility = View.GONE
            binding?.btnPauseOperations?.visibility = View.VISIBLE
        }
    }

    private fun changePassword() {
        val enteredNewPw = binding?.newPwInput?.text.toString().trim()
        val enteredOldPw = binding?.oldPwInput?.text.toString().trim()

        val enteredUsername = binding?.inputNewUsername?.text.toString()
        val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

        // Custom title with red color
        val title = SpannableString("Change Password")
        title.setSpan(ForegroundColorSpan(Color.RED), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Custom message with black color
        val message = SpannableString("Confirm this action.")
        message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        alertDialog.setTitle(title)
        alertDialog.setMessage(message)
        alertDialog.setIcon(R.drawable.warn)

        alertDialog.setPositiveButton("Yes") { _, _ ->
            val currentUser = FirebaseAuth.getInstance().currentUser
            currentUser?.let { user ->
                val credential = EmailAuthProvider.getCredential(user.email!!, enteredOldPw)
                user.reauthenticate(credential)
                    .addOnSuccessListener {
                        user.updatePassword(enteredNewPw)
                            .addOnSuccessListener {
                                Toast.makeText(requireContext(), "Password updated successfully", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { exception ->
                                Toast.makeText(requireContext(), "Failed to update password: ${exception.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(requireContext(), "Reauthentication failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        alertDialog.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss() // Dismiss dialog if user cancels
        }

        val dialog = alertDialog.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

        dialog.show()
    }

    private fun changeUsername() {
        val enteredUsername = binding?.inputNewUsername?.text.toString()
        val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

        // Custom title with red color
        val title = SpannableString("Change Username")
        title.setSpan(ForegroundColorSpan(Color.RED), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Custom message with black color
        val message = SpannableString("Confirm this action.")
        message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        alertDialog.setTitle(title)
        alertDialog.setMessage(message)
        alertDialog.setIcon(R.drawable.warn)

        alertDialog.setPositiveButton("Yes") { _, _ ->
            // Get current user
            val currentUser = FirebaseAuth.getInstance().currentUser
            currentUser?.let { user ->
                val userRef = FirebaseFirestore.getInstance().collection("users").document(user.uid)

                // Update the username in Firestore
                userRef.update("userName", enteredUsername)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Username updated successfully", Toast.LENGTH_SHORT).show()
                        binding?.usernameTextView?.text = enteredUsername // Update UI
                        binding?.inputNewUsername?.setText("")
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(requireContext(), "Failed to update username: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
            }

        }

        alertDialog.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss() // Dismiss dialog if user cancels
        }

        val dialog = alertDialog.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

        dialog.show()

    }

    private fun fetchUserInfo() {
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
            val userId = currentUser.uid // Unique user ID
            val userRef = FirebaseFirestore.getInstance().collection("users").document(userId)

            userRef.get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {

                        val username = document.getString("userName") ?: ""
                        val email = document.getString("email") ?: ""
                        var phone = document.getString("phoneNumber") ?: ""
                        val gender = document.getString("gender") ?: ""
                        var rank = document.getString("userRank") ?: ""

                        if (phone.length == 9) {
                            phone = "0$phone"
                        }
                        if (rank == "CEO") {
                            rank = "Chief Executive Officer"
                            binding?.companyIncomeLayout?.visibility = View.VISIBLE
                            binding?.ceoDiv?.visibility = View.VISIBLE
                        } else if (rank == "Admin") {
                            rank = "Administrator"
                            binding?.companyIncomeLayout?.visibility = View.VISIBLE
                        } else if (rank == "HR") {
                            rank = "Human Resource"
                        }

                        // Use safe call and check for null before updating views
                        if (gender == "Male") {
                            binding?.malePImage?.visibility = View.VISIBLE
                            binding?.femalePImage?.visibility = View.GONE
                        } else if (gender == "Female") {
                            binding?.femalePImage?.visibility = View.VISIBLE
                            binding?.malePImage?.visibility = View.GONE
                        }

                        activity?.runOnUiThread {
                            // Safely updating UI elements with the data
                            if (isAdded && binding != null) {
                                binding?.usernameTextView?.text = username
                                binding?.emailTextView?.text = email
                                binding?.phoneTextView?.text = phone
                                binding?.rankTextView?.text = rank
                            }
                        }
                        // these are universal and independent of ranks
                        binding?.actionsLayout?.visibility = View.VISIBLE
                        binding?.btnChangeUsername?.visibility = View.VISIBLE
                        binding?.btnChangePassword?.visibility = View.VISIBLE
                        binding?.mainProgressBar?.visibility = View.GONE

                    } else {
                        Log.e("UserInfo", "No user data found")
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "No user data found.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("UserInfo", "Error fetching user data: ${exception.message}")
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Failed to fetch user data.", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            Log.e("UserInfo", "No user is logged in")
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "No user is logged in.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun withdrawIncome(amount: Double) {
        binding?.progressBar3?.visibility = View.VISIBLE
        binding?.btnWithdrawCompanyMoney?.visibility = View.GONE

        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("general").document("general_variables")

        docRef.get().addOnSuccessListener { document ->
            if (document.exists()) {

                docRef.update("companyIncome", amount)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Withdrawal successful!", Toast.LENGTH_SHORT).show()
                        companyInc = amount
                        binding?.withdrawalInput?.text?.clear()
                        val formattedIncome = NumberFormat.getNumberInstance(Locale.US).apply {
                            minimumFractionDigits = 2
                            maximumFractionDigits = 2
                        }.format(amount)
                        binding?.incomeValue?.apply {
                            text = "Ksh. $formattedIncome"
                            setTextColor(
                                if (amount < 5000)
                                    ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                                else
                                    ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
                            )
                        }

                        binding?.progressBar3?.visibility = View.GONE
                        binding?.btnWithdrawCompanyMoney?.visibility = View.VISIBLE
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Failed to withdraw: ${e.message}", Toast.LENGTH_SHORT).show()

                        binding?.progressBar3?.visibility = View.GONE
                        binding?.btnWithdrawCompanyMoney?.visibility = View.VISIBLE
                    }
            } else {
                Toast.makeText(requireContext(), "Income record not found!", Toast.LENGTH_SHORT).show()

                binding?.progressBar3?.visibility = View.GONE
                binding?.btnWithdrawCompanyMoney?.visibility = View.VISIBLE
            }
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Error fetching income: ${e.message}", Toast.LENGTH_SHORT).show()

            binding?.progressBar3?.visibility = View.GONE
            binding?.btnWithdrawCompanyMoney?.visibility = View.VISIBLE
        }
    }

    private fun addIncome(amount: Double) {
        binding?.progressBar4?.visibility = View.VISIBLE
        binding?.btnAddCompanyMoney?.visibility = View.GONE

        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("general").document("general_variables")

        docRef.get().addOnSuccessListener { document ->
            if (document.exists()) {

                docRef.update("companyIncome", amount)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Added successfully!", Toast.LENGTH_SHORT).show()
                        companyInc = amount
                        binding?.addInput?.text?.clear()
                        val formattedIncome = NumberFormat.getNumberInstance(Locale.US).apply {
                            minimumFractionDigits = 2
                            maximumFractionDigits = 2
                        }.format(amount)
                        binding?.incomeValue?.apply {
                            text = "Ksh. $formattedIncome"
                            setTextColor(
                                if (amount >= 5000)
                                    ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
                                else
                                    ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                            )
                        }

                        binding?.progressBar4?.visibility = View.GONE
                        binding?.btnAddCompanyMoney?.visibility = View.VISIBLE
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Failed to add: ${e.message}", Toast.LENGTH_SHORT).show()

                        binding?.progressBar4?.visibility = View.GONE
                        binding?.btnAddCompanyMoney?.visibility = View.VISIBLE
                    }
            } else {
                Toast.makeText(requireContext(), "Income record not found!", Toast.LENGTH_SHORT).show()

                binding?.progressBar4?.visibility = View.GONE
                binding?.btnAddCompanyMoney?.visibility = View.VISIBLE
            }
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Error fetching income: ${e.message}", Toast.LENGTH_SHORT).show()

            binding?.progressBar4?.visibility = View.GONE
            binding?.btnAddCompanyMoney?.visibility = View.VISIBLE
        }
    }

//company status is also here
    private fun getCompanyIncome() {
        val ref = FirebaseFirestore.getInstance().collection("general").document("general_variables")
        ref.get()
            .addOnSuccessListener { document ->
                if (!isAdded || view == null) return@addOnSuccessListener // Prevent crash

                viewLifecycleOwner.lifecycleScope.launch {
                    val companyIncome = document.getDouble("companyIncome") ?: 0.0
                    val companyState = document.getString("companyState") ?: "Continuing"
                    companyInc = companyIncome
                    companyStatus = companyState.toString()

                    // Format the number with commas and two decimal places
                    val formattedIncome = NumberFormat.getNumberInstance(Locale.US).apply {
                        minimumFractionDigits = 2
                        maximumFractionDigits = 2
                    }.format(companyIncome)

                    binding?.incomeValue?.apply {
                        text = "Ksh. $formattedIncome"
                        setTextColor(
                            if (companyIncome < 5000)
                                ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                            else
                                ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
                        )
                    }

                    if (companyState == "Continuing") {
                        binding?.btnPauseOperations?.visibility = View.VISIBLE
                    } else if (companyState == "Paused") {
                        binding?.btnContinueOperations?.visibility = View.VISIBLE
                    }
                }
            }
            .addOnFailureListener {
                if (!isAdded || view == null) return@addOnFailureListener
                binding?.incomeValue?.text = "Error loading commission" // Safe access
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Nullify the binding reference to prevent memory leaks
    }
}
