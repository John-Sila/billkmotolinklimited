package com.example.billkmotolinkltd.ui.corrections

import android.graphics.Color
import android.os.Build
import androidx.fragment.app.Fragment
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentCorrectionsBinding
import com.example.billkmotolinkltd.ui.Utility
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.collections.get
import kotlinx.coroutines.suspendCancellableCoroutine
import com.google.firebase.firestore.DocumentSnapshot
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CorrectionsFragment: Fragment() {

    private var _binding: FragmentCorrectionsBinding? = null
    private val binding get() = _binding!!
    private var netIncome: Double = 0.0
    private var regularTargetAmount: Double = 0.0
    private var sundayTargetAmount: Double = 0.0
    private var companyCommission: Double = 0.0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCorrectionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            fetchDates()
            loadUser()
            initializeFragment()
        }

        fetchCommission()

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                fetchDates()
                loadUser()
                initializeFragment()
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }

        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.color4,
            R.color.color3,
            R.color.semiTransparent
        )

        binding.scrollView.viewTreeObserver.addOnScrollChangedListener {
            val canScrollUp = binding.scrollView.canScrollVertically(-1)
            binding.swipeRefreshLayout.isEnabled = !canScrollUp
        }


        binding.btnClockOut.setOnClickListener {
            val grossInc = binding.inputGrossIncome.text.toString()
            if (grossInc.isEmpty() || grossInc.toDouble() <= 0) {
                Toast.makeText(requireContext(), "You are grossing an invalid value!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Reclock Out")
            title.setSpan(ForegroundColorSpan(Color.RED), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("Please ensure that your details are correct.")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.warn)

            alertDialog.setPositiveButton("Proceed") { _, _ ->

                clockOut()

            }

            alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
            }

            val dialog = alertDialog.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

            dialog.show()
        }

        binding.dateToCorrect.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                binding.dateToCorrect.isEnabled = false
                binding.btnClockOut.isEnabled = false
                val selectedItem = binding.dateToCorrect.selectedItem
                if (selectedItem == null || selectedItem.toString().isEmpty()) {
                    Toast.makeText(requireContext(), "No date to correct", Toast.LENGTH_SHORT).show()
                    return
                }
                val selectedDate = selectedItem.toString()
                val currentUser = FirebaseAuth.getInstance().currentUser ?: return
                val uid = currentUser.uid
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            val requirementsMap = document.get("requirements") as? Map<*, *>
                            requirementsMap?.forEach { (_, value) ->
                                if (value is Map<*, *>) {
                                    val date = value["date"] as? String
                                    if (date == selectedDate) {
                                        val inAppBal = value["appBalance"] as? Double ?: 0.0
                                        binding.previousInAppBalance.setText(inAppBal.toString())
                                        binding.dateToCorrect.isEnabled = true
                                        binding.btnClockOut.isEnabled = true
                                    }
                                }
                            }
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Error locating you in the database. Try logging out and in again.", Toast.LENGTH_LONG).show()
                        binding.dateToCorrect.isEnabled = true
                        binding.btnClockOut.isEnabled = true
                    }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Handle no selection if needed
            }
        }
    }

    private suspend fun fetchDatesAsync(): DocumentSnapshot =
        suspendCancellableCoroutine { cont ->
            val currentUser = FirebaseAuth.getInstance().currentUser ?: return@suspendCancellableCoroutine

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    cont.resume(document)
                }
                .addOnFailureListener { exception ->
                    cont.resumeWithException(exception)
                }
        }

    private fun postDeviationData(
        userName: String,
        path: String, // week range
        deviationData: Map<String, Any>,
        dayOfWeek: String,
        selectedDate: String
    ) {
        val db = FirebaseFirestore.getInstance()
        val devRef = db.collection("deviations").document(path)

        // Construct nested data structure: JohnDoe -> Monday -> {...}
        val nestedDayMap = mapOf(
            dayOfWeek to deviationData
        )

        val finalMap = mapOf(
            userName to nestedDayMap
        )

        devRef.set(finalMap, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Weekly report posted successfully", Toast.LENGTH_SHORT).show()
                deleteRequirement(selectedDate)
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Weekly report failed again. Retry", Toast.LENGTH_SHORT).show()
            }
    }

    private fun calculateNetIncome(textNetIncome: TextView) {
        val grossIncome = binding.inputGrossIncome.text.toString().toDoubleOrNull() ?: 0.0
        val commission = binding.inputCommission.text.toString().toDoubleOrNull() ?: 0.0
        val todaysInAppBalance = binding.todaysInAppBalance.text.toString().toDoubleOrNull() ?: 0.0
        val previousInAppBalance = binding.previousInAppBalance.text.toString().toDoubleOrNull() ?: 0.0

        var totalExpenses = 0.0
        if (binding.checkBSExpense.isChecked) {
            totalExpenses += binding.inputBSExpense.text.toString().toDoubleOrNull() ?: 0.0
        }
        if (binding.checkDBExpense.isChecked) {
            totalExpenses += binding.inputDBExpense.text.toString().toDoubleOrNull() ?: 0.0
        }
        if (binding.checkLunchExpense.isChecked) {
            totalExpenses += binding.inputLunchExpense.text.toString().toDoubleOrNull() ?: 0.0
        }
        if (binding.checkPoliceExpense.isChecked) {
            totalExpenses += binding.inputPoliceExpense.text.toString().toDoubleOrNull() ?: 0.0
        }
        if (binding.checkOtherExpense.isChecked) {
            totalExpenses += binding.inputOtherExpense.text.toString().toDoubleOrNull() ?: 0.0
        }

        netIncome = grossIncome * (1 - commission) - (todaysInAppBalance - previousInAppBalance) - totalExpenses
        textNetIncome.text = "Net Income: Ksh %.2f".format(netIncome)

    }

    private suspend fun initializeFragment() {
        withContext(Dispatchers.Main) {
            val inputGrossIncome = binding.inputGrossIncome
            val inputCommission = binding.inputCommission
            val inputTodaysInAppBalance = binding.todaysInAppBalance
            val inputPreviousInAppBalance = binding.previousInAppBalance

            val checkBSExpense = binding.checkBSExpense
            val checkLunchExpense = binding.checkLunchExpense
            val checkPoliceExpense = binding.checkPoliceExpense
            val checkDBExpense = binding.checkDBExpense
            val checkOtherExpense = binding.checkOtherExpense

            val inputBSExpense = binding.inputBSExpense
            val inputLunchExpense = binding.inputLunchExpense
            val inputPoliceExpense = binding.inputPoliceExpense
            val inputDBExpense = binding.inputDBExpense
            val inputOtherExpense = binding.inputOtherExpense

            val textNetIncome: TextView = binding.textNetIncome

            listOf(inputBSExpense, inputLunchExpense, inputPoliceExpense, inputOtherExpense, inputDBExpense).forEach {
                it.visibility = View.GONE
            }

            checkBSExpense.setOnCheckedChangeListener { _, isChecked ->
                inputBSExpense.visibility = if (isChecked) View.VISIBLE else View.GONE
                calculateNetIncome(textNetIncome)
            }
            checkDBExpense.setOnCheckedChangeListener { _, isChecked ->
                inputDBExpense.visibility = if (isChecked) View.VISIBLE else View.GONE
                calculateNetIncome(textNetIncome)
            }
            checkLunchExpense.setOnCheckedChangeListener { _, isChecked ->
                inputLunchExpense.visibility = if (isChecked) View.VISIBLE else View.GONE
                calculateNetIncome(textNetIncome)
            }
            checkPoliceExpense.setOnCheckedChangeListener { _, isChecked ->
                inputPoliceExpense.visibility = if (isChecked) View.VISIBLE else View.GONE
                calculateNetIncome(textNetIncome)
            }
            checkOtherExpense.setOnCheckedChangeListener { _, isChecked ->
                inputOtherExpense.visibility = if (isChecked) View.VISIBLE else View.GONE
                binding.inputOtherExpenseDescription.visibility = if (isChecked) View.VISIBLE else View.GONE
                calculateNetIncome(textNetIncome)
            }

            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    calculateNetIncome(textNetIncome)
                }
                override fun afterTextChanged(s: Editable?) {}
            }

            listOf(
                inputGrossIncome, inputCommission,
                inputTodaysInAppBalance, inputPreviousInAppBalance,
                inputBSExpense, inputLunchExpense, inputPoliceExpense, inputOtherExpense, inputDBExpense
            ).forEach { it.addTextChangedListener(textWatcher) }

            calculateNetIncome(textNetIncome)
        }
    }

    private fun authenticateUser(onSuccess: () -> Unit, onFailure: () -> Unit) {
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
                .setTitle("BILLK MOTOLINK LTD")
                .setSubtitle("Confirm identity to proceed.")
                .setAllowedAuthenticators(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
        } else {
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("BILLK MOTOLINK LTD")
                .setSubtitle("Confirm identity to proceed.")
                .setDescription("Use fingerprint or your device screen lock")
                .setDeviceCredentialAllowed(true)
                .build()
        }



        biometricPrompt.authenticate(promptInfo)
    }

    private suspend fun fetchDates() {
        try {
            val document = fetchDatesAsync()

            if (document.exists()) {
                val requirementsMap = document.get("requirements") as? Map<*, *>
                val dateList = mutableListOf<String>()

                requirementsMap?.forEach { (_, value) ->
                    if (value is Map<*, *>) {
                        val date = value["date"] as? String
                        if (!date.isNullOrEmpty()) {
                            dateList.add(date)
                        }
                    }
                }

                if (dateList.isNotEmpty()) {
                    populateSpinner(dateList.sortedDescending())
                } else {
                    Toast.makeText(requireContext(), "No requirement dates found.", Toast.LENGTH_SHORT).show()
                    if (_binding == null || !isAdded) return
                    binding.btnClockOut.visibility = View.GONE
                }
            } else {
                Toast.makeText(requireContext(), "User document not found.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to fetch data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun populateSpinner(dateList: List<String>) {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            dateList
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.dateToCorrect.adapter = adapter
    }

    private fun fetchCommission() {
        val db = FirebaseFirestore.getInstance()
        val destinyRef = db.collection("general").document("general_variables")
        destinyRef.get()
            .addOnSuccessListener { document ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                val commission = document.getDouble("commissionPercentage") ?: 0.0
                companyCommission = commission
                binding.inputCommission.setText((commission / 100).toString())
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to load destinations: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private suspend fun loadUser() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || !isAdded || _binding == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "User not logged in or Fragment not attached", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val firestore = FirebaseFirestore.getInstance()
        val companyDoc = suspendCancellableCoroutine<DocumentSnapshot> { cont ->
            firestore.collection("general").document("general_variables")
                .get()
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

        if (!isAdded || _binding == null) return

        val companyStatus = companyDoc.getString("companyState") ?: "Unknown"
        if (companyStatus == "Paused") {
            withContext(Dispatchers.Main) {
                binding.btnCompanyPaused.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "Company is currently on hold.", Toast.LENGTH_LONG).show()
            }
            return
        }

        val userDoc = suspendCancellableCoroutine<DocumentSnapshot> { cont ->
            firestore.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

        if (!isAdded || _binding == null) return

        withContext(Dispatchers.Main) {
            val dailyTarget = userDoc.getDouble("dailyTarget") ?: 0.0
            val sundayTarget = userDoc.getDouble("sundayTarget") ?: 0.0
            val isClockedIn = userDoc.getBoolean("isClockedIn") != false

            if (isClockedIn) {
                binding.btnClockOut.visibility = View.GONE
                binding.btnClockOutFirst.visibility = View.VISIBLE
            } else {
                regularTargetAmount = dailyTarget
                sundayTargetAmount = sundayTarget
                binding.btnClockOut.visibility = View.VISIBLE
            }

            val isActive = userDoc.get("isActive") as? Boolean ?: true
            val isDeleted = userDoc.get("isDeleted") as? Boolean ?: false

            if (!isActive || isDeleted) {
                FirebaseAuth.getInstance().signOut()
                Toast.makeText(requireContext(), "You were logged out because this account has been flagged.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun getTarget(nature: String): Double {
        return if (nature == "Sunday") sundayTargetAmount
        else regularTargetAmount
    }

    fun getCommission(): Double {
        return companyCommission
    }

    private fun clockOut() {
        if (_binding == null || !isAdded) return
        val selectedItem = binding.dateToCorrect.selectedItem
        if (selectedItem == null || selectedItem.toString().isEmpty()) {
            Toast.makeText(requireContext(), "No date to correct", Toast.LENGTH_SHORT).show()
            return
        }
        val selectedDate = selectedItem.toString()

        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val uid = currentUser.uid

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val requirementsMap = document.get("requirements") as? Map<*, *>
                    requirementsMap?.forEach { (_, value) ->
                        if (value is Map<*, *>) {
                            val date = value["date"] as? String
                            if (date == selectedDate) {
                                val weekRange = value["weekRange"] as? String ?: "N/A"
                                val dayOfWeek = value["dayOfWeek"] as? String ?: "N/A"

                                if (weekRange == "N/A" || dayOfWeek == "N/A") {
                                    if (!isAdded || _binding == null) return@addOnSuccessListener
                                    val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

                                    // Custom title with red color
                                    val title = SpannableString("Corrections")
                                    title.setSpan(ForegroundColorSpan(Color.GREEN), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                                    // Custom message with black color
                                    val message = SpannableString("Failed. Path is incomplete.")
                                    message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                                    alertDialog.setTitle(title)
                                    alertDialog.setMessage(message)
                                    alertDialog.setIcon(R.drawable.warn)

                                    alertDialog.setPositiveButton("Ok") { dialog, _ ->
                                        dialog.dismiss()
                                    }

                                    val dialog = alertDialog.create()
                                    dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

                                    dialog.show()

                                    return@addOnSuccessListener
                                }
                                else {
                                    if (!isAdded || _binding == null) return@addOnSuccessListener
                                    val user = FirebaseAuth.getInstance().currentUser
                                    if (user == null) {
                                        Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
                                        return@addOnSuccessListener
                                    }

                                    val userRef = FirebaseFirestore.getInstance().collection("users").document(uid)

                                    // Get input values
                                    val grossIncome = binding.inputGrossIncome.text.toString().toDoubleOrNull() ?: 0.0
                                    val todaysBalance = binding.todaysInAppBalance.text.toString().toDoubleOrNull() ?: 0.0
                                    val previousBalance = binding.previousInAppBalance.text.toString().toDoubleOrNull() ?: 0.0
                                    val inAppDifference = todaysBalance - previousBalance

                                    // Expenses Mapping
                                    val expenses = mutableMapOf<String, Double>()
                                    var isValid = true // Flag to track validation

                                    fun validateExpense(checkBox: CheckBox, input: EditText, key: String) {
                                        if (checkBox.isChecked) {
                                            val amount = input.text.toString().toDoubleOrNull()
                                            if (amount == null || amount <= 0.0) {
                                                Toast.makeText(requireContext(), "Uncheck empty expenses: $key", Toast.LENGTH_SHORT).show()
                                                isValid = false
                                                return
                                            }
                                            if (key == "Other") {
                                                expenses[binding.inputOtherExpenseDescription.text.toString().trim()] = amount
                                            } else {
                                                expenses[key] = amount
                                            }
                                        }
                                    }

                                    validateExpense(binding.checkBSExpense, binding.inputBSExpense, "Battery Swap")
                                    validateExpense(binding.checkLunchExpense, binding.inputLunchExpense, "Lunch")
                                    validateExpense(binding.checkDBExpense, binding.inputDBExpense, "Data Bundles")
                                    validateExpense(binding.checkPoliceExpense, binding.inputPoliceExpense, "Police")
                                    validateExpense(binding.checkOtherExpense, binding.inputOtherExpense, "Other")

                                    // Validate "Other" expense description
                                    if (binding.checkOtherExpense.isChecked) {
                                        val description = binding.inputOtherExpenseDescription.text.toString().trim()
                                        if (description.length <= 3) {
                                            Toast.makeText(requireContext(), "Your custom expense needs a better description", Toast.LENGTH_SHORT).show()
                                            isValid = false
                                            return@addOnSuccessListener
                                        }
                                    }

                                    // **STOP EXECUTION if there's a validation error**
                                    if (!isValid) return@addOnSuccessListener
                                    else {
                                        authenticateUser(
                                            onSuccess = {
                                                // Secure operation here
                                                Toast.makeText(context, "Authenticated!", Toast.LENGTH_SHORT).show()
                                                binding.clockingOutProgressBar.visibility = View.VISIBLE
                                                binding.btnClockOut.visibility = View.GONE

                                                val clockoutData = mapOf(
                                                    "grossIncome" to grossIncome,
                                                    "todaysInAppBalance" to todaysBalance,
                                                    "previousInAppBalance" to previousBalance,
                                                    "inAppDifference" to inAppDifference,
                                                    "expenses" to expenses,
                                                    "netIncome" to getNetIncome(),
                                                    "posted_at" to Timestamp.now(),
                                                    "timeElapsed" to "Nullified"
                                                )

                                                if (true) {
                                                    userRef.get()
                                                        .addOnSuccessListener { document ->
                                                            if (document.exists()) {
                                                                val dTarget = document.getLong("dailyTarget")?.toInt() ?: 0
                                                                val sTarget = document.getLong("sundayTarget")?.toInt() ?: 0
                                                                val userName = document.getString("userName") ?: ""
                                                                val pendingAmount = document.getDouble("pendingAmount") ?: 0.0

                                                                // Prepare the data to update
                                                                val combinedUpdates = if (dayOfWeek == "Sunday") {
                                                                    // it's sunday
                                                                    mapOf(
                                                                        "clockouts.$selectedDate" to clockoutData,
                                                                        // "currentInAppBalance" to todaysBalance,
                                                                        // "isWorkingOnSunday" to false,
                                                                        "isClockedIn" to false,
                                                                        // "netClockedLastly" to getNetIncome(),
                                                                        "pendingAmount" to (pendingAmount + getNetIncome()),
                                                                        // "lastClockDate" to Timestamp.now(),
                                                                    )
                                                                } else {
                                                                    // it's not sunday (yet)
                                                                    mapOf(
                                                                        "clockouts.$selectedDate" to clockoutData,
                                                                        // "currentInAppBalance" to todaysBalance,
                                                                        "isClockedIn" to false,
                                                                        // "netClockedLastly" to getNetIncome(),
                                                                        "pendingAmount" to (pendingAmount + getNetIncome()),
                                                                        // "lastClockDate" to Timestamp.now(),
                                                                    )
                                                                }

                                                                // Perform the update
                                                                userRef.update(combinedUpdates)
                                                                    .addOnSuccessListener {
                                                                        Toast.makeText(requireContext(), "Clockout saved successfully!", Toast.LENGTH_SHORT).show()

                                                                        val netDeviation = if (dayOfWeek == "Sunday") {
                                                                            getNetIncome() - sTarget
                                                                        }
                                                                        else getNetIncome() - dTarget

                                                                        val grossDeviation = if (dayOfWeek == "Sunday") {
                                                                            grossIncome - sTarget
                                                                        } else grossIncome - dTarget

                                                                        val deviationData = mapOf(
                                                                            "netIncome" to getNetIncome(),
                                                                            "grossIncome" to grossIncome,
                                                                            "netDeviation" to netDeviation,
                                                                            "grossDeviation" to grossDeviation,
                                                                            "netGrossDifference" to grossIncome - getNetIncome(),
                                                                        )

                                                                        postDeviationData(userName, weekRange, deviationData, dayOfWeek, selectedDate)
                                                                        // updateNetIncome(uid, getNetIncome())
                                                                        lifecycleScope.launch {
                                                                            Utility.postTrace("Clocked out of shift.")
                                                                            val roles = listOf("Admin", "CEO", "Systems, IT")
                                                                            Utility.notifyAdmins("$userName has corrected a fault for $selectedDate.", "Clockouts", roles)
                                                                        }
                                                                        binding.btnClockOut.visibility = View.VISIBLE
                                                                        binding.clockingOutProgressBar.visibility = View.GONE

                                                                        binding.inputGrossIncome.setText("")
                                                                        binding.inputOtherExpenseDescription.setText("")
                                                                        binding.todaysInAppBalance.setText("")
                                                                        binding.previousInAppBalance.setText(todaysBalance.toString())
                                                                    }
                                                                    .addOnFailureListener { e ->
                                                                        binding.clockingOutProgressBar.visibility = View.GONE
                                                                        binding.btnClockOut.visibility = View.VISIBLE
                                                                        Toast.makeText(requireContext(), "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                                                                    }
                                                            }
                                                        }
                                                }
                                            },
                                            onFailure = {
                                                Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // If no match was found
                    Toast.makeText(requireContext(), "Date not found in requirements.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "User document not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to fetch user data.", Toast.LENGTH_SHORT).show()
            }


    }

    fun getNetIncome(): Double {
        val nc: Double = netIncome// your netIncome calculation or retrieval logic here
        return BigDecimal(nc).setScale(0, RoundingMode.HALF_UP).toDouble()
    }

    private fun deleteRequirement(date: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val uid = currentUser.uid
        val db = FirebaseFirestore.getInstance()

        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val requirements = document.get("requirements") as? Map<*, *>
                    val entryToDelete = requirements?.entries?.find { entry ->
                        val value = entry.value
                        value is Map<*, *> && value["date"] == date
                    }

                    if (entryToDelete != null) {
                        val entryKey = entryToDelete.key.toString()
                        val updates = mapOf(
                            "requirements.$entryKey" to FieldValue.delete()
                        )

                        db.collection("users")
                            .document(uid)
                            .update(updates)
                            .addOnSuccessListener {
                                Toast.makeText(requireContext(), "Requirement deleted.", Toast.LENGTH_SHORT).show()
                                if (_binding == null || !isAdded) return@addOnSuccessListener
                                binding.dateToCorrect.adapter = null
                                viewLifecycleOwner.lifecycleScope.launch {
                                    fetchDates()
                                }
                            }
                            .addOnFailureListener {
                                Toast.makeText(requireContext(), "Failed to delete requirement.", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(requireContext(), "No matching requirement found.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "User document not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to fetch user data.", Toast.LENGTH_SHORT).show()
            }
    }


}