package com.example.billkmotolinkltd.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class DevDayData(
    val dayName: String,
    val netIncome: Double,
    val grossIncome: Double,
    // val netDeviation: Double,
    val grossDeviation: Double,
    val netGrossDifference: Double
)

data class DevUser(
    val userName: String,
    val days: List<DevDayData>
)

data class DevWeek(
    val weekName: String,
    val users: List<DevUser>,
    val startDate: Date?
)

data class ClockoutGroup(
    val userName: String,
    val entries: List<ClockoutEntry>
)

data class ClockoutEntry(
    val userName: String,
    val date: String,
    val netIncome: Double,
    val grossIncome: Double,
    val inAppBal: Double,
    val inAppDiff: Double,
    val clockinMileage: Double,
    val clockoutMileage: Double,
    val mileageDifference: Double,
    val elapsedTime: String,
    val expenses: Map<String, Double>,
    val postedAt: Timestamp?
)

data class ProfileUser(
    val lastSeenClockInTime: Timestamp,
    val dateCreated: Timestamp,
    val currentBike: String,
    val inAppBalance: Double,
    val dailyTarget: Double,
    val email: String,
    val fcmToken: String,
    val gender: String,
    val bankAcc: String,
    val hrsPerShift: Number,
    val idNumber: String,
    val isActive: Boolean,
    val isClockedIn: Boolean,
    val lastClockDate: Timestamp,
    val netClockedLastly: Double,
    val amountPendingApproval: Double,
    val phoneNumber: String,
    val requirements: Number,
    val sundayTarget: Double,
    val userName: String,
    val userRank: String,
    val uid: String
)

data class Battery(
    val batteryName: String = "",
    val batteryLocation: String = "",
    val assignedBike: String = "",
    val assignedRider: String = "",
    val offTime: Timestamp? = null
)

data class ExecutiveUser(
    val userName: String? = null,
    val email: String? = null,
    val netIncome: Double? = null,
    val pendingAmount: Double? = null,
    val isClockedIn: Boolean? = null,
    val lastClockDate: Timestamp? = null,
    val userRank: String? = null
)

data class BudgetItemRow(
    val itemsFormatted: String,
    val itemCount: Int,
    val budgetStatus: String,
    val totalCost: Double,
    val postedAt: Any
)



data class TraceWeek(
    val weekName: String,
    var users: List<TraceUser> = listOf(),
    val startDate: Date?,
)
data class TraceUser(
    val userName: String,
    var days: List<TraceDay> = listOf()
)
data class TraceDay(
    val day: String = "",
    val messages: List<TraceMessage> = emptyList()
)
data class TraceMessage(
    val message: String = "",
    val timestamp: Timestamp? = null
)

data class Chatroom(
    val id: String = "",
    val name: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 24 * 60 * 60 * 1000, // 24 hours
    val participantCount: Int = 0,
    val creatorUID: String = "",
    val pendingApprovals: List<String> = emptyList(), // UIDs waiting for approval
    val approvedParticipants: List<String> = emptyList() // Approved user UIDs
)
data class UserEntry(val uid: String, val userName: String)


data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",  // ← Add this
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
)



data class CashFlow(
    val id: String,
    val message: String,
    val time: String,
    val isIncremental: Boolean,
    val identity: String
)

object Utility {
    suspend fun postTrace(message: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val firestore = FirebaseFirestore.getInstance()
        val uid = user.uid
        val userRef = firestore.collection("users").document(uid)

        try {
            val document = userRef.get().await()
            val userName = document.getString("userName") ?: "Unknown"
            val userRank = document.getString("userRank") ?: ""
            if (userRank in listOf("Systems, IT", "CEO")) return

            val calendar = Calendar.getInstance()
            calendar.firstDayOfWeek = Calendar.MONDAY
            calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            val startOfWeek = calendar.time
            val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
            val startOfWeekFormatted = dateFormat.format(startOfWeek)
            calendar.add(Calendar.DAY_OF_WEEK, 6)
            val endOfWeekFormatted = dateFormat.format(calendar.time)
            val week = calendar.get(Calendar.WEEK_OF_YEAR)
            val path = "Week $week (${startOfWeekFormatted.replace("-", " ")} to ${endOfWeekFormatted.replace("-", " ")})"

            val traceRef = firestore.collection("traces").document(path)
            val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(Calendar.getInstance().time)
            val traceData = mapOf(
                "message" to message,
                "timestamp" to Timestamp.now()
            )

            val existingDoc = traceRef.get().await()
            val existingData = existingDoc.data?.get(userName) as? Map<String, Any> ?: emptyMap()
            val dayData = existingData[dayOfWeek] as? List<Map<String, Any>> ?: emptyList()
            val updatedDayData = dayData + traceData

            val updatedData = mapOf(
                userName to mapOf(
                    dayOfWeek to updatedDayData
                )
            )

            traceRef.set(updatedData, SetOptions.merge()).await()
            Log.d("Firestore", "Trace posted successfully for $userName -> $dayOfWeek in $path")

        } catch (e: Exception) {
            Log.e("Firestore", "Error in postTrace: ${e.message}", e)
        }

        if (message == "Logged out.") {
            FirebaseAuth.getInstance().signOut()
        }
    }

    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun postCashFlow(messageToPost: String, incrementingState: Boolean) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val firestore = FirebaseFirestore.getInstance()

        // Reference to the cash_flows subcollection
        val cashFlowRef = firestore
            .collection("general")
            .document("general_variables")
            .collection("cash_flows")
            .document() // Auto-generates a unique transaction ID

        val formattedTime = getFormattedCurrentTime() // Format: "Tue, 23rd August 2025 at 19:00:32"

        fun generateRandomId(length: Int): String {
            val allowedChars = ('A'..'Z') + ('0'..'9')
            val randomPart = (1..length)
                .map { allowedChars.random() }
                .joinToString("")
            return "BML-$randomPart"
        }


        val transactionId = generateRandomId(7)

        val newEntry = mapOf(
            "message" to messageToPost,
            "time" to formattedTime,
            "timestamp" to FieldValue.serverTimestamp(),
            "isIncremental" to incrementingState,
            "transactionId" to transactionId
        )

        cashFlowRef.set(newEntry)
            .addOnSuccessListener {
                Log.d("postCashFlow", "Cash flow entry posted successfully.")
            }
            .addOnFailureListener { e ->
                Log.e("postCashFlow", "Failed to post cash flow: ${e.message}")
            }
    }

    fun getFormattedCurrentTime(): String {
        val now = Calendar.getInstance()
        val dayOfWeek = SimpleDateFormat("EEE", Locale.getDefault()).format(now.time)
        val day = now.get(Calendar.DAY_OF_MONTH)
        val daySuffix = when (day) {
            1, 21, 31 -> "st"
            2, 22 -> "nd"
            3, 23 -> "rd"
            else -> "th"
        }
        val monthYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(now.time)
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now.time)

        return "$dayOfWeek, ${day}$daySuffix $monthYear at $time"
    }

    suspend fun notifyAdmins(actionMessage: String, action: String, targetRoles: List<String>) {
        val firestore = FirebaseFirestore.getInstance()
        val notification = mapOf(
            "title" to action,
            "body" to actionMessage,
            "timestamp" to FieldValue.serverTimestamp(),
            "targetRoles" to targetRoles
        )

        try {
            firestore.collection("notifications")
                .document("latest")
                .set(notification)
                .await()
            Log.d("Notify", "Notification posted (overwritten).")
        } catch (e: Exception) {
            Log.e("Notify", "Failed to post notification: ${e.message}", e)
        }
    }
}

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Capture crash details
        val crashReport = StringBuilder().apply {
            append("Crash on: ${Date()}\n")
            append("Thread: ${thread.name}\n")
            append("Message: ${throwable.message}\n")
            append("Stacktrace:\n")
            append(Log.getStackTraceString(throwable))
        }.toString()

        // Optional: Send to Firestore
        sendCrashToFirestore(crashReport)

        // Let default handler still do its job (to show "App Crashed" dialog)
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun sendCrashToFirestore(report: String) {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: "Unknown"
        val userEmail = user?.email ?: "Unknown"

        val crashData = mapOf(
            "uid" to userId,
            "email" to userEmail,
            "timestamp" to Timestamp.now(),
            "report" to report
        )

        FirebaseFirestore.getInstance()
            .collection("crash_reports")
            .add(crashData)
    }
}



