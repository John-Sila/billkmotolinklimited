package com.billkmotolink.ltd.ui.approvals

import android.content.Context
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
import android.view.animation.OvershootInterpolator
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.billkmotolink.ltd.R
import com.google.firebase.firestore.FirebaseFirestore
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.billkmotolink.ltd.ui.User
import com.billkmotolink.ltd.ui.UsersAdapter
import com.billkmotolink.ltd.ui.Utility
import com.billkmotolink.ltd.ui.formatIncome
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import androidx.lifecycle.lifecycleScope
import com.billkmotolink.ltd.databinding.FragmentApprovalsBinding
import com.billkmotolink.ltd.ui.LocationData
import com.billkmotolink.ltd.ui.RequirementData
import com.billkmotolink.ltd.ui.UsersAdapter.UserViewHolder.TransactionResult
import kotlinx.coroutines.launch

class ApprovalsFragment : Fragment() {

    private var _binding: FragmentApprovalsBinding? = null

    // This property is only valid between onCreateView and onDestroyView ofc.
    private val binding get() = _binding!!

    private lateinit var firestore: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var usersAdapter: UsersAdapter
    private var isTableVisible = true

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

            alertDialog.create().apply {
                window?.setBackgroundDrawableResource(R.drawable.rounded_black)
                show()

                // Change button text colors after showing
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.GREEN)  // confirm button
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.RED)    // cancel button
            }
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

            alertDialog.create().apply {
                window?.setBackgroundDrawableResource(R.drawable.rounded_black)
                show()

                // Change button text colors after showing
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.GREEN)  // confirm button
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.RED)    // cancel button
            }
        }
        binding.btnApproveAllUsersIncome.setOnClickListener {
            approveAllUsersIncome(binding.approveAllUsersIncomeProgressBar, requireContext())
        }

        // Initialize Firestore
        firestore = FirebaseFirestore.getInstance()
        // Initialize RecyclerView
        recyclerView = view.findViewById(R.id.recyclerViewUsers)
        recyclerView.layoutManager = LinearLayoutManager(context)
        usersAdapter = UsersAdapter(this)
        recyclerView.adapter = usersAdapter

        // Fetch users data
        fetchUsersData()
        checkIfHrBudgetExists()

    }

    fun fetchUsersData() {
        if (!isAdded || view == null) return
        binding.approvalsProgressBar.visibility = View.VISIBLE

        usersAdapter.submitList(emptyList())

        val db = FirebaseFirestore.getInstance()
        val usersRef = db.collection("users")
        val query = usersRef
            .orderBy("lastClockDate", Query.Direction.DESCENDING)

        query.get()
            .addOnSuccessListener { documents ->
                val usersList = documents.mapNotNull { document ->
                    val isDeleted = document.getBoolean("isDeleted") == true
                    val userRank = document.getString("userRank") ?: ""
                    val thisEmail = document.getString("email") ?: ""

                    val auth = FirebaseAuth.getInstance()
                    val currentUserEmail = auth.currentUser?.email ?: return@addOnSuccessListener
                    if (isDeleted || userRank == "CEO" || userRank == "Systems, IT" || thisEmail == currentUserEmail) return@mapNotNull null

                    val locationData = document.get("location") as? Map<*, *>
                    val latitude = (locationData?.get("latitude") as? Double) ?: 0.0
                    val longitude = (locationData?.get("longitude") as? Double) ?: 0.0
                    val locationTimestamp = (locationData?.get("timestamp") as? Long) ?: 0L

                    User(
                        userName = document.getString("userName") ?: "",
                        email = document.getString("email") ?: "",
                        pendingAmount = document.getDouble("pendingAmount") ?: 0.0,
                        isWorkingOnSunday = document.getBoolean("isWorkingOnSunday") == true,
                        dailyTarget = document.getDouble("dailyTarget") ?: 0.0,
                        isActive = document.getBoolean("isActive") == true,
                        isDeleted = false,
                        userRank = document.getString("userRank") ?: "",
                        currentInAppBalance = document.getDouble("currentInAppBalance") ?: 0.0,
                        sundayTarget = document.getDouble("sundayTarget") ?: 0.0,
                        location = LocationData(latitude, longitude, locationTimestamp),
                        requirements = document.get("requirements") as? Map<String, RequirementData> ?: emptyMap()
                    )
                }

                if (isAdded && view != null) {
                    binding.approvalsProgressBar.visibility = View.GONE
                }
                if (!isAdded || view == null) return@addOnSuccessListener
                if (usersList.isEmpty()) {
                    binding.noUsers.visibility = View.VISIBLE
                } else {
                    binding.noUsers.visibility = View.GONE
                }
                usersAdapter.submitList(usersList)
                binding.btnApproveAllUsersIncome.visibility = View.VISIBLE

            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Failed to load users", Toast.LENGTH_SHORT).show()
                if (!isAdded || view == null) return@addOnFailureListener
                binding.approvalsProgressBar.visibility = View.GONE
            }
    }

    private fun slideDown(view: View) {
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationY = -view.height.toFloat()
        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setInterpolator(OvershootInterpolator())
            .setDuration(500)
            .start()
    }
    private fun slideUp(view: View) {
        view.animate()
            .translationY(-view.height.toFloat())
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                view.visibility = View.GONE
                view.translationY = 0f // reset
                view.alpha = 1f
            }
            .start()
    }

    /*absolute approval*/
    private fun approveAllUsersIncome(progressbar: View, context: Context) {
        val alertDialog = AlertDialog.Builder(context)

        val title = SpannableString("Complete Global Approval").apply {
            setSpan(ForegroundColorSpan(Color.GREEN), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val message = SpannableString("Approve all pending income from all users.")
        message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        alertDialog.setTitle(title)
        alertDialog.setMessage(message)
        alertDialog.setIcon(R.drawable.warn)

        alertDialog.setPositiveButton("Approve All") { _, _ ->
            progressbar.visibility = View.VISIBLE

            val db = FirebaseFirestore.getInstance()
            val usersRef = db.collection("users")
            val generalRef = db.collection("general").document("general_variables")

            usersRef
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (querySnapshot.isEmpty) {
                        Toast.makeText(context, "No users found.", Toast.LENGTH_SHORT).show()
                        progressbar.visibility = View.GONE
                        return@addOnSuccessListener
                    }

                    val userDocs = querySnapshot.documents
                    val usersWithPending = userDocs.filter {
                        (it.getDouble("pendingAmount") ?: 0.0) > 0.0
                    }

                    if (usersWithPending.isEmpty()) {
                        Toast.makeText(context, "No pending income to approve.", Toast.LENGTH_SHORT).show()
                        progressbar.visibility = View.GONE
                        return@addOnSuccessListener
                    }

                    db.runTransaction { transaction ->
                        var totalApproved = 0.0
                        val approvedUsers = mutableListOf<String>()

                        // Read general variables first
                        val generalSnapshot = transaction.get(generalRef)
                        val currentCompanyIncome = generalSnapshot.getDouble("companyIncome") ?: 0.0

                        // Loop over each user
                        for (userDoc in usersWithPending) {
                            val ref = userDoc.reference
                            val pending = userDoc.getDouble("pendingAmount") ?: 0.0
                            val userName = userDoc.getString("userName") ?: "Unnamed"

                            totalApproved += pending
                            approvedUsers.add(userName)

                            transaction.update(ref, "pendingAmount", 0.0)
                        }

                        // Update company income
                        transaction.update(generalRef, "companyIncome", currentCompanyIncome + totalApproved)

                        // Return the result
                        TransactionResult(
                            totalApproved,
                            approvedUsers.joinToString(", "),
                            currentCompanyIncome,
                            "Batch approval"
                        )
                    }.addOnSuccessListener { result ->
                        progressbar.visibility = View.GONE

                        val total = formatIncome(result.currentPending)
                        val newBalance = formatIncome(result.currentIncome + result.currentPending)
                        val msg = buildString {
                            append("Approved all pending income for all users.\n")
                            append("Total Approved: $total\n")
                            append("Company Balance: $newBalance\n")
                            append("Users included: ${result.username}")
                        }

                        Utility.postCashFlow(msg, true)
                        Toast.makeText(context, "All income approved successfully.", Toast.LENGTH_SHORT).show()
                        fetchUsersData()

                    }.addOnFailureListener { e ->
                        progressbar.visibility = View.GONE
                        Log.e("GlobalApproval", "Transaction failed", e)
                        Toast.makeText(context, "Approval failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener { e ->
                    progressbar.visibility = View.GONE
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        alertDialog.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        alertDialog.create().apply {
            window?.setBackgroundDrawableResource(R.drawable.rounded_black)
            show()
            getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.GREEN)
            getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.RED)
        }
    }

    private fun checkIfHrBudgetExists() {
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("general").document("general_variables")

        if (!isAdded || _binding == null) return

        docRef.get().addOnSuccessListener { document ->
            if (!isAdded || _binding == null) return@addOnSuccessListener // Guard again here
            if (document.exists()) {

                val budgetRaw = document.get("budget") as? Map<*, *>

                if (budgetRaw == null || budgetRaw.isEmpty()) {
                    // Handle missing or empty budget data
                    Toast.makeText(requireContext(), "No budget data available.", Toast.LENGTH_SHORT).show()
                    binding.budgetExists.visibility = View.GONE
                    return@addOnSuccessListener
                }
                binding.budgetExists.visibility = View.VISIBLE

                val budgetItems = budgetRaw["items"] as? List<*> ?: emptyList<Any>()
                if (!isAdded || _binding == null) return@addOnSuccessListener
                // Log.d("Firestore", "Budget exists: $budget")

                val budgetStatus = budgetRaw["budgetStatus"] as? String
                val budgetCost = budgetRaw["budgetCost"] as? Double
                val budgetHeading = budgetRaw["budgetHeading"] as? String ?: "No Heading Available"

                binding.budgetHeading.text = budgetHeading

                binding.budgetToggleLayout.setOnClickListener {
                    if (!isTableVisible) {
                        slideDown(binding.tableLayout)
                    } else {
                        slideUp(binding.tableLayout)
                    }

                    isTableVisible = !isTableVisible

                    val arrowRes = if (isTableVisible) {
                        R.drawable.ic_arrow_up
                    } else {
                        R.drawable.ic_arrow_down
                    }
                    binding.arrowIcon.setImageResource(arrowRes)
                }

                val tableLayout = view?.findViewById<TableLayout>(R.id.tableLayout)
                tableLayout?.removeAllViews()

                val headerRow = TableRow(context).apply {
                    layoutParams = TableRow.LayoutParams(
                        TableRow.LayoutParams.MATCH_PARENT,
                        TableRow.LayoutParams.WRAP_CONTENT
                    )
                }

                val headerItem = TextView(context).apply {
                    text = "Item"
                    setPadding(8, 8, 8, 8)
                    setTypeface(null, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    setTextColor("#FFFF8800".toColorInt())
                    gravity = Gravity.CENTER
                }
                headerRow.addView(headerItem)

                val headerCost = TextView(context).apply {
                    text = "Cost"
                    setPadding(8, 8, 8, 8)
                    setTypeface(null, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    setTextColor("#FFFF8800".toColorInt())
                    gravity = Gravity.CENTER
                }
                headerRow.addView(headerCost)

                tableLayout?.addView(headerRow)

                // Add item rows
                budgetItems.forEach { item ->
                    val itemMap = item as? Map<*, *> ?: return@forEach

                    val itemName = itemMap["name"] as? String ?: "Unknown Item"
                    val itemCost = when (val cost = itemMap["cost"]) {
                        is Number -> cost.toDouble()  // handles Long, Int, Double etc.
                        else -> 0.0
                    }
                    val tableRow = TableRow(context).apply {
                        layoutParams = TableRow.LayoutParams(
                            TableRow.LayoutParams.MATCH_PARENT,
                            TableRow.LayoutParams.WRAP_CONTENT
                        )
                    }

                    val rowItem = TextView(context).apply {
                        text = itemName
                        setPadding(8, 8, 8, 8)
                        gravity = Gravity.CENTER
                    }

                    val rowCost = TextView(context).apply {
                        text = "${formatIncome(itemCost)}"
                        setPadding(8, 8, 8, 8)
                        gravity = Gravity.CENTER
                    }

                    tableRow.addView(rowItem)
                    tableRow.addView(rowCost)
                    tableLayout?.addView(tableRow)
                }

                // Add totals row
                val tableRowForTotals = TableRow(context).apply {
                    layoutParams = TableRow.LayoutParams(
                        TableRow.LayoutParams.MATCH_PARENT,
                        TableRow.LayoutParams.WRAP_CONTENT
                    )
                }

                val totalLabel = TextView(context).apply {
                    text = "Total Cost"
                    setPadding(8, 8, 8, 8)
                    gravity = Gravity.CENTER
                    setTypeface(null, Typeface.BOLD)
                }

                val totalCost = TextView(context).apply {
                    text = "${formatIncome(budgetCost ?: 0.0)}"
                    setPadding(8, 8, 8, 8)
                    setTextColor(resources.getColor(android.R.color.holo_green_dark))
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                }

                tableRowForTotals.addView(totalLabel)
                tableRowForTotals.addView(totalCost)

                tableLayout?.addView(tableRowForTotals)

                checkUserRole(budgetStatus)
            }
            else {
                Log.d("Firestore", "Document does not exist.")
                // Handle case when document does not exist
                _binding.apply {
                    binding.budgetCard.visibility = View.GONE
                }
            }
        }.addOnFailureListener { exception ->
            Log.e("Firestore", "Error checking budget", exception)
        }
    }

    private fun checkUserRole(budgetStatus: String?) {
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
            val userId = currentUser.uid // Unique user ID
            val userRef = FirebaseFirestore.getInstance().collection("users").document(userId)

            userRef.get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val rank = document.getString("userRank") ?: ""
                        if (budgetStatus == "Approved") {
                            binding.btnBudgetApproved.visibility = View.VISIBLE
                            binding.declineAndApproveButtons.visibility = View.GONE
                        } else if (budgetStatus == "Declined") {
                            binding.btnBudgetDeclined.visibility = View.VISIBLE
                        } else if (budgetStatus == "Disbursed") {
                            binding.btnBudgetDisbursed.visibility = View.VISIBLE
                        } else if (budgetStatus == "Pending" && rank in arrayOf("Admin", "Systems, IT")) {
                            binding.declineAndApproveButtons.visibility = View.VISIBLE
                        } else if (budgetStatus == "Pending" && rank in arrayOf("CEO")) {
                            binding.btnBudgetPending.visibility = View.VISIBLE
                        }

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
                lifecycleScope.launch {
                    val roles = listOf("Admin", "CEO", "Systems, IT", "HR")
                    Utility.notifyAdmins("HR budget approved and awaits disbursement.", "Human Resource", roles)
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded || _binding == null) return@addOnFailureListener
                binding.hrProgressBar2.visibility = View.GONE

                Toast.makeText(context, "Error approving budget", Toast.LENGTH_SHORT).show()
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
                checkIfHrBudgetExists()
                lifecycleScope.launch {
                    val roles = listOf("Admin", "CEO", "Systems, IT", "HR")
                    Utility.notifyAdmins("HR budget declined.", "Human Resource", roles)
                }
                // Successfully updated 'budgetStatus'
                Log.d("Firestore", "Budget status updated to Approved")
            }
            .addOnFailureListener { e ->
                if (!isAdded || _binding == null) return@addOnFailureListener
                binding.hrProgressBar2.visibility = View.GONE
                Toast.makeText(context, "Error declining budget", Toast.LENGTH_SHORT).show()
                // Failed to update 'budgetStatus'
                Log.w("Firestore", "Error updating budget status", e)
            }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}