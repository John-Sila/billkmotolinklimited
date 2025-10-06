package com.example.billkmotolinkltd.ui.require_reclock

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CalendarView
import android.widget.DatePicker
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentRequireReclockBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RequireReclockFragment : Fragment() {
    private var _binding: FragmentRequireReclockBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private var selectedDate: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRequireReclockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadUserNames()

        val calendarView = view.findViewById<DatePicker>(R.id.calendarView)
        val userSpinner = view.findViewById<Spinner>(R.id.userToRequire)
        val submitBtn = view.findViewById<Button>(R.id.btnRequire)
        val autoFetchBtn = view.findViewById<Button>(R.id.btnAutoFetchBal)

        val today = System.currentTimeMillis()
        calendarView.maxDate = today

         // Set initial selected date
        selectedDate = calendarView.maxDate

        // Update selectedDate when a new date is picked
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            calendarView.setOnDateChangedListener { _, year, month, dayOfMonth ->
                val calendar = Calendar.getInstance()
                calendar.set(year, month, dayOfMonth)
                selectedDate = calendar.timeInMillis
            }
        }
        else Toast.makeText(requireContext(), "Your Android is not up to standard.", Toast.LENGTH_SHORT).show()

        autoFetchBtn.setOnClickListener {
            val user = userSpinner.selectedItem.toString()
            autoPick(user, selectedDate)
        }

        submitBtn.setOnClickListener {
            val user = userSpinner.selectedItem.toString()
            val input = binding.inAppBalToRequire.text.toString().trim()
            val isValidNumber = input.matches(Regex("^\\d+(\\.\\d+)?$"))
            var number = 0.0
            if (isValidNumber) {
                number = input.toDouble()
            } else {
                Toast.makeText(requireContext(), "Enter a valid number for balance.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val calendar = Calendar.getInstance().apply {
                timeInMillis = selectedDate
                firstDayOfWeek = Calendar.MONDAY
            }
            val weekOfYear = calendar.get(Calendar.WEEK_OF_YEAR)

            // Get Monday of the week
            val weekStart = calendar.clone() as Calendar
            weekStart.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            // Get Sunday of the week
            val weekEnd = calendar.clone() as Calendar
            weekEnd.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            // Formatters
            val fullDateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val dayOfWeekFormatter = SimpleDateFormat("EEEE", Locale.getDefault())
            // Selected date details
            val selectedDateFormatted = fullDateFormatter.format(Date(selectedDate))
            val selectedDayOfWeek = dayOfWeekFormatter.format(Date(selectedDate))
            val weekRangeText = "Week $weekOfYear (${fullDateFormatter.format(weekStart.time)} to ${fullDateFormatter.format(weekEnd.time)})"

            // Final display
            val result = """
                User: $user
                Date: $selectedDateFormatted
                Day: $selectedDayOfWeek
                $weekRangeText
            """.trimIndent()


            val alertDialog = AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Requirements")
            title.setSpan(ForegroundColorSpan(Color.BLUE), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("Please confirm action.")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.success)

            alertDialog.setPositiveButton("Confirm") { _, _ ->
                postRequirement(user, selectedDateFormatted, selectedDayOfWeek, weekRangeText, number)
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

    }

    private fun loadUserNames() {
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users")

        userRef.get()
            .addOnSuccessListener { documents ->
                if (_binding == null || !isAdded) return@addOnSuccessListener  // Prevent crash

                val userNames = mutableListOf<String>()
                val disabledNames = mutableSetOf<String>()
                val adminNames = mutableSetOf<String>()

                for (document in documents) {
                    val isDeleted = document.getBoolean("isDeleted") == true
                    val userRank = document.getString("userRank") ?: ""
                    val userName = document.getString("userName") ?: continue

                    if (!isDeleted && userRank != "CEO") {
                        userNames.add(userName)

                        if (userRank == "Admin") {
                            adminNames.add(userName)
                        }
                        if (userRank == "Systems, IT" || userRank == "HR") {
                            disabledNames.add(userName)
                        }
                    }
                }
                userNames.sort()

                val adapter = object : ArrayAdapter<String>(
                    requireContext(),
                    android.R.layout.simple_spinner_dropdown_item,
                    userNames
                ) {
                    override fun isEnabled(position: Int): Boolean {
                        return userNames[position] !in disabledNames
                    }

                    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getDropDownView(position, convertView, parent)
                        val textView = view as TextView
                        val name = userNames[position]

                        when (name) {
                            in disabledNames -> textView.setTextColor(Color.GRAY)
                            in adminNames -> textView.setTextColor(Color.GREEN) // Green
                            //else -> textView.setTextColor(Color.BLACK)/**/
                        }

                        return view
                    }
                }

                _binding?.userToRequire?.adapter = adapter

            }
            .addOnFailureListener { exception ->
                if (_binding != null) {
                    Toast.makeText(requireContext(), "Failed to load users: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }

        val cRef = db.collection("general").document("general_variables")
        cRef.get().addOnSuccessListener { companyDoc ->
            val companyStatus = companyDoc.getString("companyState") ?: "Unknown"
            if (!isAdded || view == null || _binding == null) return@addOnSuccessListener

            if (companyStatus == "Paused") {
                binding.btnOperationsPaused.visibility = View.VISIBLE
                binding.btnRequire.visibility = View.GONE
            } else if (companyStatus == "Continuing") {
                binding.btnOperationsPaused.visibility = View.GONE
                binding.btnRequire.visibility = View.VISIBLE
            }
        }
    }

    private fun getPreviousDateFormatted(currentMillis: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentMillis
        calendar.add(Calendar.DAY_OF_YEAR, -1)

        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    private fun autoPick(user: String, selectedDate: Long) {
        if (_binding == null || !isAdded) return

        binding.autoFetchProgressBar.visibility = View.VISIBLE
        binding.btnAutoFetchBal.visibility = View.GONE
        val prevDateString = getPreviousDateFormatted(selectedDate) // e.g., "02 Aug 2025"

        val db = FirebaseFirestore.getInstance()

        // Step 1: Find UID by userName
        db.collection("users")
            .whereEqualTo("userName", user)  // user is your input String
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val userDoc = querySnapshot.documents[0]
                    val clockoutsMap = userDoc.get("clockouts") as? Map<*, *>
                    val prevDayData = clockoutsMap?.get(prevDateString) as? Map<*, *>

                    val inAppBalance = prevDayData?.get("todaysInAppBalance")?.toString() ?: "N/A"
                    binding.inAppBalToRequire.setText(inAppBalance)
                } else {
                    binding.inAppBalToRequire.setText("Null")
                }

                binding.autoFetchProgressBar.visibility = View.GONE
                binding.btnAutoFetchBal.visibility = View.VISIBLE
            }
            .addOnFailureListener {
                binding.inAppBalToRequire.setText("Error")
                binding.autoFetchProgressBar.visibility = View.GONE
                binding.btnAutoFetchBal.visibility = View.VISIBLE
            }

    }

    private fun RequireReclockFragment.postRequirement(
        user: String,
        selectedDateFormatted: String,
        selectedDayOfWeek: String,
        weekRangeText: String,
        bal: Double
    ) {
        if (_binding == null || !isAdded) return

        binding.requirementsProgressBar.visibility = View.VISIBLE
        binding.btnRequire.visibility = View.GONE

        db.collection("users")
            .whereEqualTo("userName", user)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val userDoc = querySnapshot.documents.first()
                    val uid = userDoc.id

                    val requirements = userDoc.get("requirements") as? Map<*, *>
                    val alreadyExists = requirements?.values?.any { entry ->
                        if (entry is Map<*, *>) {
                            entry["date"] == selectedDateFormatted
                        } else false
                    } ?: false

                    if (alreadyExists) {
                        Toast.makeText(requireContext(), "${user.substringBefore(" ")} already has this requirement!", Toast.LENGTH_SHORT).show()
                        binding.requirementsProgressBar.visibility = View.GONE
                        binding.btnRequire.visibility = View.VISIBLE
                        return@addOnSuccessListener
                    }

                    // Prepare data to insert
                    val entryId = System.currentTimeMillis().toString()
                    val requirementEntry = mapOf(
                        "date" to selectedDateFormatted,
                        "weekRange" to weekRangeText,
                        "dayOfWeek" to selectedDayOfWeek,
                        "appBalance" to bal
                    )

                    val updates = mapOf(
                        "requirements.$entryId" to requirementEntry
                    )

                    db.collection("users")
                        .document(uid)
                        .update(updates)
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Requirement added to field!", Toast.LENGTH_SHORT).show()
                            binding.requirementsProgressBar.visibility = View.GONE
                            binding.btnRequire.visibility = View.VISIBLE
                        }
                        .addOnFailureListener {
                            Toast.makeText(requireContext(), "Failed to update requirements field.", Toast.LENGTH_SHORT).show()
                            binding.requirementsProgressBar.visibility = View.GONE
                            binding.btnRequire.visibility = View.VISIBLE
                        }
                } else {
                    Toast.makeText(requireContext(), "User not found.", Toast.LENGTH_SHORT).show()
                    binding.requirementsProgressBar.visibility = View.GONE
                    binding.btnRequire.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error fetching user.", Toast.LENGTH_SHORT).show()
                binding.requirementsProgressBar.visibility = View.GONE
                binding.btnRequire.visibility = View.VISIBLE
            }
    }

}

