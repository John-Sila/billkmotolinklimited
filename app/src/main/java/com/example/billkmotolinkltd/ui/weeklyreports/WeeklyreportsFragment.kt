package com.example.billkmotolinkltd.ui.weeklyreports

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentWeeklyreportsBinding
import com.example.billkmotolinkltd.ui.DevDayData
import com.example.billkmotolinkltd.ui.DevUser
import com.example.billkmotolinkltd.ui.DevWeek
import com.example.billkmotolinkltd.ui.DevWeeksAdapter
import com.google.firebase.firestore.FirebaseFirestore
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

        adapter = DevWeeksAdapter(emptyList())
        binding.weeklyReportsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@WeeklyreportsFragment.adapter
        }
        fetchWeeksData()
    }

    private fun fetchWeeksData() {
        val db = FirebaseFirestore.getInstance()
        db.collection("deviations").get()
            .addOnSuccessListener { weeksSnapshot ->
                val weekList = mutableListOf<DevWeek>()

                for (weekDoc in weeksSnapshot.documents) {
                    val weekName = weekDoc.id
                    val usersList = mutableListOf<DevUser>()

                    val usersMap = weekDoc.data ?: continue
                    for ((userName, userRawData) in usersMap) {
                        val userDataMap = userRawData as? Map<String, Map<String, Any>> ?: continue
                        val dayDataList = userDataMap.map { (dayName, fields) ->
                            val dayData = DevDayData(
                                dayName = dayName,
                                netIncome = (fields["netIncome"] as? Number)?.toDouble() ?: 0.0,
                                grossIncome = (fields["grossIncome"] as? Number)?.toDouble() ?: 0.0,
                                netDeviation = (fields["netDeviation"] as? Number)?.toDouble() ?: 0.0,
                                grossDeviation = (fields["grossDeviation"] as? Number)?.toDouble() ?: 0.0,
                                netGrossDifference = (fields["netGrossDifference"] as? Number)?.toDouble() ?: 0.0
                            )

                            if (!isAdded || _binding == null) return@addOnSuccessListener
                            binding.wRProgressBar.visibility = View.GONE

                            Log.d("DevDayData", "User: $userName, Day: $dayName, Data: $dayData")

                            dayData
                        }
                        usersList.add(DevUser(userName, dayDataList))
                    }

                    // Extract start date from the week name and create DevWeek object
                    val startDate = extractStartDateFromWeekName(weekName)
                    weekList.add(DevWeek(weekName, usersList, startDate))
                }

                // Sort the weeks by start date in descending order
                weekList.sortByDescending { it.startDate }

                // Submit the sorted list to the adapter
                adapter.submitList(weekList)
            }
            .addOnFailureListener {
                Log.e("Firestore", "Error loading data", it)
            }
    }

    // Helper function to extract the start date from the week name
    fun extractStartDateFromWeekName(weekName: String): Date? {
        // Use regex to extract the start date (e.g., 07 Apr 2025)
        val regex = """\((\d{2} \w{3} \d{4}) to""".toRegex()
        val matchResult = regex.find(weekName)
        val dateString = matchResult?.groups?.get(1)?.value  // Extract the date part

        return if (dateString != null) {
            try {
                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                dateFormat.parse(dateString)
            } catch (e: Exception) {
                Log.e("Firestore", "Error parsing start date", e)
                null
            }
        } else {
            null
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
