package com.example.billkmotolinkltd.ui.hr

import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentHrBinding
import com.example.billkmotolinkltd.ui.Utility
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class HrFragment : Fragment() {

    private var _binding: FragmentHrBinding? = null
    private val binding get() = _binding!!

    private lateinit var expenseContainer: LinearLayout
    private lateinit var btnAddExpense: Button
    private lateinit var btnRemoveExpense: Button
    private lateinit var textTotalCost: TextView

    private lateinit var cStatus: String
    private lateinit var bStatus: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHrBinding.inflate(inflater, container, false)
        val rootView = binding.root

        expenseContainer = binding.expenseContainer
        btnAddExpense = binding.btnAddExpense
        btnRemoveExpense = binding.btnRemoveExpense
        textTotalCost = binding.textTotalCost

        btnAddExpense.setOnClickListener { addExpenseSection() }
        btnRemoveExpense.setOnClickListener { removeExpenseSection() }

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!isAdded || _binding == null) return

        // Initialize click listener first for immediate responsiveness
        binding.btnSubmit.setOnClickListener {
            showBudgetConfirmationDialog()
        }

        // Load data in background
        lifecycleScope.launch {
            try {
                val document = withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance()
                        .collection("general")
                        .document("general_variables")
                        .get()
                        .await()
                }

                // Process data on background thread
                val budget = document.get("budget") as? Map<*, *>
                val budgetStatus = budget?.get("budgetStatus") as? String ?: ""
                val companyStatus = document.get("companyState") as? String ?: ""

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    if (isAdded && _binding != null) {
                        checkCompanyStatus(companyStatus, budgetStatus)
                    }
                }
            } catch (e: Exception) {
                // Handle error silently or log it
                Log.e("BudgetFragment", "Error loading budget data", e)
            }
        }
    }

    private fun showBudgetConfirmationDialog() {
        val alertDialog = AlertDialog.Builder(requireContext()).apply {
            // Custom title with green color
            val title = SpannableString("Submit Budget").apply {
                setSpan(ForegroundColorSpan(Color.GREEN), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            // Custom message with gray color
            val message = SpannableString("Confirm submission of a new budget.").apply {
                setSpan(ForegroundColorSpan(Color.GRAY), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            setTitle(title)
            setMessage(message)
            setIcon(R.drawable.success)

            setPositiveButton("Confirm") { _, _ ->
                lifecycleScope.launch {
                    submitBudget()
                }
            }

            setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        }

        alertDialog.create().apply {
            window?.setBackgroundDrawableResource(R.drawable.rounded_black)
            show()

            getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.GREEN)  // confirm button
            getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.RED)
        }

    }





    /*Checking budget status starts here*/
    private fun checkCompanyStatus(state: String, budget: String) {
        if (!isAdded || _binding == null) return

        // Execute UI updates in a single batch
        lifecycleScope.launch(Dispatchers.Main) {
            when {
                state == "Paused" -> handlePausedState()
                state == "Continuing" -> handleContinuingState(budget)
                else -> handleDefaultState()
            }
        }
    }

    private fun handlePausedState() {
        binding.btnOperationsPaused.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "Company is on hold.", Toast.LENGTH_SHORT).show()
    }

    private fun handleContinuingState(budget: String) {
        binding.btnOperationsPaused.visibility = View.GONE

        // Reset all views first to avoid state conflicts
        resetAllBudgetViews()

        when (budget) {
            "Pending" -> {
                binding.btnCantSubmit.visibility = View.VISIBLE
                binding.contactAdmin.visibility = View.VISIBLE
            }
            "Approved" -> {
                binding.approvedUnDisbursed.visibility = View.VISIBLE
                binding.contactAccounts.visibility = View.VISIBLE
            }
            "Declined" -> {
                binding.btnSubmit.visibility = View.VISIBLE
                binding.budgetDeclined.visibility = View.VISIBLE
            }
            "Disbursed" -> {
                binding.btnSubmit.visibility = View.VISIBLE
            }
            else -> {
                binding.btnSubmit.visibility = View.VISIBLE
            }
        }
    }

    private fun handleDefaultState() {
        resetAllBudgetViews()
        binding.btnSubmit.visibility = View.VISIBLE
    }

    private fun resetAllBudgetViews() {
        // Group all view resets in one place
        binding.apply {
            btnSubmit.visibility = View.GONE
            btnCantSubmit.visibility = View.GONE
            approvedUnDisbursed.visibility = View.GONE
            budgetDeclined.visibility = View.GONE
            contactAdmin.visibility = View.GONE
            contactAccounts.visibility = View.GONE
        }
    }

    /*Checking budget status ends here*/




    /*Submitting budget starts here*/
    private fun submitBudget() {
        if (!isAdded || _binding == null) return

        // Show loading state immediately
        binding.hrProgressBar.visibility = View.VISIBLE
        binding.btnSubmit.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // Process form data on background thread
                val (budgetList, totalCost) = withContext(Dispatchers.Default) {
                    processBudgetItems()
                }

                // Validate results on main thread
                withContext(Dispatchers.Main) {
                    if (!isAdded || _binding == null) return@withContext

                    validateBudgetData(budgetList, totalCost) ?: return@withContext
                    val budgetHeading = binding.budgetHeading.text.toString().trim()

                    if (budgetHeading.length <= 5) {
                        showErrorState("You need a better heading for your budget.")
                        return@withContext
                    }

                    // Proceed with Firestore update
                    updateBudgetInFirestore(budgetList, totalCost, budgetHeading)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showErrorState("Error: ${e.message ?: "Unknown error"}")
                }
            }
        }
    }

    private fun processBudgetItems(): Pair<List<Map<String, Any>>, Double> {
        val budgetList = mutableListOf<Map<String, Any>>()
        var totalCost = 0.0

        for (i in 0 until binding.expenseContainer.childCount) {
            val itemView = binding.expenseContainer.getChildAt(i)

            if (itemView is LinearLayout && itemView.childCount == 2) {
                val nameInput = itemView.getChildAt(0) as? EditText
                val costInput = itemView.getChildAt(1) as? EditText

                if (nameInput == null || costInput == null) continue

                val nameText = nameInput.text.toString().trim()
                val costText = costInput.text.toString().trim()

                if (nameText.isEmpty() || costText.isEmpty()) {
                    throw Exception("Please fill or delete empty inputs")
                }

                val cost = costText.toDoubleOrNull()
                    ?: throw Exception("Invalid cost entered")

                budgetList.add(mapOf("name" to nameText, "cost" to cost))
                totalCost += cost
            }
        }

        if (totalCost.toInt() == 0) {
            throw Exception("Your budget has a 0 total cost!")
        }

        return Pair(budgetList, totalCost)
    }

    private fun validateBudgetData(
        budgetList: List<Map<String, Any>>,
        totalCost: Double
    ): Boolean {
        if (budgetList.isEmpty()) {
            showErrorState("Please add at least one budget item")
            return false
        }

        if (totalCost <= 0) {
            showErrorState("Total cost must be greater than 0")
            return false
        }

        return true
    }

    private fun updateBudgetInFirestore(
        budgetList: List<Map<String, Any>>,
        totalCost: Double,
        budgetHeading: String
    ) {
        val db = FirebaseFirestore.getInstance()
        val budgetRef = db.collection("general").document("general_variables")

        val updates = mapOf(
            "budget.items" to budgetList,
            "budget.budgetStatus" to "Pending",
            "budget.budgetHeading" to budgetHeading,
            "budget.budgetCost" to totalCost,
            "budget.postedAt" to Timestamp.now()
        )

        budgetRef.update(updates)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Budget submitted successfully!", Toast.LENGTH_SHORT).show()
                binding.hrProgressBar.visibility = View.GONE

                // Load updated status
                loadCompanyStatus()

                // Notify admins
                lifecycleScope.launch {
                    val roles = listOf("Admin", "CEO", "Systems, IT")
                    Utility.notifyAdmins("A new budget was posted", "Human Resource", roles)
                }
            }
            .addOnFailureListener { e ->
                showErrorState("Error: ${e.message ?: "Unknown error"}", showContactAdmin = true)
            }
    }

    private fun loadCompanyStatus() {
        FirebaseFirestore.getInstance()
            .collection("general")
            .document("general_variables")
            .get()
            .addOnSuccessListener { document ->
                val budget = document.get("budget") as? Map<*, *>
                val budgetStatus = budget?.get("budgetStatus") as? String ?: ""
                val companyStatus = document.get("companyState") as? String ?: ""

                checkCompanyStatus(companyStatus, budgetStatus)
            }
    }

    private fun showErrorState(message: String, showContactAdmin: Boolean = false) {
        if (!isAdded || _binding == null) return

        binding.hrProgressBar.visibility = View.GONE
        binding.btnSubmit.visibility = View.VISIBLE
        binding.contactAdmin.visibility = if (showContactAdmin) View.VISIBLE else View.GONE

        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    /*Submitting budget ends here*/





    private fun addExpenseSection() {
        val context = requireContext()

        val expenseLayout = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
        }

        val inputItem = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            hint = "Item"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        val inputCost = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            hint = "Cost (Ksh)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    updateTotalCost()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        expenseLayout.addView(inputItem)
        expenseLayout.addView(inputCost)

        expenseContainer.addView(expenseLayout, expenseContainer.childCount - 2) // Before total
        updateTotalCost()
    }

    private fun removeExpenseSection() {
        val childCount = expenseContainer.childCount
        if (childCount > 2) { // Ensure at least buttons and total remain
            expenseContainer.removeViewAt(childCount - 3) // Remove last added expense
        }
        updateTotalCost()
    }

    private fun updateTotalCost() {
        var totalCost = 0
        for (i in 0 until expenseContainer.childCount - 2) {
            val expenseLayout = expenseContainer.getChildAt(i) as? LinearLayout
            val inputCost = expenseLayout?.getChildAt(1) as? EditText
            val cost = inputCost?.text.toString().toIntOrNull() ?: 0
            totalCost += cost
        }

        // Format the number with commas and two decimal places
        val formattedTotal = String.format("%,.2f", totalCost.toDouble())
        textTotalCost.text = "Total: Ksh $formattedTotal"
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

