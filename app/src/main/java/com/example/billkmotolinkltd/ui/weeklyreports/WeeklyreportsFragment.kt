package com.example.billkmotolinkltd.ui.weeklyreports

import android.icu.util.Calendar
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.billkmotolinkltd.databinding.FragmentWeeklyreportsBinding
import com.example.billkmotolinkltd.ui.DevDayData
import com.example.billkmotolinkltd.ui.DevUser
import com.example.billkmotolinkltd.ui.DevWeek
import com.example.billkmotolinkltd.ui.DevWeeksAdapter
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WeeklyreportsFragment : Fragment() {
    private lateinit var adapter: DevWeeksAdapter
    private var _binding: FragmentWeeklyreportsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWeeklyreportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DevWeeksAdapter()
        binding.weeklyReportsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@WeeklyreportsFragment.adapter
        }

        loadWeekData()
    }

    private fun loadWeekData() {
        if (!isAdded || _binding == null) return

        val binding = _binding!!  // safe because we checked above

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.wRProgressBar.visibility = View.VISIBLE
                binding.noWeeklyReports.visibility = View.GONE

                val weekList = fetchWeeksData()
                updateUI(weekList)
            } catch (e: Exception) {
                Log.e("WeeklyReports", "Error loading data", e)
                binding.noWeeklyReports.visibility = View.VISIBLE
            } finally {
                binding.wRProgressBar.visibility = View.GONE
            }
        }
    }


    private suspend fun fetchWeeksData(): List<DevWeek> {
        return withContext(Dispatchers.IO) {
            try {
                val db = FirebaseFirestore.getInstance()
                val weeksSnapshot = db.collection("deviations").get().await()

                weeksSnapshot.documents.mapNotNull { weekDoc ->
                    try {
                        val weekName = weekDoc.id
                        val usersMap = weekDoc.data ?: return@mapNotNull null

                        val usersList = usersMap.mapNotNull { (userName, userRawData) ->
                            try {
                                val userDataMap = userRawData as? Map<String, Map<String, Any>>
                                    ?: return@mapNotNull null

                                val dayDataList = userDataMap.mapNotNull { (dayName, fields) ->
                                    try {
                                        DevDayData(
                                            dayName = dayName,
                                            netIncome = (fields["netIncome"] as? Number)?.toDouble() ?: 0.0,
                                            grossIncome = (fields["grossIncome"] as? Number)?.toDouble() ?: 0.0,
                                            grossDeviation = (fields["grossDeviation"] as? Number)?.toDouble() ?: 0.0,
                                            netGrossDifference = (fields["netGrossDifference"] as? Number)?.toDouble() ?: 0.0
                                        )
                                    } catch (e: Exception) {
                                        Log.e("DayDataParse", "Error parsing day data", e)
                                        null
                                    }
                                }

                                DevUser(userName, dayDataList)
                            } catch (e: Exception) {
                                Log.e("UserDataParse", "Error parsing user data", e)
                                null
                            }
                        }

                        val startDate = extractStartDateFromWeekName(weekName)
                        DevWeek(weekName, usersList, startDate)
                    } catch (e: Exception) {
                        Log.e("WeekDataParse", "Error parsing week data", e)
                        null
                    }
                }.sortedByDescending { it.startDate }
            } catch (e: Exception) {
                Log.e("Firestore", "Error fetching weeks data", e)
                emptyList()
            }
        }
    }

    private fun updateUI(weekList: List<DevWeek>) {
        if (!isAdded || _binding == null) return

        if (weekList.isEmpty()) {
            binding.noWeeklyReports.visibility = View.VISIBLE
        } else {
            binding.noWeeklyReports.visibility = View.GONE
            adapter.submitList(weekList)
        }
    }
    fun deletePreviousMonths() {
        val db = FirebaseFirestore.getInstance()
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        val calendar = Calendar.getInstance().apply {
            add(Calendar.MONTH, -3)  // 3 months ago threshold
        }
        val thresholdDate = calendar.time

        // Get all trace documents
        db.collection("deviations")
            .get()
            .addOnSuccessListener { documents ->
                val batch = db.batch()  // Use batch for bulk deletion
                var deleteCount = 0

                documents.forEach { document ->
                    val weekName = document.id
                    val regex = """\((\d{2} \w+ \d{4}) to (\d{2} \w+ \d{4})\)""".toRegex()

                    regex.find(weekName)?.let { match ->
                        try {
                            val endDateStr = match.groupValues[2]
                            val endDate = dateFormat.parse(endDateStr)

                            if (endDate != null && endDate.before(thresholdDate)) {
                                batch.delete(document.reference)
                                deleteCount++
                            }
                        } catch (e: ParseException) {
                            Log.e("ClockOutDeletion", "Error parsing date in $weekName", e)
                        }
                    } ?: run {
                        Log.w("ClockOutDeletion", "Invalid week format: $weekName")
                    }
                }

                if (deleteCount > 0) {
                    batch.commit()
                        .addOnSuccessListener {
                            Toast.makeText(
                                requireContext(),
                                "Deleted $deleteCount old clockouts weeks",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                requireContext(),
                                "Deletion failed: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "No old clockouts to delete",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    requireContext(),
                    "Error fetching traces: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }


    // Helper function to extract the start date from the week name
    fun extractStartDateFromWeekName(weekName: String): Date? {
        // Extract "07 Sep 2025" or "07 Sept 2025"
        val regex = """\((\d{2} \w{3,4} \d{4}) to""".toRegex()
        val matchResult = regex.find(weekName)
        var dateString = matchResult?.groups?.get(1)?.value

        if (dateString != null) {
            // Normalize "Sept" -> "Sep"
            dateString = dateString.replace("Sept", "Sep")

            return try {
                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                dateFormat.parse(dateString)
            } catch (e: Exception) {
                Log.e("Firestore", "Error parsing start date", e)
                null
            }
        }
        return null
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
