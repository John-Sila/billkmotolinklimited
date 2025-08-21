package com.example.billkmotolinkltd.ui

import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R
import java.util.Calendar
import androidx.core.graphics.toColorInt
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.example.billkmotolinkltd.MainActivity
import com.example.billkmotolinkltd.ui.approvals.ApprovalsFragment
import com.google.firebase.firestore.FirebaseFirestore

// this is for approvals ONLY
class UsersAdapter(private val fragment: ApprovalsFragment) : ListAdapter<User, UsersAdapter.UserViewHolder>(UserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view, fragment)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = getItem(position)
        holder.bind(user)
    }

    class UserViewHolder(itemView: View, private val fragment: ApprovalsFragment) : RecyclerView.ViewHolder(itemView) {
        private val textViewName: TextView = itemView.findViewById(R.id.textViewName)
        private val textViewActiveStatus: TextView = itemView.findViewById(R.id.textViewActiveStatus)
        private val textViewSundayText: TextView = itemView.findViewById(R.id.textViewSundayText)
        private val amountPendingApproval: TextView = itemView.findViewById(R.id.amountPendingApproval)
        private val textViewDailyTarget: TextView = itemView.findViewById(R.id.textViewDailyTarget)
        private val textViewCurrentInAppBal: TextView = itemView.findViewById(R.id.textViewCurrentInAppBal)
        private val textViewSundayTarget: TextView = itemView.findViewById(R.id.textViewSundayTarget)

        private val btnApprovePartial: Button = itemView.findViewById(R.id.btnApprovePartial)
        private val btnApproveAllIncome: Button = itemView.findViewById(R.id.btnApproveAllIncome)
        private val btnChangeDailyTarget: Button = itemView.findViewById(R.id.btnChangeDailyTarget)
        private val btnChangeSundayTarget: Button = itemView.findViewById(R.id.btnChangeSundayTarget)
        private val btnChangeInAppBalance: Button = itemView.findViewById(R.id.btnChangeInAppBalance)
        private val btnChangeSundayWorkingStatus: Button = itemView.findViewById(R.id.btnChangeSundayWorkingStatus)

        private val inputPartialApproval: EditText = itemView.findViewById(R.id.inputPartialApproval)
        private val inputDailyTarget: EditText = itemView.findViewById(R.id.inputDailyTarget)
        private val inputSundayTarget: EditText = itemView.findViewById(R.id.inputSundayTarget)
        private val inputInAppBal: EditText = itemView.findViewById(R.id.inputInAppBal)

        private val pApprovalProgressBar: ProgressBar = itemView.findViewById(R.id.pApprovalProgressBar)
        private val approvalProgressBar: ProgressBar = itemView.findViewById(R.id.approvalProgressBar)
        private val dTargetProgressBar: ProgressBar = itemView.findViewById(R.id.dTargetProgressBar)
        private val sTargetProgressBar: ProgressBar = itemView.findViewById(R.id.sTargetProgressBar)
        private val iABalProgressBar: ProgressBar = itemView.findViewById(R.id.iABalProgressBar)
        private val sWStatusProgressBar: ProgressBar = itemView.findViewById(R.id.sWStatusProgressBar)

        private val pALine: LinearLayout = itemView.findViewById(R.id.pALine)
        private val dTLine: LinearLayout = itemView.findViewById(R.id.dTLine)
        private val cIALine: LinearLayout = itemView.findViewById(R.id.cIALine)
        private val sTLine: LinearLayout = itemView.findViewById(R.id.sTLine)

        fun bind(user: User) {
             val calendar = Calendar.getInstance()
             val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
             val daysUntilSunday = (Calendar.SUNDAY - currentDayOfWeek + 7) % 7
             calendar.add(Calendar.DAY_OF_YEAR, daysUntilSunday)
             val dayDescription = when (daysUntilSunday) {
                 0 -> "today"
                 1 -> "tomorrow"
                 else -> "this sunday"
             }

             var sundayText = ""
             sundayText = if (user.isWorkingOnSunday) {
                 "User is set to work $dayDescription"
             } else {
                 "User will not be working $dayDescription"
             }

            // if the user is an HR, don't display much
            if (user.userRank == "HR") {
                pALine.visibility = View.GONE
                dTLine.visibility = View.GONE
                cIALine.visibility = View.GONE
                sTLine.visibility = View.GONE

                btnApprovePartial.visibility = View.GONE
                btnApproveAllIncome.visibility = View.GONE
                btnChangeDailyTarget.visibility = View.GONE
                btnChangeSundayTarget.visibility = View.GONE
                btnChangeInAppBalance.visibility = View.GONE
                btnChangeSundayWorkingStatus.visibility = View.GONE

                sundayText = "This user is bound to work indefinitely."
            }

             fun formatIncome(amount: Double): String {
                 val symbols = DecimalFormatSymbols(Locale("en", "KE")).apply {
                     currencySymbol = "Ksh. "
                 }
                 val formatter = DecimalFormat("¤#,##0.00", symbols)
                 return formatter.format(amount)
             }
             var activeText = ""
             activeText = if (user.isActive == true) { "Active" } else "Inactive"

            textViewName.text = user.userName
            textViewActiveStatus.text = activeText
            textViewSundayText.text = sundayText
            amountPendingApproval.text = formatIncome(user.pendingAmount)
            textViewDailyTarget.text = formatIncome(user.dailyTarget)
            textViewCurrentInAppBal.text = formatIncome(user.currentInAppBalance)
            textViewSundayTarget.text = formatIncome(user.sundayTarget)

            btnApprovePartial.setOnClickListener {
                togglePartialVisibility(inputPartialApproval, pApprovalProgressBar, user.email, itemView.context)
            }
            btnApproveAllIncome.setOnClickListener {
                approveAllIncome(user.email, approvalProgressBar, itemView.context)
            }
            btnChangeDailyTarget.setOnClickListener {
                toggleDailyVisibility(inputDailyTarget, user.email, dTargetProgressBar, itemView.context)
            }
            btnChangeSundayTarget.setOnClickListener {
                toggleSundayVisibility(inputSundayTarget, user.email, sTargetProgressBar, itemView.context)
            }
            btnChangeInAppBalance.setOnClickListener {
                toggleInAppVisibility(inputInAppBal, user.email,  iABalProgressBar, itemView.context)
            }
            btnChangeSundayWorkingStatus.setOnClickListener {
                changeSundayStatus(user.email, user.isWorkingOnSunday ,sWStatusProgressBar, itemView.context)
            }
        }

        // partial approval
        fun togglePartialVisibility(view: View, progressbar: View, email: String, context: Context) {
            if (view.isVisible) {
                view.animate().alpha(0f).withEndAction {
                    view.visibility = View.GONE

                    val amount = inputPartialApproval.text.toString().toDoubleOrNull()
                    if (amount == null || amount <= 0.0) {
                        Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                        return@withEndAction
                    }

                    val alertDialog = androidx.appcompat.app.AlertDialog.Builder(itemView.context)

                    // Custom title with red color
                    val title = SpannableString("Partial Approval")
                    title.setSpan(ForegroundColorSpan(Color.GREEN), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    // Custom message with black color
                    val message = SpannableString("Confirm approval of a partial amount.")
                    message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    alertDialog.setTitle(title)
                    alertDialog.setMessage(message)
                    alertDialog.setIcon(R.drawable.warn)

                    alertDialog.setPositiveButton("Approve") { _, _ ->
                        progressbar.visibility = View.VISIBLE
                        btnApprovePartial.visibility = View.GONE
                        val db = FirebaseFirestore.getInstance()
                        val usersRef = db.collection("users")
                        val generalRef = db.collection("general").document("general_variables")

                        usersRef
                            .whereEqualTo("email", email)
                            .limit(1)
                            .get()
                            .addOnSuccessListener { querySnapshot ->
                                if (querySnapshot.isEmpty) {
                                    Toast.makeText(context, "User not found", Toast.LENGTH_SHORT).show()
                                    inputPartialApproval.text.clear()
                                    progressbar.visibility = View.GONE
                                    btnApprovePartial.visibility = View.VISIBLE
                                    return@addOnSuccessListener
                                }

                                val userDoc = querySnapshot.documents[0]
                                val userRef = userDoc.reference

                                db.runTransaction { transaction ->
                                    // 1. Read user doc
                                    val userSnapshot = transaction.get(userRef)
                                    val currentPending = userSnapshot.getDouble("pendingAmount") ?: 0.0
                                    val usernameToApprove = userSnapshot.getString("userName") ?: "an unidentified rider"

                                    if (amount > currentPending) {
                                        inputPartialApproval.text.clear()
                                        throw IllegalArgumentException("Amount exceeds pending income")
                                    }

                                    // 2. Read general_variables
                                    val generalSnapshot = transaction.get(generalRef)
                                    val currentIncome = generalSnapshot.getDouble("companyIncome") ?: 0.0

                                    // 3. Write both
                                    transaction.update(userRef, "pendingAmount", currentPending - amount)
                                    transaction.update(generalRef, "companyIncome", currentIncome + amount)

                                    PartialTransactionResult(
                                        currentPending /*for this user*/,
                                        amount,
                                        usernameToApprove,
                                        currentIncome, /*in the company*/
                                        "Some Info" /*dummy. Remove if it causes clamped cache*/)
                                }
                                    .addOnSuccessListener { result ->
                                    progressbar.visibility = View.GONE
                                    btnApprovePartial.visibility = View.VISIBLE
                                    fragment.fetchUsersData()
                                    Toast.makeText(context, "Approved Ksh. $amount for $email", Toast.LENGTH_SHORT).show()

                                    ///check
                                    val message = "Approved ${formatIncome(result.pApprovedAmount)} (Partial) for ${result.pUsername}.\n" +
                                            "Company Balance: ${formatIncome(result.pCurrentIncome + result.pApprovedAmount)}\n" +
                                            "${result.pUsername.substringBefore(" ")}'s pending amount: ${formatIncome(result.pCurrentPending - result.pApprovedAmount)}."
                                    Utility.postCashFlow(message, true)
                                }
                                    .addOnFailureListener { e ->
                                    Toast.makeText(context, "Approval failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    Log.e("PartialApproval", "Transaction failed", e)
                                    progressbar.visibility = View.GONE
                                    btnApprovePartial.visibility = View.VISIBLE

                                    inputPartialApproval.text.clear()
                                }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                progressbar.visibility = View.GONE
                                btnApprovePartial.visibility = View.VISIBLE

                                inputPartialApproval.text.clear()
                            }


                    }

                    alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss() // Dismiss dialog if user cancels
                        inputPartialApproval.text.clear()
                    }

                    val dialog = alertDialog.create()
                    dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

                    dialog.show()


                }.duration = 100
            } else {
                view.alpha = 0f
                view.visibility = View.VISIBLE
                view.animate().alpha(1f).duration = 500
            }
        }

        // change of daily target
        fun toggleDailyVisibility(view: View, email: String, progressbar: View, context: Context) {
            if (view.isVisible) {
                view.animate().alpha(0f).withEndAction {
                    view.visibility = View.GONE

                    val amount = inputDailyTarget.text.toString().toDoubleOrNull()
                    if (amount == null || amount <= 0.0) {
                        Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                        return@withEndAction
                    }


                    val alertDialog = androidx.appcompat.app.AlertDialog.Builder(itemView.context)

                    // Custom title with red color
                    val title = SpannableString("Daily Target")
                    title.setSpan(ForegroundColorSpan(Color.GREEN), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    // Custom message with black color
                    val message = SpannableString("Confirm action to change the daily target.")
                    message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    alertDialog.setTitle(title)
                    alertDialog.setMessage(message)
                    alertDialog.setIcon(R.drawable.warn)

                    alertDialog.setPositiveButton("Confirm") { _, _ ->
                        progressbar.visibility = View.VISIBLE
                        btnChangeDailyTarget.visibility = View.GONE
                        val db = FirebaseFirestore.getInstance()
                        val usersRef = db.collection("users")
                        usersRef
                            .whereEqualTo("email", email)
                            .limit(1)
                            .get()
                            .addOnSuccessListener { querySnapshot ->
                                if (querySnapshot.isEmpty) {
                                    Toast.makeText(context, "User not found", Toast.LENGTH_SHORT).show()
                                    inputDailyTarget.text.clear()
                                    progressbar.visibility = View.GONE
                                    btnChangeDailyTarget.visibility = View.VISIBLE
                                    return@addOnSuccessListener
                                }

                                val userDoc = querySnapshot.documents[0]
                                val userRef = userDoc.reference

                                db.runTransaction { transaction ->
                                    // 3. Write both
                                    transaction.update(userRef, "dailyTarget", amount)
                                    null
                                }.addOnSuccessListener {
                                    progressbar.visibility = View.GONE
                                    btnChangeDailyTarget.visibility = View.VISIBLE
                                    Toast.makeText(context, "Changed daily target.", Toast.LENGTH_SHORT).show()
                                    fragment.fetchUsersData()

                                }.addOnFailureListener { e ->
                                    Toast.makeText(context, "In-App change failed", Toast.LENGTH_LONG).show()
                                    Log.e("Target", "Transaction failed", e)
                                    progressbar.visibility = View.GONE
                                    btnChangeDailyTarget.visibility = View.VISIBLE

                                    inputDailyTarget.text.clear()
                                }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                progressbar.visibility = View.GONE
                                btnChangeDailyTarget.visibility = View.VISIBLE

                                inputDailyTarget.text.clear()
                            }


                    }

                    alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss() // Dismiss dialog if user cancels
                        inputDailyTarget.text.clear()
                    }

                    val dialog = alertDialog.create()
                    dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

                    dialog.show()

                }.duration = 100
            }
            else {
                view.alpha = 0f
                view.visibility = View.VISIBLE
                view.animate().alpha(1f).duration = 500
            }
        }

        // change of sunday target
        fun toggleSundayVisibility(view: View, email: String, progressbar: View, context: Context) {
            if (view.isVisible) {
                view.animate().alpha(0f).withEndAction {
                    view.visibility = View.GONE

                    val amount = inputSundayTarget.text.toString().toDoubleOrNull()
                    if (amount == null || amount <= 0.0) {
                        Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                        return@withEndAction
                    }

                    val alertDialog = androidx.appcompat.app.AlertDialog.Builder(itemView.context)

                    // Custom title with red color
                    val title = SpannableString("Sunday Target")
                    title.setSpan(ForegroundColorSpan(Color.GREEN), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    // Custom message with black color
                    val message = SpannableString("Confirm action to change the sunday target.")
                    message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    alertDialog.setTitle(title)
                    alertDialog.setMessage(message)
                    alertDialog.setIcon(R.drawable.warn)

                    alertDialog.setPositiveButton("Confirm") { _, _ ->
                        progressbar.visibility = View.VISIBLE
                        btnChangeSundayTarget.visibility = View.GONE
                        val db = FirebaseFirestore.getInstance()
                        val usersRef = db.collection("users")
                        usersRef
                            .whereEqualTo("email", email)
                            .limit(1)
                            .get()
                            .addOnSuccessListener { querySnapshot ->
                                if (querySnapshot.isEmpty) {
                                    Toast.makeText(context, "User not found", Toast.LENGTH_SHORT).show()
                                    inputSundayTarget.text.clear()
                                    progressbar.visibility = View.GONE
                                    btnChangeSundayTarget.visibility = View.VISIBLE
                                    return@addOnSuccessListener
                                }

                                val userDoc = querySnapshot.documents[0]
                                val userRef = userDoc.reference

                                db.runTransaction { transaction ->
                                    // 3. Write both
                                    transaction.update(userRef, "sundayTarget", amount)
                                    null
                                }.addOnSuccessListener {
                                    progressbar.visibility = View.GONE
                                    btnChangeSundayTarget.visibility = View.VISIBLE
                                    Toast.makeText(context, "Changed Sunday target.", Toast.LENGTH_SHORT).show()
                                    fragment.fetchUsersData()

                                }.addOnFailureListener { e ->
                                    Toast.makeText(context, "Sunday target change failed", Toast.LENGTH_LONG).show()
                                    Log.e("Target", "Transaction failed", e)
                                    progressbar.visibility = View.GONE
                                    btnChangeSundayTarget.visibility = View.VISIBLE
                                    inputSundayTarget.text.clear()
                                }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                progressbar.visibility = View.GONE
                                btnChangeSundayTarget.visibility = View.VISIBLE

                                inputSundayTarget.text.clear()
                            }


                    }

                    alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss() // Dismiss dialog if user cancels
                        inputSundayTarget.text.clear()
                    }

                    val dialog = alertDialog.create()
                    dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

                    dialog.show()

                }.duration = 100
            }
            else {
                view.alpha = 0f
                view.visibility = View.VISIBLE
                view.animate().alpha(1f).duration = 500
            }
        }

        // change of in - app bal
        fun toggleInAppVisibility(view: View, email: String, progressbar: View, context: Context) {
            if (view.isVisible) {
                view.animate().alpha(0f).withEndAction {
                    view.visibility = View.GONE

                    val amount = inputInAppBal.text.toString().toDoubleOrNull()
                    if (amount == null) {
                        Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                        return@withEndAction
                    }


                    val alertDialog = androidx.appcompat.app.AlertDialog.Builder(itemView.context)

                    // Custom title with red color
                    val title = SpannableString("In-App Balance")
                    title.setSpan(ForegroundColorSpan(Color.GREEN), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    // Custom message with black color
                    val message = SpannableString("Confirm action to change In-App balance value.")
                    message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    alertDialog.setTitle(title)
                    alertDialog.setMessage(message)
                    alertDialog.setIcon(R.drawable.warn)

                    alertDialog.setPositiveButton("Confirm") { _, _ ->
                        progressbar.visibility = View.VISIBLE
                        btnChangeInAppBalance.visibility = View.GONE
                        val db = FirebaseFirestore.getInstance()
                        val usersRef = db.collection("users")
                        usersRef
                            .whereEqualTo("email", email)
                            .limit(1)
                            .get()
                            .addOnSuccessListener { querySnapshot ->
                                if (querySnapshot.isEmpty) {
                                    Toast.makeText(context, "User not found", Toast.LENGTH_SHORT).show()
                                    inputInAppBal.text.clear()
                                    progressbar.visibility = View.GONE
                                    btnChangeInAppBalance.visibility = View.VISIBLE
                                    return@addOnSuccessListener
                                }

                                val userDoc = querySnapshot.documents[0]
                                val userRef = userDoc.reference

                                db.runTransaction { transaction ->
                                    // 3. Write both
                                    transaction.update(userRef, "currentInAppBalance", amount)
                                    null
                                }.addOnSuccessListener {
                                    progressbar.visibility = View.GONE
                                    btnChangeInAppBalance.visibility = View.VISIBLE
                                    Toast.makeText(context, "Changed In-App balance", Toast.LENGTH_SHORT).show()
                                    fragment.fetchUsersData()

                                }.addOnFailureListener { e ->
                                    Toast.makeText(context, "In-App change failed", Toast.LENGTH_LONG).show()
                                    Log.e("Target", "Transaction failed", e)
                                    progressbar.visibility = View.GONE
                                    btnChangeInAppBalance.visibility = View.VISIBLE

                                    inputInAppBal.text.clear()
                                }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                progressbar.visibility = View.GONE
                                btnChangeInAppBalance.visibility = View.VISIBLE

                                inputInAppBal.text.clear()
                            }


                    }

                    alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss() // Dismiss dialog if user cancels
                        inputPartialApproval.text.clear()
                    }

                    val dialog = alertDialog.create()
                    dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

                    dialog.show()



                }.duration = 100
            } else {
                view.alpha = 0f
                view.visibility = View.VISIBLE
                view.animate().alpha(1f).duration = 500
            }
        }

        data class TransactionResult(
            val currentPending: Double,
            val username: String,
            val currentIncome: Double,
            val extraInfo: String
        )

        data class PartialTransactionResult(
            val pCurrentPending: Double,
            val pApprovedAmount: Double,
            val pUsername: String,
            val pCurrentIncome: Double,
            val pExtraInfo: String
        )

        // approve all income
        fun approveAllIncome(email: String, progressbar: View, context: Context) {
            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(itemView.context)

            // Custom title with red color
            val title = SpannableString("Complete Approval")
            title.setSpan(ForegroundColorSpan(Color.GREEN), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("Approve all pending income.")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.warn)

            alertDialog.setPositiveButton("Approve") { _, _ ->
                progressbar.visibility = View.VISIBLE
                btnApproveAllIncome.visibility = View.GONE
                val db = FirebaseFirestore.getInstance()
                val usersRef = db.collection("users")
                val generalRef = db.collection("general").document("general_variables")

                usersRef
                    .whereEqualTo("email", email)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        if (querySnapshot.isEmpty) {
                            Toast.makeText(context, "User not found", Toast.LENGTH_SHORT).show()
                            progressbar.visibility = View.GONE
                            btnApproveAllIncome.visibility = View.VISIBLE
                            return@addOnSuccessListener
                        }

                        val userDoc = querySnapshot.documents[0]
                        val userRef = userDoc.reference

                        db.runTransaction { transaction ->
                            // 1. Read user doc
                            val userSnapshot = transaction.get(userRef)
                            val currentPending = userSnapshot.getDouble("pendingAmount") ?: 0.0
                            val usernameToApprove = userSnapshot.getString("userName")

                            // 2. Read general_variables
                            val generalSnapshot = transaction.get(generalRef)
                            val currentIncome = generalSnapshot.getDouble("companyIncome") ?: 0.0

                            // 3. Write both
                            transaction.update(userRef, "pendingAmount", 0.0)
                            transaction.update(generalRef, "companyIncome", currentIncome + currentPending)

                            // Return values you want to use outside
                            TransactionResult(currentPending /*for this user*/, usernameToApprove ?: "an unidentified rider", currentIncome /*in the company*/, "Some Info" /*dummy. Remove if it causes clamped cache*/)
                        }
                            .addOnSuccessListener { result ->
                            progressbar.visibility = View.GONE
                            btnApproveAllIncome.visibility = View.VISIBLE
                            Toast.makeText(context, "Approved all income for for $email", Toast.LENGTH_SHORT).show()
                            // post cash flow
                            val message = "Approved ${formatIncome(result.currentPending)} (All pending) for ${result.username}.\nCompany Balance: ${formatIncome(result.currentIncome + result.currentPending)}."
                            Utility.postCashFlow(message, true)
                            fragment.fetchUsersData()

                        }.addOnFailureListener { e ->
                            Toast.makeText(context, "Approval failed: ${e.message}", Toast.LENGTH_LONG).show()
                            Log.e("PartialApproval", "Transaction failed", e)
                            progressbar.visibility = View.GONE
                            btnApproveAllIncome.visibility = View.VISIBLE
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        progressbar.visibility = View.GONE
                        btnApproveAllIncome.visibility = View.VISIBLE
                    }


            }

            alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
                inputPartialApproval.text.clear()
            }

            val dialog = alertDialog.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

            dialog.show()

        }

        // their sunday working status
        fun changeSundayStatus(email: String, currentStatus: Boolean, progressbar: View, context: Context) {
            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(itemView.context)

            var text = ""
            val calendar = Calendar.getInstance()
            val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val daysUntilSunday = (Calendar.SUNDAY - currentDayOfWeek + 7) % 7
            calendar.add(Calendar.DAY_OF_YEAR, daysUntilSunday)

            // Determine the description of how far away the next Sunday is
            val dayDescription = when (daysUntilSunday) {
                0 -> "today"
                1 -> "tomorrow"
                else -> "on Sunday"
            }
            text = if (currentStatus) {
                "Set user to be off $dayDescription?"
            } else {
                "Set user to work $dayDescription?"
            }
            // Custom title with red color
            val title = SpannableString("Change Sunday working status")
            title.setSpan(ForegroundColorSpan(Color.GREEN), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString(text)
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.warn)

            alertDialog.setPositiveButton("Change") { _, _ ->
                progressbar.visibility = View.VISIBLE
                btnChangeSundayWorkingStatus.visibility = View.GONE
                val db = FirebaseFirestore.getInstance()
                val usersRef = db.collection("users")

                usersRef
                    .whereEqualTo("email", email)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        if (querySnapshot.isEmpty) {
                            Toast.makeText(context, "User not found", Toast.LENGTH_SHORT).show()
                            progressbar.visibility = View.GONE
                            btnChangeSundayWorkingStatus.visibility = View.VISIBLE
                            return@addOnSuccessListener
                        }

                        val userDoc = querySnapshot.documents[0]
                        val userRef = userDoc.reference

                        db.runTransaction { transaction ->
                            // 3. Write both
                            transaction.update(userRef, "isWorkingOnSunday", !currentStatus)
                            null
                        }.addOnSuccessListener {
                            progressbar.visibility = View.GONE
                            btnChangeSundayWorkingStatus.visibility = View.VISIBLE
                            Toast.makeText(context, "Status updated", Toast.LENGTH_SHORT).show()
                            fragment.fetchUsersData()

                        }.addOnFailureListener { e ->
                            Toast.makeText(context, "Approval failed: ${e.message}", Toast.LENGTH_LONG).show()
                            Log.e("PartialApproval", "Transaction failed", e)
                            progressbar.visibility = View.GONE
                            btnChangeSundayWorkingStatus.visibility = View.VISIBLE
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        progressbar.visibility = View.GONE
                        btnChangeSundayWorkingStatus.visibility = View.VISIBLE
                    }
            }

            alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }

            val dialog = alertDialog.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

            dialog.show()
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            // compare item contents
            return oldItem.email == newItem.email
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            // compare items
            return oldItem == newItem
        }
    }
}

fun formatIncome(amount: Double): String {
    val symbols = DecimalFormatSymbols(Locale("en", "KE")).apply {
        currencySymbol = "Ksh. "
    }
    val formatter = DecimalFormat("¤#,##0.00", symbols)
    return formatter.format(amount)
}
