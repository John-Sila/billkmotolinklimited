package com.example.billkmotolinkltd.ui.clockouts

import android.graphics.Color
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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentClockoutsBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ClockoutsFragment : Fragment() {

    private var _binding: FragmentClockoutsBinding? = null
    private val binding get() = _binding!!
    private var netIncome: Double = 0.0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val clockoutsViewModel =
            ViewModelProvider(this).get(ClockoutsViewModel::class.java)

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
        val checkOtherExpense = binding.checkOtherExpense

        val inputBSExpense = binding.inputBSExpense
        val inputLunchExpense = binding.inputLunchExpense
        val inputPoliceExpense = binding.inputPoliceExpense
        val inputOtherExpense = binding.inputOtherExpense

        // Net Income Display
        val textNetIncome: TextView = binding.textNetIncome

        // Initially hide the expense inputs
        listOf(inputBSExpense, inputLunchExpense, inputPoliceExpense, inputOtherExpense).forEach {
            it.visibility = View.GONE
        }

        // Show/Hide EditText based on Checkbox state
        checkBSExpense.setOnCheckedChangeListener { _, isChecked ->
            inputBSExpense.visibility = if (isChecked) View.VISIBLE else View.GONE
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
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        // Attach TextWatcher to all inputs
        listOf(
            inputGrossIncome, inputCommission,
            inputTodaysInAppBalance, inputPreviousInAppBalance,
            inputBSExpense, inputLunchExpense, inputPoliceExpense, inputOtherExpense
        ).forEach { it.addTextChangedListener(textWatcher) }

        // Initial calculation
        calculateNetIncome(textNetIncome)
        getCommissionConstant()
        fetchPreviousInAppBalance()
        checkIfClocked()

        binding.btnClockOut.setOnClickListener{
            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Clock Out")
            title.setSpan(ForegroundColorSpan(Color.RED), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("Are you sure you want to submit this report? It can only be done once in a day.")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.warn)

            alertDialog.setPositiveButton("Yes") { _, _ ->
                clockOut()
            }

            alertDialog.setNegativeButton("No") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
            }

            val dialog = alertDialog.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

            dialog.show()
        }

//
    }

//    also check if they are active
    private fun checkIfClocked() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || !isAdded || _binding == null) {
            Toast.makeText(requireContext(), "User not logged in or Fragment not attached", Toast.LENGTH_SHORT).show()
            return
        }

        val firestore = FirebaseFirestore.getInstance()
        val companyRef = firestore.collection("general").document("general_variables")

        companyRef.get().addOnSuccessListener { companyDoc ->
            if (!isAdded || _binding == null) return@addOnSuccessListener // Check again before accessing binding

            val companyStatus = companyDoc.getString("companyState") ?: "Unknown"
            if (companyStatus == "Paused") {
                binding.btnCompanyPaused.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "Company is currently on hold.", Toast.LENGTH_LONG).show()
                return@addOnSuccessListener
            }

            val uid = user.uid
            val userRef = firestore.collection("users").document(uid)
            val dateKey = SimpleDateFormat("dd_MMM_yyyy", Locale.getDefault()).format(Date())

            userRef.get().addOnSuccessListener { document ->
                if (!isAdded || _binding == null) return@addOnSuccessListener // Check again before accessing binding

                val clockouts = document.get("clockouts") as? Map<*, *>
                val recordExists = clockouts?.containsKey(dateKey) == true

                binding.btnClockedOut.visibility = if (recordExists) View.VISIBLE else View.GONE
                binding.btnClockOut.visibility = if (recordExists) View.GONE else View.VISIBLE

                val isActive: Boolean = document.get("isActive") as Boolean
                if (!isActive) {
                    FirebaseAuth.getInstance().signOut()
                    Toast.makeText(requireContext(), "You were logged out because this account has been flagged.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Function to retrieve net income globally
    fun getNetIncome(): Double {
        val nc: Double = netIncome// your netIncome calculation or retrieval logic here
        return BigDecimal(nc).setScale(0, RoundingMode.HALF_UP).toDouble()
    }

    private fun clockOut() {
        if (!isAdded || _binding == null) return

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = user.uid
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
                expenses[key] = amount
            }
        }

        validateExpense(binding.checkBSExpense, binding.inputBSExpense, "Battery Swap")
        validateExpense(binding.checkLunchExpense, binding.inputLunchExpense, "Lunch")
        validateExpense(binding.checkPoliceExpense, binding.inputPoliceExpense, "Police")
        validateExpense(binding.checkOtherExpense, binding.inputOtherExpense, "Other")

        // Validate "Other" expense description
        if (binding.checkOtherExpense.isChecked) {
            val description = binding.inputOtherExpenseDescription.text.toString().trim()
            if (description.length <= 3) {
                Toast.makeText(requireContext(), "Your custom expense needs a better description", Toast.LENGTH_SHORT).show()
                isValid = false
                return
            }
            expenses["Other Description"] = description.toDoubleOrNull() ?: 0.0
        }

        // **STOP EXECUTION if there's a validation error**
        if (!isValid) return
        binding.clockingOutProgressBar.visibility = View.VISIBLE
        binding.btnClockOut.visibility = View.GONE


        val dateKey = SimpleDateFormat("dd_MMM_yyyy", Locale.getDefault()).format(Date())

        val clockoutData = mapOf(
            "grossIncome" to grossIncome,
            "todaysInAppBalance" to todaysBalance,
            "previousInAppBalance" to previousBalance,
            "inAppDifference" to inAppDifference,
            "expenses" to expenses,
            "netIncome" to getNetIncome()
        )

        // Post to Firestore

        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
            userRef.get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val dTarget = document.getLong("dailyTarget")?.toInt() ?: 0
                        val userName = document.getString("userName") ?: ""
                        val pendingAmount = document.getDouble("pendingAmount") ?: 0.0

                        // Prepare the data to update
                        val combinedUpdates = mapOf(
                            "clockouts.$dateKey" to clockoutData,
                            "currentInAppBalance" to todaysBalance,
                            "pendingAmount" to (pendingAmount + getNetIncome()),
                            "lastClockDate" to Timestamp.now(),
                        )

                        // Perform the update
                        userRef.update(combinedUpdates)
                            .addOnSuccessListener {
                                Toast.makeText(requireContext(), "Clockout saved successfully!", Toast.LENGTH_SHORT).show()
                                binding.btnClockedOut.visibility = View.VISIBLE
                                binding.btnClockOut.visibility = View.GONE
                                binding.clockingOutProgressBar.visibility = View.GONE
                                binding.inputGrossIncome.setText("")
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(requireContext(), "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                            }

                        // Get today's day (Mon, Tue, etc.)
                        val today = SimpleDateFormat("EEE", Locale.getDefault()).format(Calendar.getInstance().time)

                        // Define the days of the week in order
                        val daysOfWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

                        // Find today's index in the list
                        val todayIndex = daysOfWeek.indexOf(today)


                        val netDeviation = getNetIncome() - dTarget
                        val grossDeviation = grossIncome - dTarget

                        val deviationData = mapOf(
                            "netIncome" to getNetIncome(),
                            "grossIncome" to grossIncome,
                            "netDeviation" to netDeviation,
                            "grossDeviation" to grossDeviation,
                            "netGrossDifference" to grossIncome - getNetIncome(),
                        )

                        val calendar = Calendar.getInstance()

// Start of the week
                        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                        val startOfWeek = calendar.time
                        val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
                        val startOfWeekFormatted = dateFormat.format(startOfWeek)

// End of the week
                        calendar.add(Calendar.DAY_OF_WEEK, 6)
                        val endOfWeek = calendar.time
                        val endOfWeekFormatted = dateFormat.format(endOfWeek)

// Get week number (optional)
                        val week = calendar.get(Calendar.WEEK_OF_YEAR)

// Format the path
                        val path = "Week $week (${startOfWeekFormatted.replace("-", " ")} to ${endOfWeekFormatted.replace("-", " ")})"

                        postDeviationData(userName, path, deviationData)

                        // set general netIncomes for each month
                        updateNetIncome(uid, getNetIncome())

                    }
                }
        }
    }

    fun postDeviationData(userName: String, path: String, deviationData: Map<String, Any>) {
        val db = FirebaseFirestore.getInstance()
        val devRef = db.collection("deviations").document(path)

        val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(Calendar.getInstance().time)

        // Construct nested data structure: JohnDoe -> Monday -> {...}
        val nestedDayMap = mapOf(
            dayOfWeek to deviationData
        )

        val finalMap = mapOf(
            userName to nestedDayMap
        )

        devRef.set(finalMap, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("Firestore", "Deviation data posted for $userName -> $dayOfWeek in $path")
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Failed to post deviation data", e)
            }
    }


    fun updateNetIncome(uid: String, incomeToAdd: Double) {
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(uid)

        // Get the current date
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: "Unknown"

        // Determine the next month
        calendar.add(Calendar.MONTH, 1)
        val nextMonth = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: "Unknown"

        // Build paths for the current and next month's net income fields
        val currentMonthField = "netIncomes.$currentMonth"
        val nextMonthField = "netIncomes.$nextMonth"

        // Perform the update in a transaction
        db.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)

            // Increment current month's net income
            val currentIncome = snapshot.getDouble(currentMonthField) ?: 0.0
            val newIncome = currentIncome + incomeToAdd
            transaction.update(userRef, currentMonthField, newIncome)

            // Reset next month's net income to 0 if it doesn't exist
            if (!snapshot.contains(nextMonthField)) {
                transaction.update(userRef, nextMonthField, 0.0)
            }
        }.addOnSuccessListener {
            // Handle success
            println("Net income updated successfully.")
        }.addOnFailureListener { e ->
            // Handle failure
            println("Error updating net income: ${e.message}")
        }
    }


    //    in this function, we also check if it's monday so we delete deviations
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

    private fun fetchPreviousInAppBalance() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) return // Exit if no user is logged in

        val db = FirebaseFirestore.getInstance()
        val uid = user.uid

        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (!isAdded || view == null) return@addOnSuccessListener // Prevent crash

                val previousBalance = document.getDouble("currentInAppBalance") ?: 0.0
                viewLifecycleOwner.lifecycleScope.launch {
                    binding.previousInAppBalance.setText(previousBalance.toString()) // Safe UI update
                }
            }
            .addOnFailureListener { exception ->
                if (!isAdded || view == null) return@addOnFailureListener
                Toast.makeText(requireContext(), "Error loading balance: ${exception.message}", Toast.LENGTH_SHORT).show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
