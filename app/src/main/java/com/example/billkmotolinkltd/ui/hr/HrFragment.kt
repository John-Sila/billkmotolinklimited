package com.example.billkmotolinkltd.ui.hr

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
import androidx.fragment.app.Fragment
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentHrBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

class HrFragment : Fragment() {

    private var _binding: FragmentHrBinding? = null
    private val binding get() = _binding!!

    private lateinit var expenseContainer: LinearLayout
    private lateinit var btnAddExpense: Button
    private lateinit var btnRemoveExpense: Button
    private lateinit var textTotalCost: TextView

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

        val userRef = FirebaseFirestore.getInstance()
            .collection("general")
            .document("general_variables")
        userRef.get()
            .addOnSuccessListener { document ->
                val budget = document.get("budget") as? Map<*, *>  // Retrieve `budget` as a Map
                val budgetStatus = budget?.get("budgetStatus") as? String  // Access `budgetStatus`
                val companyStatus = document.get("companyState") as? String

                checkCompanyStatus(companyStatus.toString(), budgetStatus.toString())
            }
        if (!isAdded || _binding == null) return
        binding.btnSubmit.setOnClickListener{
            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Submit Budget")
            title.setSpan(ForegroundColorSpan(Color.GREEN), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("Confirm submission of a new budget.")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.success)

            alertDialog.setPositiveButton("Confirm") { _, _ ->
                submitBudget()
            }

            alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
            }

            val dialog = alertDialog.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

            dialog.show()
        }
    }

    private fun checkCompanyStatus(state: String, budget: String) {
        if (state == "Paused") {
            if (!isAdded || _binding == null) return
            binding.btnOperationsPaused.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Company is paused", Toast.LENGTH_SHORT).show()
            return
        } else if (state == "Continuing") {
            if (!isAdded || _binding == null) return // Prevent crash
            binding.btnOperationsPaused.visibility = View.GONE
            if (budget == "Pending") {
                binding.btnCantSubmit.visibility = View.VISIBLE
                binding.btnSubmit.visibility = View.GONE
                binding.approvedUnDisbursed.visibility = View.GONE

                binding.budgetDeclined.visibility = View.GONE

                binding.contactAdmin.visibility = View.VISIBLE
            }
            else if (budget == "Approved") {
                binding.approvedUnDisbursed.visibility = View.VISIBLE
                binding.btnSubmit.visibility = View.GONE
                binding.btnCantSubmit.visibility = View.GONE

                binding.budgetDeclined.visibility = View.GONE

                binding.contactAccounts.visibility = View.VISIBLE
            }
            else if (budget == "Declined") {
                binding.approvedUnDisbursed.visibility = View.GONE
                binding.btnSubmit.visibility = View.VISIBLE
                binding.btnCantSubmit.visibility = View.GONE


                binding.budgetDeclined.visibility = View.VISIBLE
            }
            else if (budget == "Disbursed") {
                binding.approvedUnDisbursed.visibility = View.GONE
                binding.btnSubmit.visibility = View.VISIBLE
                binding.btnCantSubmit.visibility = View.GONE


                binding.budgetDeclined.visibility = View.GONE
            }
            else {
                binding.approvedUnDisbursed.visibility = View.GONE
                binding.btnSubmit.visibility = View.VISIBLE
                binding.btnCantSubmit.visibility = View.GONE


                binding.budgetDeclined.visibility = View.GONE
            }
        }
    }

    private fun submitBudget() {
        if (!isAdded || _binding == null) return
        binding.hrProgressBar.visibility = View.VISIBLE
        binding.btnSubmit.visibility = View.GONE

        val container = binding.expenseContainer
        val budgetList = mutableListOf<Map<String, Any>>()
        var totalCost = 0.0

        for (i in 0 until expenseContainer.childCount) {
            val itemView = expenseContainer.getChildAt(i)

            // Ensure the view is a LinearLayout and contains exactly 2 EditTexts (name and cost)
            if (itemView is LinearLayout && itemView.childCount == 2) {
                val nameInput = itemView.getChildAt(0) as? EditText
                val costInput = itemView.getChildAt(1) as? EditText

                if (nameInput == null || costInput == null) continue // Skip if not both are EditTexts

                val nameText = nameInput.text.toString().trim()
                val costText = costInput.text.toString().trim()

                Log.d("BudgetSubmission", "Item: $nameText, Cost: $costText")

                if (nameText.isEmpty() || costText.isEmpty()) {
                    Toast.makeText(requireContext(), "Please fill or delete empty inputs", Toast.LENGTH_SHORT).show()
                    return
                }

                val cost = costText.toDoubleOrNull()
                if (cost == null) {
                    Toast.makeText(requireContext(), "Invalid cost entered", Toast.LENGTH_SHORT).show()
                    return
                }

                budgetList.add(mapOf("name" to nameText, "cost" to cost))
                totalCost += cost
            }
        }

        if (totalCost.toInt() == 0) {
            Toast.makeText(requireContext(), "Your budget has a 0 total cost!", Toast.LENGTH_SHORT).show()
            return
        }


        // Now you can submit budgetList to Firestore
        val db = FirebaseFirestore.getInstance()
        val budgetRef = db.collection("general").document("general_variables")

        val budgetData = mapOf("items" to budgetList)

        budgetRef.update("budget.items", budgetList)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Budget submitted successfully!", Toast.LENGTH_SHORT).show()
                binding.hrProgressBar.visibility = View.GONE
                binding.btnSubmit.visibility = View.GONE
                binding.hrProgressBar.visibility = View.GONE
                binding.btnCantSubmit.visibility = View.VISIBLE
                binding.approvedUnDisbursed.visibility = View.GONE

                binding.budgetDeclined.visibility = View.GONE
            }
            .addOnFailureListener { e ->

                binding.contactAdmin.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        budgetRef.update("budget.budgetStatus", "Pending")
            .addOnSuccessListener {
//                Toast.makeText(requireContext(), "Budget submitted successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
//                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        budgetRef.update("budget.budgetCost", totalCost)
            .addOnSuccessListener {
//                Toast.makeText(requireContext(), "Budget submitted successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
//                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }

    }

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
        textTotalCost.text = "Total: Ksh $totalCost"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

