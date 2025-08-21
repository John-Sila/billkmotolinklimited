package com.example.billkmotolinkltd.ui.clockouts

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import androidx.biometric.BiometricPrompt
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentClockoutsBinding
import com.example.billkmotolinkltd.ui.Utility
import com.example.billkmotolinkltd.ui.formatIncome
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

class ClockoutsFragment : Fragment() {

    private var _binding: FragmentClockoutsBinding? = null
    private val binding get() = _binding!!
    private var netIncome: Double = 0.0
    private var todayClockinMileage: Double = 0.0
    private var userBike: String = "Unknown"
    private var timeElapsed: String = "Unknown"

    private var targetAmount: Double = 0.0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentClockoutsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Input fields
        val inputGrossIncome = binding.inputGrossIncome
        val inputCommission = binding.inputCommission
        val inputTodaysInAppBalance = binding.todaysInAppBalance
        val inputPreviousInAppBalance = binding.previousInAppBalance

        // Expense Checkboxes & Fields
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

        // Net Income Display
        val textNetIncome: TextView = binding.textNetIncome

        // Initially hide the expense inputs
        listOf(inputBSExpense, inputLunchExpense, inputPoliceExpense, inputOtherExpense, inputDBExpense).forEach {
            it.visibility = View.GONE
        }

        // Show/Hide EditText based on Checkbox state
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

        // TextWatcher for inputs to recalculate net income
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                calculateNetIncome(textNetIncome)
                calculateDeviation()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        // Attach TextWatcher to all inputs
        listOf(
            inputGrossIncome, inputCommission,
            inputTodaysInAppBalance, inputPreviousInAppBalance,
            inputBSExpense, inputLunchExpense, inputPoliceExpense, inputOtherExpense, inputDBExpense
        ).forEach { it.addTextChangedListener(textWatcher) }

        // Initial calculation
        calculateNetIncome(textNetIncome)
        getCommissionConstant()
        checkIfClocked()
        fetchLocations()
        deleteOldClockOuts()

        binding.btnClockOut.setOnClickListener{
            val grossInc = binding.inputGrossIncome.text.toString()
            if (grossInc.isEmpty() || grossInc.toDouble() <= 0) {
                Toast.makeText(requireContext(), "You are grossing an invalid value!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val inputMileage = binding.inputMileage.text.toString().trim()
            val isValidMileage = inputMileage.matches(Regex("^\\d+(\\.\\d+)?$"))
            val isValidTodaysBal = binding.todaysInAppBalance.text.toString().trim().matches(Regex("^\\d+(\\.\\d+)?$"))
            var mileage = 0.0
            if (isValidMileage) {
                mileage = inputMileage.toDouble()
                if (!isValidTodaysBal) {
                    Toast.makeText(requireContext(), "Invalid in-app balance.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            } else {
                Toast.makeText(requireContext(), "Invalid mileage.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (mileage < todayClockinMileage) {
                Toast.makeText(requireContext(), "Your clockout mileage is less than expected.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Clock Out")
            title.setSpan(ForegroundColorSpan(Color.RED), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("Are you sure you want to submit this report? It can only be done once in a day.\n" +
                    "This action also automatically drops your bike and battery(s) so ensure your location is set right.")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.warn)

            alertDialog.setPositiveButton("Clock Out") { _, _ ->
                clockOut(mileage)
            }

            alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
            }

            val dialog = alertDialog.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

            dialog.show()
        }
    }

    private fun calculateDeviation() {
        if (_binding == null || !isAdded) return

        val grossIncomeText = binding.inputGrossIncome.text.toString()
        val grossIncome = grossIncomeText.toDoubleOrNull() ?: 0.0
        val dev = grossIncome - getTarget()

        binding.actualDeviation.text = formatIncome(dev).toString()

        // Set text color based on whether deviation is negative or positive
        if (dev < 0) {
            binding.actualDeviation.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
        } else {
            binding.actualDeviation.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
        }
    }

    private fun deleteOldClockOuts() {
        if (!isAdded || _binding == null) return

        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(currentUser.uid)

        lifecycleScope.launch {
            try {
                // Execute Firestore operations on IO thread
                val document = withContext(Dispatchers.IO) {
                    userRef.get().await()
                }

                val clockouts = document.get("clockouts") as? Map<String, Any> ?: return@launch

                // Calculate date ranges on background thread
                val (allowedMonths, updatedClockouts) = withContext(Dispatchers.Default) {
                    val calendar = Calendar.getInstance()
                    val currentMonthIndex = calendar.get(Calendar.MONTH)
                    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
                        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

                    val allowedMonths = listOf(
                        monthNames[currentMonthIndex],
                        monthNames[(currentMonthIndex - 1 + 12) % 12],
                        monthNames[(currentMonthIndex - 2 + 12) % 12]
                    )

                    val filteredClockouts = clockouts.filterKeys { dateKey ->
                        allowedMonths.any { month -> dateKey.contains(month) }
                    }

                    Pair(allowedMonths, filteredClockouts)
                }

                // Update Firestore on IO thread
                withContext(Dispatchers.IO) {
                    userRef.update("clockouts", updatedClockouts).await()
                }

                // Show success message on main thread if fragment is still attached
                withContext(Dispatchers.Main) {
                    if (isAdded && _binding != null) {
                        Toast.makeText(
                            requireContext(),
                            "Clockout memory trimmed.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {
                // Show error message on main thread if fragment is still attached
                withContext(Dispatchers.Main) {
                    if (isAdded && _binding != null) {
                        val message = if (e is CancellationException) {
                            "Operation cancelled"
                        } else {
                            "Failed to delete old clockouts: ${e.message}. Retrying in the next launch."
                        }
                        Toast.makeText(
                            requireContext(),
                            message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
    // fetch destinations
    private fun fetchLocations() {
        if (!isAdded || _binding == null) return

        val db = FirebaseFirestore.getInstance()
        val destinyRef = db.collection("general").document("general_variables")

        // Execute in background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val document = destinyRef.get().await()

                // Process data in background
                val destinationsList = document.get("destinations") as? List<String> ?: emptyList()
                if (destinationsList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "No destinations found", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val sortedDestinations = destinationsList.sorted()

                // Switch to main thread only for UI updates
                withContext(Dispatchers.Main) {
                    if (!isAdded || _binding == null) return@withContext

                    val adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        sortedDestinations
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.clockOutLocation.adapter = adapter
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded && _binding != null) {
                        Toast.makeText(
                            requireContext(),
                            "Failed to load destinations: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }




    // Check of active status, today working status and company status starts here

    private fun checkIfClocked() {
        if (!isAdded || _binding == null) return

        val user = FirebaseAuth.getInstance().currentUser ?: run {
            showToast("User not logged in")
            return
        }

        // Use coroutine scope tied to fragment lifecycle
        lifecycleScope.launch {
            try {
                val companyStatus = withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance()
                        .collection("general")
                        .document("general_variables")
                        .get()
                        .await()
                        .getString("companyState") ?: "Unknown"
                }

                if (companyStatus == "Paused") {
                    withContext(Dispatchers.Main) {
                        binding.btnCompanyPaused.visibility = View.VISIBLE
                        showToast("Company is currently on hold", Toast.LENGTH_LONG)
                    }
                    return@launch
                }

                val userData = withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(user.uid)
                        .get()
                        .await()
                }

                if (!isAdded || _binding == null) return@launch

                processUserData(userData)
            } catch (e: Exception) {
                Log.e("ClockCheck", "Error checking clock status", e)
                if (isAdded && _binding != null) {
                    showToast("Error checking status: ${e.message}")
                }
            }
        }
    }

    private suspend fun processUserData(document: DocumentSnapshot) {
        val dateKey = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        val clockouts = document.get("clockouts") as? Map<*, *>
        val isWorkingOnSunday = document.getBoolean("isWorkingOnSunday") ?: false
        val assignedBike = document.getString("currentBike") ?: "None"
        val clockedInTime = document.getTimestamp("clockInTime") ?: Timestamp.now()

        // Fix for ClassCastException - safely convert any Number to Double
        val clockinMileage = when (val mileage = document.get("clockinMileage")) {
            is Double -> mileage
            is Number -> mileage.toDouble()
            else -> 0.0
        }

        todayClockinMileage = clockinMileage

        val dailyTarget = document.getDouble("dailyTarget") ?: 0.0
        val sundayTarget = document.getDouble("sundayTarget") ?: 0.0
        val previousBalance = document.getDouble("currentInAppBalance") ?: 0.0

        withContext(Dispatchers.Main) {
            binding.previousInAppBalance.setText(previousBalance.toString())
        }

        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        targetAmount = if (dayOfWeek == Calendar.SUNDAY) sundayTarget else dailyTarget

        calculateDeviation()

        val now = Timestamp.now()
        val diffMillis = now.toDate().time - clockedInTime.toDate().time
        val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis) % 60
        timeElapsed = "${hours}h ${minutes}m"

        withContext(Dispatchers.Main) {
            when {
                clockouts?.containsKey(dateKey) == true -> {
                    binding.btnClockedOut.visibility = View.VISIBLE
                    binding.btnIsSundayCantClockOut.visibility = View.GONE
                    binding.btnNoAssignedBike.visibility = View.GONE
                    binding.btnClockOut.visibility = View.GONE
                }
                dayOfWeek == Calendar.SUNDAY && !isWorkingOnSunday -> {
                    binding.btnIsSundayCantClockOut.visibility = View.VISIBLE
                    binding.btnClockedOut.visibility = View.GONE
                    binding.btnNoAssignedBike.visibility = View.GONE
                    binding.btnClockOut.visibility = View.GONE
                }
                assignedBike == "None" -> {
                    binding.btnNoAssignedBike.visibility = View.VISIBLE
                    binding.btnClockedOut.visibility = View.GONE
                    binding.btnIsSundayCantClockOut.visibility = View.GONE
                    binding.btnClockOut.visibility = View.GONE
                }
                else -> {
                    userBike = assignedBike
                    checkAccountStatus(document)
                }
            }
        }
    }

    private suspend fun checkAccountStatus(document: DocumentSnapshot) {
        val isActive = document.getBoolean("isActive") ?: true
        val isDeleted = document.getBoolean("isDeleted") ?: false

        withContext(Dispatchers.Main) {
            if (!isActive || isDeleted) {
                FirebaseAuth.getInstance().signOut()
                showToast("You were logged out because this account has been flagged.")
            } else {
                binding.btnClockOut.visibility = View.VISIBLE
                binding.btnClockedOut.visibility = View.GONE
                binding.btnIsSundayCantClockOut.visibility = View.GONE
                binding.btnNoAssignedBike.visibility = View.GONE
            }
        }
    }

    /*Check of active status, today working status and company status ends here*/





    // Function to retrieve net income globally
    private fun getNetIncome(): Double {
        val nc: Double = netIncome
        return BigDecimal(nc).setScale(0, RoundingMode.HALF_UP).toDouble()
    }

    private fun getTarget(): Double {
        return targetAmount
    }
    private fun getClockinMileage() : Double {
        return todayClockinMileage
    }




    /*User biometric authentication begins here*/
    private suspend fun authenticateUser(): Boolean {
        val keyguardManager = requireContext().getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        // If device has no security set up at all, allow proceeding
        if (!keyguardManager.isDeviceSecure) {
            return true
        }

        return suspendCancellableCoroutine { continuation ->
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("BILLK MOTOLINK LTD")
                .setSubtitle("Confirm identity to proceed")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                private var wasResumed = false

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (!wasResumed) {
                        wasResumed = true
                        continuation.resume(true)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!wasResumed) {
                        wasResumed = true
                        // Only allow proceeding if it's because no credentials are enrolled
                        val result = when (errorCode) {
                            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> true
                            else -> false
                        }
                        continuation.resume(result)
                    }
                }

                override fun onAuthenticationFailed() {
                    // Wrong biometric was presented - don't resume here
                    // Let onAuthenticationError handle it
                }
            }

            continuation.invokeOnCancellation {
                // Handle cancellation if needed
            }

            BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(requireContext()),
                callback
            ).authenticate(promptInfo)
        }
    }
    /*User biometric authentication ends here*/




    /*Clocking out begins here*/

    private fun clockOut(mileage: Double) {
        if (!isAdded || _binding == null) return

        val user = FirebaseAuth.getInstance().currentUser ?: run {
            showToast("User not logged in")
            return
        }

        val clockOutLocation = _binding?.clockOutLocation?.selectedItem as? String ?: run {
            showToast("No location was found")
            return
        }

        // Use lifecycleScope to ensure operations are tied to fragment lifecycle
        lifecycleScope.launch {
            try {
                clockOutSafely(user, mileage, clockOutLocation)
            } catch (e: Exception) {
                Log.e("ClockOut", "Error during clock out", e)
                if (isAdded && _binding != null) {
                    showToast("Clock out failed: ${e.message}")
                    resetClockOutUI()
                }
            }
        }
    }

    private suspend fun clockOutSafely(user: FirebaseUser, mileage: Double, clockOutLocation: String) {
        // Validate inputs on main thread first
        val (grossIncome, todaysBalance, previousBalance) = withContext(Dispatchers.Main) {
            Triple(
                binding.inputGrossIncome.text.toString().toDoubleOrNull() ?: 0.0,
                binding.todaysInAppBalance.text.toString().toDoubleOrNull() ?: 0.0,
                binding.previousInAppBalance.text.toString().toDoubleOrNull() ?: 0.0
            )
        }

        // Validate expenses on main thread
        val expenses = validateExpenses() ?: return
        // Validation failed, toast already shown

        // Authenticate user
        lifecycleScope.launch {
            when (authenticateUser()) {
                true -> {
                    // Only proceed if:
                    // 1. Authentication succeeded, OR
                    // 2. No authentication is set up on device

                    // Show loading state
                    withContext(Dispatchers.Main) {
                        binding.clockingOutProgressBar.visibility = View.VISIBLE
                        binding.btnClockOut.visibility = View.GONE
                    }

                    try {
                        // Prepare clockout data
                        val dateKey = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
                        val clockoutData = createClockoutData(grossIncome, todaysBalance, previousBalance, expenses, mileage)

                        // Perform Firestore operations
                        val userRef = FirebaseFirestore.getInstance().collection("users").document(user.uid)
                        val userDoc = withContext(Dispatchers.IO) { userRef.get().await() }

                        if (!userDoc.exists()) {
                            showToast("User document not found")
                            return@launch
                        }

                        val (dTarget, sTarget, userName, pendingAmount) = extractUserData(userDoc)
                        val combinedUpdates = prepareUserUpdates(dateKey, clockoutData, pendingAmount, todaysBalance)

                        // Update user document
                        withContext(Dispatchers.IO) { userRef.update(combinedUpdates).await() }

                        // Calculate deviations
                        val deviationData = calculateDeviations(grossIncome, dTarget, sTarget)

                        // Perform additional operations
                        performPostClockoutOperations(user.uid, userName, clockOutLocation, deviationData)

                        // Update UI
                        withContext(Dispatchers.Main) {
                            if (isAdded && _binding != null) {
                                showToast("Clockout saved successfully!")
                                resetClockOutUI()
                                binding.previousInAppBalance.setText(todaysBalance.toString())
                            }
                        }
                    }
                    catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            showToast("Clockout failed: ${e.message}")
                            resetClockOutUI()
                        }
                    }

                }
                false -> {
                    // Authentication failed or was cancelled
                    showToast("Authentication cancelled or failed")
                    // Don't proceed with clockout
                }
            }
        }
    }

    private suspend fun validateExpenses(): Map<String, Double>? {
        val expenses = mutableMapOf<String, Double>()

        fun validateExpense(checkBox: CheckBox, input: EditText, key: String): Boolean {
            if (checkBox.isChecked) {
                val amount = input.text.toString().toDoubleOrNull()
                if (amount == null || amount <= 0.0) {
                    showToast("Uncheck empty expenses: $key")
                    return false
                }
                if (key == "Other") {
                    val description = binding.inputOtherExpenseDescription.text.toString().trim()
                    if (description.length <= 3) {
                        showToast("Your custom expense needs a better description")
                        return false
                    }
                    expenses[description] = amount
                } else {
                    expenses[key] = amount
                }
            }
            return true
        }

        return withContext(Dispatchers.Main) {
            if (!validateExpense(binding.checkBSExpense, binding.inputBSExpense, "Battery Swap")) return@withContext null
            if (!validateExpense(binding.checkLunchExpense, binding.inputLunchExpense, "Lunch")) return@withContext null
            if (!validateExpense(binding.checkDBExpense, binding.inputDBExpense, "Data Bundles")) return@withContext null
            if (!validateExpense(binding.checkPoliceExpense, binding.inputPoliceExpense, "Police")) return@withContext null
            if (!validateExpense(binding.checkOtherExpense, binding.inputOtherExpense, "Other")) return@withContext null

            expenses
        }
    }

    private fun createClockoutData(grossIncome: Double, todaysBalance: Double, previousBalance: Double, expenses: Map<String, Double>, mileage: Double): Map<String, Any> {
        return mapOf(
            "grossIncome" to grossIncome,
            "todaysInAppBalance" to todaysBalance,
            "previousInAppBalance" to previousBalance,
            "inAppDifference" to todaysBalance - previousBalance,
            "expenses" to expenses,
            "netIncome" to getNetIncome(),
            "clockinMileage" to getClockinMileage(),
            "clockoutMileage" to mileage,
            "mileageDifference" to mileage - getClockinMileage(),
            "posted_at" to Timestamp.now(),
            "timeElapsed" to timeElapsed
        )
    }

    private fun extractUserData(document: DocumentSnapshot): Quadruple<Int, Int, String, Double> {
        return Quadruple(
            document.getLong("dailyTarget")?.toInt() ?: 0,
            document.getLong("sundayTarget")?.toInt() ?: 0,
            document.getString("userName") ?: "",
            document.getDouble("pendingAmount") ?: 0.0
        )
    }

    private fun prepareUserUpdates(dateKey: String, clockoutData: Map<String, Any>, pendingAmount: Double, todaysBalance: Double): Map<String, Any> {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        return if (dayOfWeek == Calendar.SUNDAY) {
            mapOf(
                "clockouts.$dateKey" to clockoutData,
                "currentInAppBalance" to todaysBalance,
                "isWorkingOnSunday" to false,
                "isClockedIn" to false,
                "netClockedLastly" to getNetIncome(),
                "pendingAmount" to (pendingAmount + getNetIncome()),
                "lastClockDate" to Timestamp.now()
            )
        } else {
            mapOf(
                "clockouts.$dateKey" to clockoutData,
                "currentInAppBalance" to todaysBalance,
                "isClockedIn" to false,
                "netClockedLastly" to getNetIncome(),
                "pendingAmount" to (pendingAmount + getNetIncome()),
                "lastClockDate" to Timestamp.now()
            )
        }
    }

    private fun calculateDeviations(grossIncome: Double, dTarget: Int, sTarget: Int): Map<String, Double> {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        val netDeviation = if (dayOfWeek == Calendar.SUNDAY) {
            getNetIncome() - sTarget
        } else {
            getNetIncome() - dTarget
        }

        val grossDeviation = if (dayOfWeek == Calendar.SUNDAY) {
            grossIncome - sTarget
        } else {
            grossIncome - dTarget
        }

        return mapOf(
            "netIncome" to getNetIncome(),
            "grossIncome" to grossIncome,
            "netDeviation" to netDeviation,
            "grossDeviation" to grossDeviation,
            "netGrossDifference" to grossIncome - getNetIncome()
        )
    }

    private suspend fun performPostClockoutOperations(uid: String, userName: String, clockOutLocation: String, deviationData: Map<String, Double>) {
        try {
            // Calculate week info
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

            // Execute in parallel
            withContext(Dispatchers.IO) {
                val results = listOf(
                    async { postDeviationData(userName, path, deviationData) },
                    async { dropBikesAndBatteries(clockOutLocation, userName) },
                    async { updateNetIncome(uid, getNetIncome()) },
                    async {
                        Utility.notifyAdmins(
                            "$userName just clocked out.",
                            "Clockouts",
                            listOf("Admin", "CEO", "Systems, IT")
                        )
                        true // Ensure all async operations return Boolean for consistency
                    }
                ).awaitAll()

                // Safe cast and check
                val deviationSuccess = results[0] as? Boolean ?: false
                if (!deviationSuccess) {
                    withContext(Dispatchers.Main) {
                        showToast("Failed to post to weekly reports. Kindly get this clockout required.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PostClockout", "Error in post-clockout operations", e)
            throw e
        }
    }

    private fun resetClockOutUI() {
        binding.clockingOutProgressBar.visibility = View.GONE
        binding.btnClockedOut.visibility = View.VISIBLE
        binding.inputGrossIncome.setText("")
        binding.inputOtherExpenseDescription.setText("")
        binding.todaysInAppBalance.setText("")
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        if (isAdded && _binding != null) {
            Toast.makeText(requireContext(), message, duration).show()
        }
    }

    // Helper data class
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    /*Clocking out ends here*/




    /*Dropping bikes, batteries and userBike starts here*/

    private suspend fun dropBikesAndBatteries(location: String?, userName: String) = withContext(Dispatchers.IO) {
        val db = FirebaseFirestore.getInstance()
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: run {
            Log.w("Auth", "No authenticated user")
            return@withContext
        }

        try {
            // Parallel fetch of user data and general variables
            val (userSnapshot, generalSnapshot) = coroutineScope {
                val userDeferred = async {
                    db.collection("users")
                        .whereEqualTo("email", currentUserEmail)
                        .get().await()
                }
                val generalDeferred = async {
                    db.collection("general").document("general_variables").get().await()
                }
                Pair(userDeferred.await(), generalDeferred.await())
            }

            val userDoc = userSnapshot.documents.firstOrNull() ?: run {
                Log.w("Firestore", "User document not found")
                return@withContext
            }
            val userName = userDoc.getString("userName") ?: run {
                Log.w("Firestore", "Username not found")
                return@withContext
            }

            // Execute updates in parallel
            /*User State*/
            coroutineScope {
                val updateUserJob = launch {
                    try {
                        // Update both currentBike and isClockedIn in a single operation
                        db.collection("users").document(userDoc.id)
                            .update(
                                mapOf(
                                    "currentBike" to "None",
                                    "isClockedIn" to false
                                )
                            )
                            .await()
                    } catch (e: Exception) {
                        Log.e("UserUpdate", "Failed to update user status", e)
                        // Consider adding error handling/retry logic here
                    }
                }

                /*Batteries*/
                val updateBatteriesJob = launch {
                    db.runTransaction { transaction ->
                        val generalSnapshotMain = transaction.get(db.collection("general").document("general_variables"))
                        val batteriesMap = generalSnapshot.get("batteries") as? MutableMap<String, MutableMap<String, Any>>
                            ?: mutableMapOf()

                        batteriesMap.values
                            .filter { it["assignedRider"] == userName }
                            .forEach { battery ->
                                battery.apply {
                                    this["assignedRider"] = "None"
                                    this["assignedBike"] = "None"
                                    this["offTime"] = Timestamp.now()
                                    this["batteryLocation"] = location ?: "Unknown"
                                }
                            }

                        transaction.update(generalSnapshotMain.reference, "batteries", batteriesMap)
                    }.await()
                }

                /*Bikes*/
                val updateBikesJob = launch {
                    try {
                        // 1. Get current bikes map
                        val bikesMap = generalSnapshot.get("bikes") as? MutableMap<String, MutableMap<String, Any>>
                            ?: return@launch

                        // 2. Find the bike assigned to this user
                        val userBikeEntry = bikesMap.entries.find { (_, bikeData) ->
                            bikeData["assignedRider"] == userName
                        }

                        // 3. Update the found bike
                        userBikeEntry?.let { (bikeId, bikeData) ->
                            bikeData.apply {
                                put("isAssigned", false)
                                put("assignedRider", "None")
                            }

                            // 4. Update the database
                            db.collection("general").document("general_variables")
                                .update("bikes.$bikeId", bikeData)
                                .await()
                        }
                    } catch (e: Exception) {
                        Log.e("BikeUpdate", "Failed to update bike assignment", e)
                    }
                }

                // Wait for all updates to complete
                joinAll(updateUserJob, updateBatteriesJob, updateBikesJob)

            }
        } catch (e: Exception) {
            Log.e("Firestore", "dropBikesAndBatteries failed", e)
            throw e
        }
    }

    /*dropping bikes ends here*/




    /*Weekly reports start here*/
    private suspend fun postDeviationData(
        userName: String,
        path: String,
        deviationData: Map<String, Any>
    ): Boolean = coroutineScope {
        withContext(Dispatchers.IO) {
            val db = FirebaseFirestore.getInstance()
            val devRef = db.collection("deviations").document(path)
            val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault())
                .format(System.currentTimeMillis())

            val updates = mapOf(
                userName to mapOf(
                    dayOfWeek to deviationData
                )
            )

            retry(maxRetries = 3) { attempt ->
                try {
                    devRef.set(updates, SetOptions.merge())
                        .awaitWithTimeout(5000)
                    true
                } catch (e: Exception) {
                    delay(1000L * attempt)
                    throw e
                }
            }
        }
    }
    /*weekly reports end here*/





    // Generic retry helper
    private suspend fun <T> retry(
        maxRetries: Int,
        initialDelay: Long = 1000,
        block: suspend (attempt: Int) -> T
    ): T {
        var currentDelay = initialDelay
        repeat(maxRetries - 1) { attempt ->
            try {
                return block(attempt + 1)
            } catch (e: Exception) {
                delay(currentDelay)
                currentDelay *= 2
            }
        }
        return block(maxRetries) // Last attempt
    }
    // Extension function for timeout
    private suspend fun <T> Task<T>.awaitWithTimeout(timeout: Long): T {
        return withTimeout(timeout) { await() }
    }
    /*Weekly reports end here*/




    private suspend fun updateNetIncome(uid: String, incomeToAdd: Double) {
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(uid)

        val calendar = Calendar.getInstance()
        val currentMonth = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: "Unknown"
        calendar.add(Calendar.MONTH, 1)
        val nextMonth = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: "Unknown"

        val currentMonthField = "netIncomes.$currentMonth"
        val nextMonthField = "netIncomes.$nextMonth"
        val currentMonthFieldWD = "workedDays.$currentMonth"
        val nextMonthFieldWD = "workedDays.$nextMonth"

        try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val currentIncome = snapshot.getDouble(currentMonthField) ?: 0.0
                val currentDays = snapshot.getDouble(currentMonthFieldWD) ?: 0.0

                transaction.update(userRef, currentMonthField, currentIncome + incomeToAdd)
                transaction.update(userRef, currentMonthFieldWD, currentDays + 1)
                transaction.update(userRef, nextMonthField, FieldValue.delete())
                transaction.update(userRef, nextMonthFieldWD, FieldValue.delete())
            }.await()
        } catch (e: Exception) {
            Log.e("Firestore", "Failed to update net income", e)
            throw e
        }
    }

    // in this function, we also check if it's monday so we delete deviations
    // no we don't
    private fun getCommissionConstant() {
        val db = FirebaseFirestore.getInstance()

        db.collection("general").document("general_variables")
            .get()
            .addOnSuccessListener { document ->
                if (!isAdded || view == null) return@addOnSuccessListener // Prevent crash

                viewLifecycleOwner.lifecycleScope.launch {
                    val commissionConstant = document.getDouble("commissionPercentage") ?: 0.0
                    binding.inputCommission.setText((commissionConstant / 100).toString()) // Safe access
                }
            }
            .addOnFailureListener {
                if (!isAdded || view == null) return@addOnFailureListener
                binding.inputCommission.setText("Error loading commission") // Safe access
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

    fun getUserBike(): String {
        return userBike
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
