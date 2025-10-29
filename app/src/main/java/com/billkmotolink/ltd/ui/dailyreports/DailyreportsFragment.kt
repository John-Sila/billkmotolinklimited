package com.billkmotolink.ltd.ui.dailyreports

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.billkmotolink.ltd.R
import com.billkmotolink.ltd.databinding.FragmentDailyreportsBinding
import com.billkmotolink.ltd.ui.ClockoutEntry
import com.billkmotolink.ltd.ui.ClockoutGroup
import com.billkmotolink.ltd.ui.ClockoutGroupedAdapter
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class DailyreportsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ClockoutGroupedAdapter
    private lateinit var progressBar: View

    private var _binding: FragmentDailyreportsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDailyreportsBinding.inflate(inflater, container, false)
        inflater.inflate(R.layout.fragment_dailyreports, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.recyclerViewClockouts)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ClockoutGroupedAdapter().apply {
            setHasStableIds(true) // Improves performance for stable items
        }
        recyclerView.adapter = adapter
        progressBar = view.findViewById(R.id.dRProgressBar)

        loadData()
    }

    private fun loadData() {
        val binding = _binding ?: return  // safely capture a local reference
        binding.dRProgressBar.visibility = View.VISIBLE
        binding.noDailyReports.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val rawData = withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .get()
                        .await()
                        .documents
                }

                val processedGroups = processDataInChunks(rawData)

                // Only proceed if fragment is still attached and view is alive
                if (!isAdded || _binding == null) return@launch

                updateUI(processedGroups)

            } catch (e: Exception) {
                Log.e("DailyReports", "Error loading data", e)
                _binding?.noDailyReports?.visibility = View.VISIBLE
            } finally {
                _binding?.dRProgressBar?.visibility = View.GONE
            }
        }
    }


    private suspend fun processDataInChunks(
        rawData: List<DocumentSnapshot>,
        chunkSize: Int = 5
    ): List<ClockoutGroup> = withContext(Dispatchers.Default) {
        val tempResult = mutableListOf<ClockoutGroup>()

        rawData.chunked(chunkSize).forEach { chunk ->
            if (!isActive) return@withContext tempResult

            chunk.mapNotNull { userDoc ->
                try {
                    val userId = userDoc.id  // <-- Firestore doc id as unique userId
                    val userName = userDoc.getString("userName") ?: "Unknown"
                    val clockoutsMap = userDoc.get("clockouts") as? Map<String, Map<String, Any>>
                        ?: return@mapNotNull null

                    val entries = clockoutsMap.mapNotNull { (date, data) ->
                        try {
                            ClockoutEntry(
                                userName = userName,
                                date = date,
                                netIncome = (data["netIncome"] as? Number)?.toDouble() ?: 0.0,
                                grossIncome = (data["grossIncome"] as? Number)?.toDouble() ?: 0.0,
                                todaysInAppBalance = (data["todaysInAppBalance"] as? Number)?.toDouble() ?: 0.0,
                                inAppDifference = (data["inAppDifference"] as? Number)?.toDouble() ?: 0.0,
                                clockinMileage = (data["clockinMileage"] as? Number)?.toDouble() ?: 0.0,
                                clockoutMileage = (data["clockoutMileage"] as? Number)?.toDouble() ?: 0.0,
                                mileageDifference = (data["mileageDifference"] as? Number)?.toDouble() ?: 0.0,
                                expenses = (data["expenses"] as? Map<String, Number>)?.mapValues { it.value.toDouble() } ?: emptyMap(),
                                elapsedTime = data["timeElapsed"] as? String ?: "Unknown",
                                postedAt = data["posted_at"] as? Timestamp
                            )
                        } catch (e: Exception) {
                            Log.e("ClockoutParse", "Failed to parse clockout", e)
                            null
                        }
                    }

                    if (entries.isEmpty()) null else ClockoutGroup(userId, userName, entries)
                } catch (e: Exception) {
                    Log.e("UserDataParse", "Failed to parse user data", e)
                    null
                }
            }.let { tempResult.addAll(it) }
        }

        // Merge by userName so each user only appears once
        tempResult
            .groupBy { it.userId }   // <-- group by unique id
            .map { (userId, groups) ->
                ClockoutGroup(
                    userId = userId,
                    userName = groups.first().userName, // pick the name from one of the entries
                    entries = groups.flatMap { it.entries }
                )
            }

    }

    private fun updateUI(groups: List<ClockoutGroup>) {
        if (groups.isEmpty()) {
            binding.noDailyReports.visibility = View.VISIBLE
            adapter.updateData(emptyList())
        } else {
            binding.noDailyReports.visibility = View.GONE
            adapter.updateData(groups)
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null  // Avoid memory leaks by clearing the binding
    }

}
