package com.example.billkmotolinkltd.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.billkmotolinkltd.databinding.FragmentHomeBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import android.util.Log
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.ui.BudgetAdapter
import com.example.billkmotolinkltd.ui.BudgetItemRow
import com.example.billkmotolinkltd.ui.ExecutiveUser
import com.example.billkmotolinkltd.ui.ExecutiveUserAdapter
import com.example.billkmotolinkltd.ui.formatIncome
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.provider.CalendarContract
import android.text.Spanned
import android.text.style.TypefaceSpan


import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException
import java.util.Date

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var textViewWeather: TextView // Declare the TextView
    private lateinit var textViewUserName: TextView
    private lateinit var textViewSunday: TextView

    private lateinit var executiveUserAdapter: ExecutiveUserAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        handler.post(fetchRunnable)
        requestLocation()

        recyclerView = binding.recyclerView
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textViewWeather = view.findViewById(R.id.weather) // Initialize it
        textViewUserName = view.findViewById(R.id.userNameTextView)
        textViewSunday = view.findViewById(R.id.sundayStuff)

        // Call getWeather with some default lat/lon values
        getWeather(-1.3238, 36.9000) // begin with embakasi cords
        getUserData()
        askNotificationPermission()
        getMemo()
    }

    /*Deal with memos*/

    private fun getMemo() {
        if (!isAdded || _binding == null) return

        FirebaseFirestore.getInstance()
            .collection("memos")
            .document("latest")
            .get()
            .addOnSuccessListener { document ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                handleDocument(document)
            }
            .addOnFailureListener { e ->
                if (!isAdded || _binding == null) return@addOnFailureListener
                showError("Failed to load memo: ${e.message}")
            }
    }

    private fun handleDocument(document: DocumentSnapshot) {
        if (!document.exists()) {
            binding.memoLayout.visibility = View.GONE
            showError("No memos found.")
            return
        }

        // Extract all data first
        val heading = document.getString("heading").orEmpty()
        val htmlDescription = document.getString("description").orEmpty()
        val postedOnTs = document.getTimestamp("postedOn")
        val memoTimeStamp = document.getTimestamp("timestamp")
        val memoVenue = document.getString("venue").orEmpty()

        // Process posted time
        postedOnTs?.let { processPostedTime(it) }

        // Process memo timestamp (future/past logic)
        memoTimeStamp?.let { processTimestamp(it, heading, memoVenue, htmlDescription) }

        // Handle venue visibility
        updateVenueVisibility(memoVenue)

        // Set memo content
        setMemoContent(heading, htmlDescription)

        binding.memoLayout.visibility = View.VISIBLE
    }

    private fun processPostedTime(timestamp: Timestamp) {
        val calendar = Calendar.getInstance().apply { time = timestamp.toDate() }
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val suffix = getDayWithSuffix(day)
        val monthName = SimpleDateFormat("MMMM", Locale.US).format(calendar.time)
        val timeFormatted = SimpleDateFormat("HH:mm", Locale.US).format(calendar.time)
        val postedOnFormatted = "$suffix $monthName, ${calendar.get(Calendar.YEAR)} at $timeFormatted"

        val spannable = SpannableString(postedOnFormatted)
        spannable.setSpan(ForegroundColorSpan(Color.GRAY), 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(TypefaceSpan("serif"), 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        binding.memoTime.text = spannable
    }

    private fun processTimestamp(timestamp: Timestamp, heading: String, venue: String, description: String) {
        val now = Timestamp.now()
        val millisDiff = timestamp.toDate().time - now.toDate().time

        if (millisDiff < 0) {
            // Past event
            val spannable = SpannableString("~ expired")
            spannable.setSpan(ForegroundColorSpan(Color.RED), 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(TypefaceSpan("serif"), 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            binding.memoTimeTo.text = spannable

            //binding.memoTimeTo.text = HtmlCompat.fromHtml(expiredText, HtmlCompat.FROM_HTML_MODE_LEGACY)
            binding.meetingStillFuture.visibility = View.GONE
            return
        }

        // Future event logic
        val remainingTime = calculateRemainingTime(millisDiff)
        val italicRemaining = "<font color='#00FF00'><i><b>⏳ $remainingTime</b></i></font>"
        binding.memoTimeTo.text = HtmlCompat.fromHtml(italicRemaining, HtmlCompat.FROM_HTML_MODE_LEGACY)

        val calendar = Calendar.getInstance().apply { time = timestamp.toDate() }
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val suffix = getDayWithSuffix(day)
        val monthName = SimpleDateFormat("MMMM", Locale.US).format(calendar.time)
        val timeFormatted = SimpleDateFormat("HH:mm", Locale.US).format(calendar.time)
        val spannable = SpannableString("~ scheduled for $suffix $monthName, ${calendar.get(Calendar.YEAR)} at $timeFormatted")
        spannable.setSpan(ForegroundColorSpan(Color.MAGENTA), 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(TypefaceSpan("serif"), 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        binding.memoTime.text = spannable
        binding.actualMeetTime.text = spannable

        binding.btnAddToCalendar.apply {
            visibility = View.VISIBLE
            setOnClickListener {
                loadCalendarButton(timestamp.toDate().time, heading, venue, description)
            }
        }
    }

    private fun calculateRemainingTime(millisDiff: Long): String {
        val diffSeconds = millisDiff / 1000
        val diffMinutes = diffSeconds / 60
        val diffHours = diffMinutes / 60
        val diffDays = diffHours / 24

        return when {
            diffDays >= 1 -> {
                val hrRemainder = diffHours % 24
                "$diffDays day${if (diffDays > 1) "s" else ""} $hrRemainder hr${if (hrRemainder != 1L) "s" else ""} remaining"
            }
            diffHours >= 1 -> {
                val minRemainder = diffMinutes % 60
                "$diffHours hr${if (diffHours != 1L) "s" else ""} $minRemainder min${if (minRemainder != 1L) "s" else ""} remaining"
            }
            diffMinutes >= 1 -> "$diffMinutes min${if (diffMinutes != 1L) "s" else ""} remaining"
            else -> "Less than a minute remaining"
        }
    }

    private fun updateVenueVisibility(venue: String) {
        binding.apply {
            physicalOptionLayout.visibility = if (venue == "Physical") View.VISIBLE else View.GONE
            meetOptionLayout.visibility = if (venue == "Google Meet") View.VISIBLE else View.GONE
            teamsOptionLayout.visibility = if (venue == "Microsoft Teams") View.VISIBLE else View.GONE
            zoomOptionLayout.visibility = if (venue == "Zoom") View.VISIBLE else View.GONE
            whatsAppOptionLayout.visibility = if (venue == "WhatsApp") View.VISIBLE else View.GONE
        }
    }

    private fun setMemoContent(heading: String, htmlDescription: String) {
        val formattedHeader = "<font color='#FFA500'><b>$heading</b></font>"
        binding.memoHeading.text = HtmlCompat.fromHtml(formattedHeader, HtmlCompat.FROM_HTML_MODE_LEGACY)

        try {
            val safeHtml = htmlDescription.replace(Regex("<script.*?>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            binding.memoDescription.text = HtmlCompat.fromHtml(safeHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
        } catch (e: Exception) {
            Log.e("Memo", "Error parsing HTML", e)
            binding.memoDescription.text = "Memo content could not be loaded."
        }
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    /*Memos end here*/


    private fun loadCalendarButton(timeParam: Long, heading: String, memoVenue: String, htmlDescription: String) {
        val endMillis = timeParam + (60 * 60 * 1000) // 1 hour later

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, heading)
            putExtra(CalendarContract.Events.EVENT_LOCATION, memoVenue)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, timeParam)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            putExtra(
                CalendarContract.Events.DESCRIPTION,
                HtmlCompat.fromHtml(htmlDescription, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
            )
        }

        // Check if any calendar app can handle this intent
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(requireContext(), "No calendar app found.", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("Notifications", "Permission granted")
            } else {
                Log.w("Notifications", "Permission denied")
            }
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun askNotificationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(requireContext() as Activity,
                Manifest.permission.POST_NOTIFICATIONS))
        {
            AlertDialog.Builder(requireContext())
                .setTitle("Notification Permission")
                .setMessage("We use notifications to alert you of important actions.")
                .setPositiveButton("Allow") { _, _ ->
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                .setNegativeButton("Deny", null)
                .show()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

    }

    private fun formatCustomTimestamp(timestamp: Timestamp?, context: Context): SpannableString {
        if (timestamp == null) return SpannableString("N/A")

        val date = timestamp.toDate()

        val calendar = Calendar.getInstance().apply { time = date }
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val daySuffix = when {
            day in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }

        val sdf = SimpleDateFormat("MMMM yyyy HH:mm:ss", Locale.getDefault())
        val formattedMain = "$day$daySuffix, ${sdf.format(date)}"

        // Remove time to compare only calendar dates
        fun stripTime(cal: Calendar): Calendar {
            return Calendar.getInstance().apply {
                time = cal.time
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }

        val today = stripTime(Calendar.getInstance())
        val reportDate = stripTime(Calendar.getInstance().apply { time = date })

        val daysDiff = ((today.timeInMillis - reportDate.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()

        val bracketText = when (daysDiff) {
            0 -> "(Today)"
            1 -> "(Yesterday)"
            else -> "($daysDiff days ago)"
        }

        val fullText = "$formattedMain $bracketText"

        val spannable = SpannableString(fullText)
        val start = fullText.indexOf(bracketText)
        val end = start + bracketText.length
        spannable.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, android.R.color.holo_red_light)),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        return spannable
    }

    private fun getLast12MonthNames(): List<String> {
        val calendar = Calendar.getInstance()
        val months = mutableListOf<String>()
        val sdf = SimpleDateFormat("MMMM", Locale.getDefault())

        repeat(11) { // current month + last 10 months
            months.add(sdf.format(calendar.time))
            calendar.add(Calendar.MONTH, -1)
        }
        return months
    }








    /*Getting this user's data*/

    private fun getUserData() {
        if (!isAdded || _binding == null) return

        binding.homeProgressBar.visibility = View.VISIBLE
        val currentUser = FirebaseAuth.getInstance().currentUser ?: run {
            showToast("No user is logged in.")
            return
        }

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                if (!document.exists()) return@addOnSuccessListener

                handleUserDocument(document)
            }
            .addOnFailureListener { e ->
                Log.e("UserInfo", "Error fetching user data", e)
                binding.homeProgressBar.visibility = View.GONE
            }
    }

    private fun handleUserDocument(document: DocumentSnapshot) {
        // Extract all data at once
        val userData = UserData(
            username = document.getString("userName").orEmpty(),
            pendingAmount = document.getDouble("pendingAmount") ?: 0.0,
            targetAmount = document.getDouble("dailyTarget") ?: 0.0,
            sundayTarget = document.getDouble("sundayTarget") ?: 0.0,
            userRank = document.getString("userRank") ?: "Rider",
            isWorkingOnSunday = document.getBoolean("isWorkingOnSunday") == true,
            assignedBike = document.getString("currentBike") ?: "None",
            netIncomesMap = document.get("netIncomes") as? Map<*, *>,
            requirements = document.get("requirements") as? Map<*, *>,
            lastClockDate = document.getTimestamp("lastClockDate"),
            netClockedLastly = document.getDouble("netClockedLastly") ?: 0.0,
            clockInTime = document.getTimestamp("clockInTime"),
            isActive = document.getBoolean("isActive") ?: true
        )

        // Check account status first
        if (!userData.isActive) {
            handleInactiveAccount()
            return
        }

        // Update UI based on user data
        updateUserProfileUI(userData)
        handleRequirements(userData.requirements)
        handleRankSpecificViews(userData)
        handleClockData(userData)
        handleBikeAssignment(userData)

        binding.homeProgressBar.visibility = View.GONE
    }

    private data class UserData(
        val username: String,
        val pendingAmount: Double,
        val targetAmount: Double,
        val sundayTarget: Double,
        val userRank: String,
        val isWorkingOnSunday: Boolean,
        val assignedBike: String,
        val netIncomesMap: Map<*, *>?,
        val requirements: Map<*, *>?,
        val lastClockDate: Timestamp?,
        val netClockedLastly: Double,
        val clockInTime: Timestamp?,
        val isActive: Boolean
    )

    private fun handleInactiveAccount() {
        FirebaseAuth.getInstance().signOut()
        showToast("You were logged out because this account has been flagged.")
    }

    private fun updateUserProfileUI(data: UserData) {
        val firstName = data.username.substringBefore(" ")
        binding.userNameTextView.apply {
            visibility = View.VISIBLE
            text = "Hello $firstName,"
        }
        binding.helloText.visibility = View.GONE

        updateAmountViews(data.pendingAmount, data.targetAmount)
        updateSundayStatus(data.isWorkingOnSunday, data.sundayTarget)
    }

    private fun updateAmountViews(pendingAmount: Double, targetAmount: Double) {
        binding.pendingAmountTextView.apply {
            text = formatCurrency(pendingAmount, "Ksh. ")
            setTextColor(ContextCompat.getColor(
                requireContext(),
                if (pendingAmount > 3000) android.R.color.holo_red_dark
                else android.R.color.holo_green_dark
            ))
        }

        binding.expectedTargetTextView?.apply {
            text = formatCurrency(targetAmount, "Ksh. ")
            setTextColor(ContextCompat.getColor(
                requireContext(),
                android.R.color.holo_orange_dark
            ))
        }
    }

    private fun updateSundayStatus(isWorking: Boolean, target: Double) {
        binding.sundayStuff.visibility = View.VISIBLE

        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val daysUntilSunday = (Calendar.SUNDAY - currentDayOfWeek + 7) % 7

        val (text, color) = if (isWorking) {
            calendar.add(Calendar.DAY_OF_YEAR, daysUntilSunday)
            val formattedTarget = formatCurrency(target, "Ksh. ")
            val dayDescription = when (daysUntilSunday) {
                0 -> "Today"
                1 -> "Tomorrow"
                else -> "In $daysUntilSunday days"
            }

            "You will be working this Sunday ($dayDescription) date " +
                    "${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH) + 1}/" +
                    "${calendar.get(Calendar.YEAR)} with an expected target of $formattedTarget" to
                    android.R.color.holo_green_dark
        } else {
            val dayDescription = when (daysUntilSunday) {
                0 -> "today"
                1 -> "tomorrow"
                else -> "on Sunday"
            }
            "You are not set to work $dayDescription" to
                    android.R.color.holo_orange_dark
        }

        binding.sundayStuff.apply {
            this.text = getString(R.string.custom_string, text)
            setTextColor(ContextCompat.getColor(requireContext(), color))
        }
    }

    private fun handleRequirements(requirements: Map<*, *>?) {
        val requirementCount = requirements?.size ?: 0
        if (requirementCount > 0) {
            binding.pendingRequirements.text = requirementCount.toString()
            binding.requirementsDiv.visibility = View.VISIBLE
        }
    }

    private fun handleRankSpecificViews(data: UserData) {
        // Reset all views first
        binding.apply {
            adminsAndRiders.visibility = View.GONE
            humanResource.visibility = View.GONE
            chiefExecutive.visibility = View.GONE
        }

        when (data.userRank) {
            "Admin", "Rider" -> {
                binding.adminsAndRiders.visibility = View.VISIBLE
                setupPieChart(data.netIncomesMap)

                if (data.userRank == "Admin") {
                    binding.chiefExecutive.visibility = View.VISIBLE
                    getExecutiveInformation()
                }
            }
            "CEO" -> {
                binding.chiefExecutive.visibility = View.VISIBLE
                getExecutiveInformation()
            }
            "HR" -> {
                binding.humanResource.visibility = View.VISIBLE
                getHumanResource()
            }
            "Systems, IT" -> {
                binding.apply {
                    chiefExecutive.visibility = View.VISIBLE
                    adminsAndRiders.visibility = View.VISIBLE
                    humanResource.visibility = View.VISIBLE
                }
                getExecutiveInformation()
                getHumanResource()
                setupPieChart(data.netIncomesMap)
            }
        }
    }

    private fun handleClockData(data: UserData) {
        if (data.netIncomesMap == null) {
            binding.noClockouts.visibility = View.VISIBLE
            binding.clockoutsLayout.visibility = View.GONE
        } else {
            binding.noClockouts.visibility = View.GONE
            binding.clockoutsLayout.visibility = View.VISIBLE
            binding.lastClockDate.text = formatCustomTimestamp(data.lastClockDate, requireContext())
            binding.lastClockAmount.text = formatIncome(data.netClockedLastly)
        }
    }

    private fun handleBikeAssignment(data: UserData) {
        if (data.assignedBike == "None") {
            binding.onlineHrsTextView.text = "You are not assigned to any bike"
            binding.onlineHrsText.visibility = View.GONE
            binding.bikeLayout.visibility = View.GONE
        } else {
            binding.apply {
                onlineHrsText.visibility = View.VISIBLE
                bikeLayout.visibility = View.VISIBLE
                assignedBikeTextView.text = data.assignedBike

                data.clockInTime?.let {
                    val now = Timestamp.now()
                    val diffMillis = now.toDate().time - it.toDate().time
                    val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis) % 60
                    onlineHrsTextView.text = "${hours}h ${minutes}m"
                }
            }
        }
        binding.onlineImage.visibility = View.VISIBLE
    }

    private fun setupPieChart(netIncomesMap: Map<*, *>?) {
        val pieEntries = if (netIncomesMap != null) {
            getLast12MonthNames().mapNotNull { month ->
                (netIncomesMap[month] as? Number)?.toFloat()?.let { PieEntry(it, month) }
            }
        } else {
            showToast("Data for net incomes is unavailable.")
            emptyList()
        }

        val dataSet = PieDataSet(pieEntries, "")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.valueTextSize = 14f
        val pieChart = binding.pieChart

        val pieData = PieData(dataSet)
        pieChart.data = pieData
        pieChart.description.isEnabled = false
        pieChart.centerText = "Last 11 Months"
        pieChart.animateY(1000)


        // Customize legend
        val legend = pieChart.legend
        legend.isEnabled = true
        legend.textColor = ContextCompat.getColor(pieChart.context, R.color.gray)
        legend.formSize = 14f
        legend.form = Legend.LegendForm.CIRCLE

        // Disable description
        pieChart.description.isEnabled = false
        val valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return formatIncome(value.toDouble()) // Show only the amount (Ksh)
            }
        }

        dataSet.valueFormatter = valueFormatter
        pieChart.setDrawSliceText(false)
        pieChart.animateXY(10, 10)
        pieChart.isDragDecelerationEnabled = true
        pieChart.setDragDecelerationFrictionCoef(.9F)
        pieChart.notifyDataSetChanged()

        pieChart.invalidate()
    }

    /*User data ends here*/




    private fun formatCurrency(amount: Double, prefix: String = ""): String {
        return prefix + NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(amount)
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }



    /*Getting information for Admins and CEOs*/

    private fun getExecutiveInformation() {
        FirebaseFirestore.getInstance().collection("users")
            .get()
            .addOnSuccessListener { querySnapshot ->
                handleExecutiveUsers(querySnapshot.documents)
            }
            .addOnFailureListener { e ->
                showExecutiveError(e)
            }
    }

    private fun handleExecutiveUsers(documents: List<DocumentSnapshot>) {
        val users = documents.mapNotNull { document ->
            parseExecutiveUser(document)?.takeIf { isValidExecutiveUser(it) }
        }

        if (users.isEmpty()) {
            showNoUsers()
        } else {
            displayExecutiveUsers(users)
        }
    }

    private fun parseExecutiveUser(document: DocumentSnapshot): ExecutiveUser? {
        val userRank = document.getString("userRank") ?: return null
        if (userRank == "CEO") return null // Skip CEOs

        val isDeleted = document.getBoolean("isDeleted") == true
        val isActive = document.getBoolean("isActive") != false // Default to true if null

        // Skip deleted or inactive users
        if (isDeleted || !isActive) return null

        return ExecutiveUser(
            userName = document.getString("userName"),
            email = document.getString("email"),
            netIncome = document.getDouble("netClockedLastly"),
            pendingAmount = document.getDouble("pendingAmount"),
            isClockedIn = document.getBoolean("isClockedIn"),
            lastClockDate = document.getTimestamp("lastClockDate"),
            userRank = userRank
        )
    }

    private fun isValidExecutiveUser(user: ExecutiveUser): Boolean {
        // Add any additional validation logic here
        return true
    }

    private fun displayExecutiveUsers(users: List<ExecutiveUser>) {
        updateRecyclerView(users)
        binding.noUsers.visibility = View.GONE
    }

    private fun showNoUsers() {
        binding.noUsers.visibility = View.VISIBLE
    }

    private fun showExecutiveError(e: Exception) {
        Toast.makeText(
            context,
            "Error getting users: ${e.message ?: "Unknown error"}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updateRecyclerView(users: List<ExecutiveUser>) {
        executiveUserAdapter = ExecutiveUserAdapter(users)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = executiveUserAdapter
    }

    /*Executive information ends here*/





    // Human Resource starts here

    private fun getHumanResource() {
        setupRecyclerView()
        fetchBudgetData()
    }

    private fun setupRecyclerView() {
        binding.budgetRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
        }
    }

    private fun fetchBudgetData() {
        FirebaseFirestore.getInstance()
            .collection("general")
            .document("general_variables")
            .get()
            .addOnSuccessListener { document ->
                handleBudgetDocument(document)
            }
            .addOnFailureListener { e ->
                handleBudgetError(e)
            }
    }

    private fun handleBudgetDocument(document: DocumentSnapshot) {
        val budget = document.get("budget") as? Map<*, *> ?: run {
            showNoBudget()
            return
        }

        val items = budget["items"] as? List<Map<String, Any>> ?: run {
            showNoBudget()
            return
        }

        displayBudgetData(budget, items)
    }

    private fun displayBudgetData(budget: Map<*, *>, items: List<Map<String, Any>>) {
        binding.apply {
            budgetExists.visibility = View.VISIBLE
            noBudgets.visibility = View.GONE
        }

        // Parse budget header
        val budgetHeading = budget["budgetHeading"]?.toString() ?: "No Heading"
        binding.budgetHeadingHrHome.text = budgetHeading

        // Format posted date
        val postedAt = formatPostedDate(budget["postedAt"] as? Timestamp)

        // Calculate total cost
        val totalCost = (budget["budgetCost"] as? Number)?.toDouble() ?: 0.0

        // Format items
        val itemsFormatted = formatBudgetItems(items)

        // Create and display budget row
        val row = BudgetItemRow(
            itemsFormatted = itemsFormatted,
            itemCount = items.size,
            budgetStatus = budget["budgetStatus"]?.toString() ?: "N/A",
            totalCost = totalCost,
            postedAt = postedAt
        )

        binding.budgetRecyclerView.adapter = BudgetAdapter(listOf(row))
    }

    private fun formatPostedDate(timestamp: Timestamp?): String {
        val date = timestamp?.toDate() ?: Date()
        val calendar = Calendar.getInstance().apply { time = date }

        return buildString {
            append(getDayWithSuffix(calendar.get(Calendar.DAY_OF_MONTH)))
            append(", ")
            append(SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date))
            append(" at ")
            append(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date))
        }
    }

    private fun formatBudgetItems(items: List<Map<String, Any>>): String {
        return items.joinToString(",\n\n") { item ->
            val name = item["name"]?.toString() ?: "Unnamed"
            val cost = (item["cost"] as? Number)?.toDouble() ?: 0.0
            "$name (${formatIncome(cost)})"
        }
    }

    private fun showNoBudget() {
        binding.apply {
            budgetExists.visibility = View.GONE
            noBudgets.visibility = View.VISIBLE
        }
    }

    private fun handleBudgetError(e: Exception) {
        Log.e("BudgetError", "Failed to load budget", e)
        Toast.makeText(
            requireContext(),
            "Failed to load budget: ${e.message ?: "Unknown error"}",
            Toast.LENGTH_SHORT
        ).show()
        showNoBudget()
    }

    /*HR ends here*/



    /*Weather & locations start here*/
    private fun getWeather(lat: Double, lon: Double) {
        // Use lifecycleScope (Fragment/Activity) or viewModelScope (ViewModel)
        val coroutineScope = lifecycleScope // or viewModelScope

        coroutineScope.launch {
            try {
                val weatherInfo = fetchWeatherData(lat, lon)

                // Update UI only if Fragment is alive
                if (isAdded && _binding != null) {
                    textViewWeather.text = weatherInfo
                }
            } catch (e: Exception) {
                Log.e("WeatherError", "API failed: ${e.message}")
                if (isAdded && _binding != null) {
                    Toast.makeText(
                        requireContext(),
                        "Weather update failed: ${e.message ?: "Unknown error"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private suspend fun fetchWeatherData(lat: Double, lon: Double): String {
        return withContext(Dispatchers.IO) { // Switch to IO thread for network call
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                val json = response.body?.string() ?: throw IOException("Empty API response")
                Log.d("WeatherAPI", "Raw JSON: $json")

                parseWeatherJson(json) // Extract parsing logic
            }
        }
    }

    private fun parseWeatherJson(json: String): String {
        val jsonObject = JSONObject(json)
        val currentWeather = jsonObject.optJSONObject("current_weather")
            ?: throw JSONException("Missing 'current_weather' in response")

        val temperature = currentWeather.getDouble("temperature")
        val windSpeed = currentWeather.getDouble("windspeed")
        val windDirection = getWindDirection(currentWeather.getInt("winddirection"))

        return "It's currently $temperature°C with winds at $windSpeed km/hr due $windDirection."
    }

    private fun getWindDirection(degrees: Int): String {
        return when (degrees) {
            in 0..22, in 338..360 -> "North"
            in 23..67 -> "North East"
            in 68..112 -> "East"
            in 113..157 -> "South East"
            in 158..202 -> "South"
            in 203..247 -> "South West"
            in 248..292 -> "West"
            in 293..337 -> "North West"
            else -> "an unknown direction"
        }
    }

    /*Weather & locations end here*/




    private fun getDayWithSuffix(day: Int): String {
        return when {
            day in 11..13 -> "${day}th"
            day % 10 == 1 -> "${day}st"
            day % 10 == 2 -> "${day}nd"
            day % 10 == 3 -> "${day}rd"
            else -> "${day}th"
        }
    }


    private fun requestLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            getUserLocation()
        }
    }

    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    val handler = Handler(Looper.getMainLooper())
    private val fetchRunnable = object : Runnable {
        override fun run() {
            if (isInternetAvailable(requireContext())) {
                getUserData()
                getUserLocation()
                getMemo()
            } else {
                Toast.makeText(requireContext(), "No internet connection. Couldn't reload contents.", Toast.LENGTH_SHORT).show()
            }
            handler.postDelayed(this, 60000) // Run again in 1 minute
        }
    }

    @SuppressLint("MissingPermission")
    private fun getUserLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) getWeather(location.latitude, location.longitude) else textViewWeather.text = "We couldn't analyze your environment 😓."
        }
    }








    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(fetchRunnable)
        _binding = null
    }
}

