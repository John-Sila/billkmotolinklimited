package com.example.billkmotolinkltd.ui.approvals

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentApprovalsBinding
import com.google.firebase.firestore.FirebaseFirestore
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.ui.User
import com.example.billkmotolinkltd.ui.UsersAdapter
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.toObjects

class ApprovalsFragment : Fragment() {

    private var _binding: FragmentApprovalsBinding? = null

    // This property is only valid between onCreateView and onDestroyView ofc.
    private val binding get() = _binding!!


    private lateinit var firestore: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var usersAdapter: UsersAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApprovalsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkIfHrBudgetExists()
        binding.btnApproveBudget.setOnClickListener {
            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Approve Budget")
            title.setSpan(ForegroundColorSpan(Color.GREEN), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("After this action, the budget will await funds disbursement from Accounts.")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.warn)

            alertDialog.setPositiveButton("Approve") { _, _ ->
                approveBudget()
            }

            alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
            }

            val dialog = alertDialog.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

            dialog.show()
        }
        binding.btnDeclineBudget.setOnClickListener {
            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Decline Budget?")
            title.setSpan(ForegroundColorSpan(Color.RED), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("Confirm action")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.warn)

            alertDialog.setPositiveButton("Decline") { _, _ ->
                declineBudget()
            }

            alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
            }

            val dialog = alertDialog.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

            dialog.show()
        }

        // Initialize Firestore
        firestore = FirebaseFirestore.getInstance()
        // Initialize RecyclerView
        recyclerView = view.findViewById(R.id.recyclerViewUsers)
        recyclerView.layoutManager = LinearLayoutManager(context)
        usersAdapter = UsersAdapter()
        recyclerView.adapter = usersAdapter

        // Fetch users data
        fetchUsersData()

    }

    private fun fetchUsersData() {

        val db = FirebaseFirestore.getInstance()
        val usersRef = db.collection("users")
        val query = usersRef
//            .whereNotEqualTo("isDeleted", true)
            .orderBy("lastClockDate", Query.Direction.DESCENDING)
        query
            .get()
            .addOnSuccessListener { documents ->
                val usersList = documents.map { document ->
                    User(
                        userName = document.getString("userName") ?: "",
                        email = document.getString("email") ?: "",
                        pendingAmount = document.getDouble("pendingAmount") ?: 0.0,
                        isWorkingOnSunday = document.getBoolean("isWorkingOnSunday") == true,
                        dailyTarget = document.getDouble("dailyTarget") ?: 0.0,
                        isActive = document.getBoolean("isActive") == true,
                        userRank = document.getString("userRank") ?: "",
                        currentInAppBalance = document.getDouble("currentInAppBalance") ?: 0.0,
                        sundayTarget = document.getDouble("sundayTarget") ?: 0.0
                    )
                }
                usersAdapter.submitList(usersList)
            }
            .addOnFailureListener { exception ->
                Log.e("FragmentApprovals", "Error fetching users data: ", exception)
            }


    }

    private fun checkIfHrBudgetExists() {
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("general").document("general_variables")

        docRef.get().addOnSuccessListener { document ->
            if (document.exists()) {

                val budget = document.get("budget")
                val budgetItems = document.get("budget.items")
                if (!isAdded || _binding == null) return@addOnSuccessListener
                binding.hrProgressBar.visibility = View.GONE

                if (budgetItems != null) {
                    if (!isAdded || _binding == null) return@addOnSuccessListener
                    Log.d("Firestore", "Budget exists: $budget")
                    binding.budgetExists.visibility = View.VISIBLE
                    binding.textHrUnavailable.visibility = View.GONE

                    val budget = document.get("budget") as? Map<*, *>  // Retrieve `budget` as a Map
                    val budgetStatus = budget?.get("budgetStatus") as? String
                    val budgetCost = budget?.get("budgetCost") as? Double
                    val items = budget?.get("items") as? List<Map<String, Any>>

                    items?.forEach { itemMap ->
                        val tableLayout = view?.findViewById<TableLayout>(R.id.tableLayout)

                        // Clear existing rows except the header if needed
                        tableLayout?.removeAllViews()

                        // Optional: Add table header
                        val headerRow = TableRow(context)
                        val headerParams = TableRow.LayoutParams(
                            TableRow.LayoutParams.MATCH_PARENT,
                            TableRow.LayoutParams.WRAP_CONTENT
                        )
                        headerRow.layoutParams = headerParams

                        val headerItem = TextView(context).apply {
                            text = "Item"
                            setPadding(8, 8, 8, 8)
                            setTypeface(null, Typeface.BOLD)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f) // Larger text size
                            setTextColor("#FFFF8800".toColorInt()) // Orange color
                            gravity = Gravity.CENTER
                        }
                        headerRow.addView(headerItem)

                        val headerCost = TextView(context).apply {
                            text = "Cost"
                            setPadding(8, 8, 8, 8)
                            setTypeface(null, Typeface.BOLD)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f) // Larger text size
                            setTextColor("#FFFF8800".toColorInt()) // Orange color
                            gravity = Gravity.CENTER
                        }
                        headerRow.addView(headerCost)


                        tableLayout?.addView(headerRow)



                        // Add rows dynamically
                        items.forEach { itemMap ->
                            val tableRow = TableRow(context)
                            tableRow.layoutParams = TableRow.LayoutParams(
                                TableRow.LayoutParams.MATCH_PARENT,
                                TableRow.LayoutParams.WRAP_CONTENT
                            )// Apply bottom border


                            val itemName = itemMap["name"] as? String ?: "Unknown Item"
                            val itemCost = itemMap["cost"] as? Double ?: 0.0

                            val rowItem = TextView(context).apply {
                                text = itemName
                                setPadding(8, 8, 8, 8)
                                gravity = Gravity.CENTER
                            }
                            tableRow.addView(rowItem)

                            val rowCost = TextView(context).apply {
                                text = "Ksh.${String.format(" %, .2f", itemCost)}"
                                setPadding(8, 8, 8, 8)
                                gravity = Gravity.CENTER
                            }
                            tableRow.addView(rowCost)
                            tableLayout?.addView(tableRow)
                        }





//                        totals
                        val tableRowForTotals = TableRow(context)
                        tableRowForTotals.layoutParams = TableRow.LayoutParams(
                            TableRow.LayoutParams.MATCH_PARENT,
                            TableRow.LayoutParams.WRAP_CONTENT
                        )// Apply bottom border


                        val rowItem = TextView(context).apply {
                            text = "Total Cost"
                            setPadding(8, 8, 8, 8)
                            gravity = Gravity.CENTER
                            setTypeface(null, Typeface.BOLD)
                        }
                        tableRowForTotals.addView(rowItem)

                        val rowCost = TextView(context).apply {
                            text = "Ksh.${String.format(" % .2f", budgetCost)}"
                            setPadding(8, 8, 8, 8)
                            setTextColor(resources.getColor(android.R.color.holo_green_dark))
                            gravity = Gravity.CENTER
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                        }
                        tableRowForTotals.addView(rowCost)
                        tableLayout?.addView(tableRowForTotals)

                        if (budgetStatus == "Approved") {
                            if (!isAdded || _binding == null) return@addOnSuccessListener
                            binding.btnApproveBudget.visibility = View.GONE
                            binding.btnDeclineBudget.visibility = View.GONE
                            binding.btnBudgetApproved.visibility = View.VISIBLE
                        }



                    } ?: Log.d("Firestore", "No items found in budget.")

                }
                else {
                    Log.d("Firestore", "Budget does not exist.")
                    if (!isAdded || _binding == null) return@addOnSuccessListener
                    binding.budgetExists.visibility = View.GONE
                    binding.textHrUnavailable.visibility = View.VISIBLE
                }
            } else {
                Log.d("Firestore", "Document does not exist.")
                // Handle case when document does not exist
            }
        }.addOnFailureListener { exception ->
            Log.e("Firestore", "Error checking budget", exception)
        }
    }

    private fun approveBudget() {
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("general").document("general_variables")

        if (!isAdded || _binding == null) return
        binding.btnApproveBudget.visibility = View.GONE
        binding.btnDeclineBudget.visibility = View.GONE
        binding.btnBudgetApproved.visibility = View.GONE
        binding.hrProgressBar2.visibility = View.VISIBLE

        // Update the 'budgetStatus' field to 'Approved'
        docRef.update("budget.budgetStatus", "Approved")
            .addOnSuccessListener {
                if (!isAdded || _binding == null) return@addOnSuccessListener
                binding.btnBudgetApproved.visibility = View.VISIBLE
                binding.hrProgressBar2.visibility = View.GONE
                // Successfully updated 'budgetStatus'
                checkIfHrBudgetExists()
                Log.d("Firestore", "Budget status updated to Approved")
            }
            .addOnFailureListener { e ->
                if (!isAdded || _binding == null) return@addOnFailureListener
                binding.btnApproveBudget.visibility = View.VISIBLE
                binding.btnDeclineBudget.visibility = View.VISIBLE
                binding.btnBudgetApproved.visibility = View.GONE
                binding.hrProgressBar2.visibility = View.GONE

                Toast.makeText(context, "Error approving budget", Toast.LENGTH_SHORT).show()
                checkIfHrBudgetExists()
                // Failed to update 'budgetStatus'
                Log.w("Firestore", "Error updating budget status", e)
            }
    }

    private fun declineBudget() {
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("general").document("general_variables")

        if (!isAdded || _binding == null) return
        binding.btnApproveBudget.visibility = View.GONE
        binding.btnDeclineBudget.visibility = View.GONE
        binding.btnBudgetApproved.visibility = View.GONE
        binding.hrProgressBar2.visibility = View.VISIBLE

        // Update the 'budgetStatus' field to 'Approved'
        docRef.update("budget.budgetStatus", "Declined")
            .addOnSuccessListener {
                if (!isAdded || _binding == null) return@addOnSuccessListener
                binding.hrProgressBar2.visibility = View.GONE
                // Successfully updated 'budgetStatus'
                Log.d("Firestore", "Budget status updated to Approved")
            }
            .addOnFailureListener { e ->
                if (!isAdded || _binding == null) return@addOnFailureListener
                binding.btnApproveBudget.visibility = View.VISIBLE
                binding.btnDeclineBudget.visibility = View.VISIBLE
                binding.btnBudgetApproved.visibility = View.GONE
                binding.hrProgressBar2.visibility = View.GONE
                Toast.makeText(context, "Error declining budget", Toast.LENGTH_SHORT).show()
                // Failed to update 'budgetStatus'
                Log.w("Firestore", "Error updating budget status", e)
            }

        // Delete the 'items' field from the 'budget' document
        docRef.update("budget.items", FieldValue.delete())
            .addOnSuccessListener {
                // Successfully deleted 'items' field
                checkIfHrBudgetExists()
                Log.d("Firestore", "Budget items deleted")
            }
            .addOnFailureListener { e ->
                // Failed to delete 'items' field
                Log.w("Firestore", "Error deleting budget items", e)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}