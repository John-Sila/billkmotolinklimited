package com.example.billkmotolinkltd.ui.settings

import android.graphics.Color
import android.os.Build
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
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentSettingsBinding
import com.example.billkmotolinkltd.ui.Utility
import com.example.billkmotolinkltd.ui.formatIncome
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
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
        checkIfHrBudgetExists()
        getWeeklyTotals()


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
                        authenticateUser(
                            onSuccess = {
                                // Secure operation here
                                Toast.makeText(context, "Authenticated!", Toast.LENGTH_SHORT).show()
                                withdrawIncome(getCInc() - enteredAmount, enteredAmount)
                            },
                            onFailure = {
                                binding?.withdrawalInput?.text?.clear()
                                Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
                            }
                        )
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
                    val message = SpannableString("The funds will be available for use in the company.")
                    message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    alertDialog.setTitle(title)
                    alertDialog.setMessage(message)
                    alertDialog.setIcon(R.drawable.success)

                    alertDialog.setPositiveButton("Yes") { _, _ ->
                        addIncome(getCInc() + enteredAmount, enteredAmount)
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

        binding?.btnDisburseBudget?.setOnClickListener {
            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Confirm")
            title.setSpan(ForegroundColorSpan(Color.GREEN), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("Disburse funds for the current budget?")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.warn)

            alertDialog.setPositiveButton("Disburse") { _, _ ->
                disburseBudget()
            }

            alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
            }

            val dialog = alertDialog.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

            dialog.show()

        }
    }

    private fun getWeeklyTotals() {
        lifecycleScope.launch {
            val calendar = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
            }
            val startOfWeek = calendar.apply {
                set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            }.time
            val endOfWeek = calendar.apply {
                add(Calendar.DAY_OF_WEEK, 6)
            }.time
            val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
            val week = calendar.get(Calendar.WEEK_OF_YEAR)
            val path = "Week $week (${dateFormat.format(startOfWeek).replace("-", " ")} to ${
                dateFormat.format(endOfWeek).replace("-", " ")
            })"
            try {
                val totals = fetchWeeklyTotals(path) // your function we wrote earlier

                // Back on main thread, safe to update UI
                binding?.totalNetIncome?.text = formatIncome(totals["totalNetIncome"]?.toDouble() ?: 0.0)
                binding?.totalGrossIncome?.text = formatIncome(totals["totalGrossIncome"]?.toDouble() ?: 0.0)
                //binding?.totalNetDeviation?.text = totals["totalNetDeviation"].toString()
                //binding?.totalGrossDeviation?.text = totals["totalGrossDeviation"].toString()
                binding?.totalDifference?.text = formatIncome(totals["totalNetGrossDifference"]?.toDouble() ?: 0.0)

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load totals: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
            }
        }

    }

    fun authenticateUser(onSuccess: () -> Unit, onFailure: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(requireContext())

        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                // Only block if the user actively cancels or fails auth
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_LOCKOUT ||
                    errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT
                ) {
                    onFailure()
                } else {
                    // Allow fallback clock out on other errors
                    onSuccess()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
            }
        })

        val promptInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Authenticate")
                .setSubtitle("BILLK MOTOLINK LTD wants to confirm your identity before you proceed")
                .setAllowedAuthenticators(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
        } else {
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Authenticate")
                .setSubtitle("BILLK MOTOLINK LTD wants to confirm your identity before you proceed")
                .setDescription("Use fingerprint or your device screen lock")
                .setDeviceCredentialAllowed(true)
                .build()
        }
        biometricPrompt.authenticate(promptInfo)
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
                        getCompanyIncome()
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




    /*budget disbursal starts here*/
    private fun disburseBudget() {
        if (!isAdded || _binding == null) return

        // Update UI immediately on main thread
        binding?.btnDisburseBudget?.visibility = View.GONE
        binding?.progressBarDisburseBudget?.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val db = FirebaseFirestore.getInstance()
                    val docRef = db.collection("general").document("general_variables")
                    val document = docRef.get().await()

                    if (!document.exists()) {
                        return@withContext Result.Failure("Budget data not found")
                    }

                    val budget = document.get("budget") as? Map<String, Any> ?:
                    return@withContext Result.Failure("Invalid budget format")

                    val currentBudgetCost = (budget["budgetCost"] as? Number)?.toDouble() ?: 0.0
                    val currentCompanyIncome = (document.get("companyIncome") as? Number)?.toDouble() ?: 0.0
                    val updatedCompanyIncome = currentCompanyIncome - currentBudgetCost

                    val updatedBudget = budget.toMutableMap().apply {
                        put("budgetStatus", "Disbursed")
                    }

                    docRef.update(
                        mapOf(
                            "companyIncome" to updatedCompanyIncome,
                            "budget" to updatedBudget
                        )
                    ).await()

                    Result.Success(currentBudgetCost, currentCompanyIncome, updatedCompanyIncome)
                }

                when (result) {
                    is Result.Success -> handleSuccess(
                        result.currentBudgetCost,
                        result.currentCompanyIncome,
                        result.updatedCompanyIncome
                    )
                    is Result.Failure -> showError(result.message)
                }
            } catch (e: Exception) {
                showError("Failed to process budget: ${e.message}")
            } finally {
                if (isAdded && _binding != null) {
                    withContext(Dispatchers.Main) {
                        binding?.progressBarDisburseBudget?.visibility = View.GONE
                    }
                }
            }
        }
    }

    private suspend fun handleSuccess(currentBudgetCost: Double, currentCompanyIncome: Double, updatedCompanyIncome: Double) {
        if (!isAdded || _binding == null) return

        withContext(Dispatchers.Main) {
            // Update UI
            binding?.incomeValue?.text = "Ksh.${String.format(" %,.2f", updatedCompanyIncome)}"
            binding?.btnDisburseBudget?.visibility = View.GONE

            // Show success toast
            Toast.makeText(requireContext(), "Budget disbursed successfully", Toast.LENGTH_SHORT).show()

            // Post cash flow
            val cashFlowMessage = "Disbursed a ${formatIncome(currentBudgetCost)} budget. " +
                    "This action decremented company income from ${formatIncome(currentCompanyIncome)} " +
                    "to ${formatIncome(updatedCompanyIncome)}."
            Utility.postCashFlow(cashFlowMessage, false)

            // Notify admins
            val roles = listOf("Admin", "CEO", "Systems, IT", "HR")
            Utility.notifyAdmins("HR budget disbursed.", "Human Resource", roles)

            // Show confirmation dialog
            showConfirmationDialog(currentBudgetCost)
        }
    }

    private fun showConfirmationDialog(amount: Double) {
        val alertDialog = AlertDialog.Builder(requireContext()).apply {
            // Custom title with green color
            val title = SpannableString("Human Resource").apply {
                setSpan(ForegroundColorSpan(Color.GREEN), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            // Custom message with gray color
            val message = SpannableString("Send Ksh.${String.format(" %,.2f", amount)} to your acting HR personnel.").apply {
                setSpan(ForegroundColorSpan(Color.GRAY), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            setTitle(title)
            setMessage(message)
            setIcon(R.drawable.warn)
            setPositiveButton("Ok") { dialog, _ -> dialog.dismiss() }
        }

        alertDialog.create().apply {
            window?.setBackgroundDrawableResource(R.drawable.rounded_black)
            show()
        }
    }

    private fun showError(message: String) {
        if (!isAdded || _binding == null) return

        lifecycleScope.launchWhenResumed {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            binding?.btnDisburseBudget?.visibility = View.VISIBLE
        }
    }

    sealed class Result {
        data class Success(
            val currentBudgetCost: Double,
            val currentCompanyIncome: Double,
            val updatedCompanyIncome: Double
        ) : Result()

        data class Failure(val message: String) : Result()
    }

    /*budget disbursal ends here*/


    /*fetching totals for this week begin here*/
    suspend fun fetchWeeklyTotals(path: String): Map<String, Int> = withContext(Dispatchers.IO) {
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("deviations").document(path)

        try {
            val snapshot = docRef.get().await()  // suspend-friendly, no ANR

            if (!snapshot.exists()) {
                // Path doesn't exist → return zeros
                return@withContext mapOf(
                    "totalNetIncome" to 0,
                    "totalGrossIncome" to 0,
                    "totalNetDeviation" to 0,
                    "totalGrossDeviation" to 0,
                    "totalNetGrossDifference" to 0
                )
            }

            var totalNetIncome = 0
            var totalGrossIncome = 0
            var totalNetDeviation = 0
            var totalGrossDeviation = 0
            var totalNetGrossDifference = 0

            // Firestore doc structure: { userName -> { DayOfWeek -> deviationData } }
            snapshot.data?.forEach { (_, userEntry) ->
                if (userEntry is Map<*, *>) {
                    userEntry.values.forEach { dayEntry ->
                        if (dayEntry is Map<*, *>) {
                            totalNetIncome += (dayEntry["netIncome"] as? Number)?.toInt() ?: 0
                            totalGrossIncome += (dayEntry["grossIncome"] as? Number)?.toInt() ?: 0
                            totalNetDeviation += (dayEntry["netDeviation"] as? Number)?.toInt() ?: 0
                            totalGrossDeviation += (dayEntry["grossDeviation"] as? Number)?.toInt() ?: 0
                            totalNetGrossDifference += (dayEntry["netGrossDifference"] as? Number)?.toInt() ?: 0
                        }
                    }
                }
            }

            return@withContext mapOf(
                "totalNetIncome" to totalNetIncome,
                "totalGrossIncome" to totalGrossIncome,
                "totalNetDeviation" to totalNetDeviation,
                "totalGrossDeviation" to totalGrossDeviation,
                "totalNetGrossDifference" to totalNetGrossDifference
            )
        } catch (e: Exception) {
            // On exception also return zeros
            return@withContext mapOf(
                "totalNetIncome" to 0,
                "totalGrossIncome" to 0,
                "totalNetDeviation" to 0,
                "totalGrossDeviation" to 0,
                "totalNetGrossDifference" to 0
            )
        }
    }

    /*fetching totals fot this week end here*/





    private fun checkIfHrBudgetExists() {
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("general").document("general_variables")

        docRef.get().addOnSuccessListener { document ->
            if (document.exists()) {

                val budget = document.get("budget")
                val budgetItems = document.get("budget.items")
                if (!isAdded || _binding == null) return@addOnSuccessListener
                //binding.hrProgressBar.visibility = View.GONE

                if (budgetItems != null) {
                    Log.d("Firestore", "Budget exists: $budget")

                    val budget = document.get("budget") as? Map<*, *>  // Retrieve `budget` as a Map
                    val budgetStatus = budget?.get("budgetStatus") as? String
                    val budgetCost = budget?.get("budgetCost") as? Double
                    val items = budget?.get("items") as? List<Map<String, Any>>
                    val itemCount = items?.size
                    if (budgetStatus == "Approved") {
                        if (!isAdded || _binding == null) return@addOnSuccessListener
                        binding?.btnDisburseBudget?.visibility = View.VISIBLE
                        binding?.btnDisburseBudget?.text = "Disburse the Ksh.${String.format("%,.2f", budgetCost)} Budget"
                    }
                }
                else {

                }
            } else {
                Log.d("Firestore", "Document does not exist.")
                // Handle case when document does not exist
            }
        }.addOnFailureListener { exception ->
            Log.e("Firestore", "Error checking budget", exception)
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
            binding?.changePwdProgressBar?.visibility = View.VISIBLE
            binding?.btnChangePassword?.visibility = View.GONE

            val currentUser = FirebaseAuth.getInstance().currentUser
            currentUser?.let { user ->
                val credential = EmailAuthProvider.getCredential(user.email!!, enteredOldPw)
                user.reauthenticate(credential)
                    .addOnSuccessListener {
                        user.updatePassword(enteredNewPw)
                            .addOnSuccessListener {
                                binding?.oldPwInput?.setText("")
                                binding?.newPwInput?.setText("")
                                binding?.changePwdProgressBar?.visibility = View.GONE
                                binding?.btnChangePassword?.visibility = View.VISIBLE
                                Toast.makeText(requireContext(), "Password updated successfully", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { exception ->
                                binding?.oldPwInput?.setText("")
                                binding?.newPwInput?.setText("")
                                binding?.changePwdProgressBar?.visibility = View.GONE
                                binding?.btnChangePassword?.visibility = View.VISIBLE
                                Toast.makeText(requireContext(), "Failed to update password: ${exception.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { exception ->
                        binding?.oldPwInput?.setText("")
                        binding?.newPwInput?.setText("")
                        binding?.changePwdProgressBar?.visibility = View.GONE
                        binding?.btnChangePassword?.visibility = View.VISIBLE
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
            binding?.changeUserNameProgressBar?.visibility = View.VISIBLE
            binding?.btnChangeUsername?.visibility = View.GONE

            val currentUser = FirebaseAuth.getInstance().currentUser
            val db = FirebaseFirestore.getInstance()

            currentUser?.let { user ->
                val usersRef = db.collection("users")
                val generalRef = db.collection("general").document("general_variables")

                // First get current userName from users collection
                usersRef.whereEqualTo("email", user.email).get()
                    .addOnSuccessListener { querySnapshot ->
                        val userDoc = querySnapshot.documents.firstOrNull()
                        val currentUserName = userDoc?.getString("userName")

                        if (currentUserName.isNullOrEmpty()) {
                            binding?.changeUserNameProgressBar?.visibility = View.GONE
                            binding?.btnChangeUsername?.visibility = View.VISIBLE
                            Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        // Now check if any bike has this user as assignedRider
                        generalRef.get().addOnSuccessListener { generalDoc ->
                            val bikesMap = generalDoc.get("batteries") as? Map<String, Map<String, Any>> ?: emptyMap()

                            val hasAssignedBike = bikesMap.any { (_, bikeData) ->
                                bikeData["assignedRider"] == currentUserName
                            }

                            if (hasAssignedBike) {
                                binding?.changeUserNameProgressBar?.visibility = View.GONE
                                binding?.btnChangeUsername?.visibility = View.VISIBLE
                                Toast.makeText(requireContext(), "You are still assigned to a bike. Offload before changing username.", Toast.LENGTH_LONG).show()
                                binding?.inputNewUsername?.setText("")
                                return@addOnSuccessListener
                            }

                            // Safe to update the username now
                            val userRef = usersRef.document(user.uid)
                            userRef.update("userName", enteredUsername)
                                .addOnSuccessListener {
                                    Toast.makeText(requireContext(), "Username updated successfully", Toast.LENGTH_SHORT).show()
                                    binding?.usernameTextView?.text = enteredUsername
                                    binding?.inputNewUsername?.setText("")
                                    binding?.changeUserNameProgressBar?.visibility = View.GONE
                                    binding?.btnChangeUsername?.visibility = View.VISIBLE
                                }
                                .addOnFailureListener { exception ->
                                    binding?.changeUserNameProgressBar?.visibility = View.GONE
                                    binding?.btnChangeUsername?.visibility = View.VISIBLE
                                    Toast.makeText(requireContext(), "Failed to update username: ${exception.message}", Toast.LENGTH_SHORT).show()
                                }

                        }.addOnFailureListener { e ->
                            binding?.changeUserNameProgressBar?.visibility = View.GONE
                            binding?.btnChangeUsername?.visibility = View.VISIBLE
                            Toast.makeText(requireContext(), "Failed to check bikes: ${e.message}", Toast.LENGTH_SHORT).show()
                        }

                    }
                    .addOnFailureListener { e ->
                        binding?.changeUserNameProgressBar?.visibility = View.GONE
                        binding?.btnChangeUsername?.visibility = View.VISIBLE
                        Toast.makeText(requireContext(), "Failed to fetch user info: ${e.message}", Toast.LENGTH_SHORT).show()
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
                            phone = "+254$phone"
                        }
                        else if (phone.length == 10) {
                            phone = "+254${phone.substring(1)}"
                        }
                        else if (phone.length == 12) {
                            phone = "+$phone"
                        }

                        if (rank == "CEO") {
                            rank = "Chief Executive Officer"
                            binding?.ceoDiv?.visibility = View.VISIBLE
                        }
                        else if (rank == "Admin") {
                            rank = "Administrator/Manager"
                            binding?.ceoDiv?.visibility = View.VISIBLE
                            // binding?.btnDisburseBudget?.visibility = View.GONE
                        }
                        else if (rank == "HR") {
                            rank = "Human Resource"
                        }
                        else if (rank == "Systems, IT") {
                            binding?.ceoDiv?.visibility = View.VISIBLE
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

                        val shimmerLayout = binding?.shimmerLayout
                        shimmerLayout?.stopShimmer()
                        shimmerLayout?.visibility = View.GONE


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

    private fun withdrawIncome(amount: Double, enteredAmount: Double) {
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
                        getCompanyIncome()
                        val message = "Withdrew ${formatIncome(enteredAmount)} from company income\n.Company Balance: ${formatIncome(amount)}."
                        Utility.postCashFlow(message, false)
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

    private fun addIncome(amount: Double, enteredAmount: Double) {
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

                        val message = "Added ${formatIncome(enteredAmount)} to company account\n.Company Balance: ${formatIncome(amount)}."
                        Utility.postCashFlow(message, true)

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
                    val companyConstant = document.getDouble("commissionPercentage")?: 0.0
                    binding?.cConstant?.text = "${companyConstant}%"
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
