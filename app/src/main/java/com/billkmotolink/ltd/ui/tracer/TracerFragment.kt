package com.billkmotolink.ltd.ui.tracer

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.billkmotolink.ltd.R
import com.billkmotolink.ltd.databinding.FragmentTracerBinding
import com.billkmotolink.ltd.ui.TraceDay
import com.billkmotolink.ltd.ui.TraceMessage
import com.billkmotolink.ltd.ui.TraceUser
import com.billkmotolink.ltd.ui.TraceWeek
import com.billkmotolink.ltd.ui.TraceWeekAdapter
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.collections.get

class TracerFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private var _binding: FragmentTracerBinding? = null

    private val binding get() = _binding!!
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentTracerBinding.inflate(inflater, container, false)
        return inflater.inflate(R.layout.fragment_tracer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.traceRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        fetchTraceData()
        deleteOldTraces()
    }

    private fun fetchTraceData() {
        val db = FirebaseFirestore.getInstance()

        if (!isAdded || _binding == null) return

        binding.tracerProgressBar.visibility = View.VISIBLE

        db.collection("traces").get()
            .addOnSuccessListener { weeksSnapshot ->
                if (weeksSnapshot.isEmpty) {
                    Log.d("TRACE_DEBUG", "No data in traces")
                    return@addOnSuccessListener
                }

                // Move parsing to background thread
                lifecycleScope.launch(Dispatchers.Default) {
                    val weekList = weeksSnapshot.mapNotNull { weekDoc ->
                        val weekName = weekDoc.id
                        val users = weekDoc.data.mapNotNull { (userName, userData) ->
                            if (userName == "Paul Muuo" || userName == "John Sila") return@mapNotNull null
                            if (userData !is Map<*, *>) return@mapNotNull null

                            val days = userData.mapNotNull { (dayName, dayContent) ->
                                when (dayContent) {
                                    is List<*> -> {
                                        val messages = dayContent.mapNotNull { item ->
                                            if (item is Map<*, *>) TraceMessage(
                                                item["message"] as? String ?: "",
                                                item["timestamp"] as? Timestamp ?: Timestamp.now()
                                            ) else null
                                        }
                                        TraceDay(dayName.toString(), messages)
                                    }

                                    is Map<*, *> -> {
                                        val messages = dayContent.mapNotNull { (_, value) ->
                                            if (value is Map<*, *>) TraceMessage(
                                                value["message"] as? String ?: "",
                                                value["timestamp"] as? Timestamp ?: Timestamp.now()
                                            ) else null
                                        }
                                        TraceDay(dayName.toString(), messages)
                                    }

                                    else -> null
                                }
                            }

                            val dayOrder = mapOf(
                                "Monday" to 1,
                                "Tuesday" to 2,
                                "Wednesday" to 3,
                                "Thursday" to 4,
                                "Friday" to 5,
                                "Saturday" to 6,
                                "Sunday" to 7
                            )
                            val sortedDays = days.sortedBy { dayOrder[it.day] ?: Int.MAX_VALUE }

                            TraceUser(userName.toString(), sortedDays)
                        }

                        val startDate = extractStartDateFromWeekName(weekName)
                        TraceWeek(weekName, users, startDate)
                    }.sortedByDescending { it.startDate }

                    // Post results back to main thread
                    withContext(Dispatchers.Main) {
                        updateRecyclerView(weekList)

                        binding.tracerProgressBar.visibility = View.GONE
                        if (weekList.isEmpty()) {
                            if (_binding == null || !isAdded) return@withContext
                            binding.noTraces.visibility = View.VISIBLE

                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error fetching trace data", e)
                binding.tracerProgressBar.visibility = View.GONE
            }
    }

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

    fun updateRecyclerView(weekList: List<TraceWeek>) {
        val recyclerView = view?.findViewById<RecyclerView>(R.id.traceRecyclerView)
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        recyclerView?.adapter = TraceWeekAdapter(weekList)
    }

    fun deleteOldTraces() {
        val db = FirebaseFirestore.getInstance()
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        val calendar = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)  // 1 month ago threshold
        }
        val thresholdDate = calendar.time

        db.collection("traces")
            .get()
            .addOnSuccessListener { documents ->
                val batch = db.batch()
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
                            Log.e("TraceDeletion", "Error parsing date in $weekName", e)
                        }
                    } ?: run {
                        Log.w("TraceDeletion", "Invalid week format: $weekName")
                    }
                }

                if (!isAdded) return@addOnSuccessListener

                if (deleteCount > 0) {
                    batch.commit()
                        .addOnSuccessListener {
                            if (!isAdded) return@addOnSuccessListener
                            Toast.makeText(requireContext(), "Deleted $deleteCount old trace weeks", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            if (!isAdded) return@addOnFailureListener
                            Toast.makeText(requireContext(), "Deletion failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(requireContext(), "No old traces to delete", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(requireContext(), "Error fetching traces: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

}