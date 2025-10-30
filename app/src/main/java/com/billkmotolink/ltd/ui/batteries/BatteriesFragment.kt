package com.billkmotolink.ltd.ui.batteries

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.billkmotolink.ltd.R
import com.billkmotolink.ltd.ui.BatteriesAdapter
import com.billkmotolink.ltd.ui.BatteryModel
import com.billkmotolink.ltd.ui.cleanOldTracesSuspend
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class BatteriesFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var rv: RecyclerView
    private lateinit var progress: View
    private lateinit var adapter: BatteriesAdapter
    private var currentUserName: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_batteries, container, false)
        rv = root.findViewById(R.id.batteriesRecyclerView)
        progress = root.findViewById(R.id.batteriesProgressBar)
        rv.layoutManager = LinearLayoutManager(requireContext())

        adapter = BatteriesAdapter(currentUserName, mutableListOf(), ::onShowTracesClicked)
        rv.adapter = adapter

        loadAndShow()

        lifecycleScope.launch {
            try {
                cleanOldTracesSuspend(7)
                Toast.makeText(requireContext(), "Cleanup was successful", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Cleanup failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        return root
    }

    private fun loadAndShow() {
        progress.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                if (user == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "No user logged in", Toast.LENGTH_SHORT).show()
                        progress.visibility = View.GONE
                    }
                    return@launch
                }

                val userDoc = db.collection("users").document(user.uid).get().await()
                currentUserName = userDoc.getString("userName") ?: ""

                val snapshot = db.collection("batteries").get().await()

                val models = snapshot.documents.map { doc ->
                    val id = doc.id
                    val name = doc.getString("batteryName") ?: ""
                    val nameLower = doc.getString("batteryNameLower") ?: ""
                    val loc = doc.getString("batteryLocation") ?: ""
                    val bike = doc.getString("assignedBike") ?: ""
                    val rider = doc.getString("assignedRider") ?: ""
                    val off = doc.getTimestamp("offTime")
                    val tracesMap = (doc.get("traces") as? Map<String, Map<String, Any>>) ?: emptyMap()

                    // Convert Firestore map to list so sorting isn’t affected by internal key order
                    val tracesList = tracesMap.entries.mapNotNull { (key, value) ->
                        val timestamp = value["dateEdited"] as? Timestamp
                        if (timestamp != null) key to (value to timestamp) else null
                    }

                    // Sort by dateEdited DESC and rebuild ordered map
                    val sortedTraces = LinkedHashMap<String, Map<String, Any>>()
                    tracesList.sortedByDescending { it.second.second.seconds }.forEach { (key, pair) ->
                        val value = pair.first
                        sortedTraces[key] = value
                    }

                    // Reverse "entries" inside each trace so latest appears first
                    val normalizedTraces = sortedTraces.mapValues { (_, value) ->
                        val reversedEntries = when (val rawEntries = value["entries"]) {
                            is List<*> -> rawEntries.asReversed()
                            else -> emptyList<Any>()
                        }
                        value.toMutableMap().apply { this["entries"] = reversedEntries }
                    }

                    BatteryModel(
                        id = id,
                        batteryName = name,
                        batteryNameLower = nameLower,
                        batteryLocation = loc,
                        assignedBike = bike,
                        assignedRider = rider,
                        offTime = off,
                        traces = normalizedTraces
                    )
                }

                // Natural numeric sort: extract trailing digits (e.g., "BK-BT-021" → 21)
                val sortedModels = models.sortedBy {
                    val numberPart = Regex("\\d+$").find(it.batteryName)?.value?.toIntOrNull() ?: Int.MAX_VALUE
                    numberPart
                }

                withContext(Dispatchers.Main) {
                    adapter = BatteriesAdapter(
                        currentUserName,
                        sortedModels.toMutableList(),
                        ::onShowTracesClicked
                    )
                    rv.adapter = adapter
                    progress.visibility = View.GONE
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    Toast.makeText(requireContext(), "Failed to load batteries: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun onShowTracesClicked(battery: BatteryModel) {
        val traces = battery.traces
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.battery_trace, null)
        val container = view.findViewById<LinearLayout>(R.id.tracesContainer)

        if (traces.isEmpty()) {
            val emptyText = TextView(requireContext()).apply {
                text = "No trace data available"
                setPadding(12, 12, 12, 12)
            }
            container.addView(emptyText)
        } else {
            // Use the map’s existing insertion order (already sorted by timestamp descending)
            for ((key, traceMap) in traces) {
                val header = TextView(requireContext()).apply {
                    text = key
                    setTypeface(null, Typeface.BOLD)
                    textSize = 16f
                    setPadding(4, 12, 4, 6)
                }
                container.addView(header)

                val entriesObj = traceMap["entries"]
                when (entriesObj) {
                    is List<*> -> {
                        for (entry in entriesObj) {
                            val tv = TextView(requireContext()).apply {
                                text = entry?.toString() ?: ""
                                setPadding(48, 4, 8, 4)
                            }
                            container.addView(tv)
                        }
                    }
                    is String -> {
                        val tv = TextView(requireContext()).apply {
                            text = entriesObj
                            setPadding(8, 4, 8, 4)
                        }
                        container.addView(tv)
                    }
                    is Map<*, *> -> {
                        for ((k, v) in entriesObj) {
                            val tv = TextView(requireContext()).apply {
                                text = "$k: $v"
                                setPadding(8, 4, 8, 4)
                            }
                            container.addView(tv)
                        }
                    }
                }

                val dateEditedObj = traceMap["dateEdited"]
                val dateEditedStr = when (dateEditedObj) {
                    is Timestamp -> {
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            .format(dateEditedObj.toDate())
                    }
                    else -> dateEditedObj?.toString() ?: ""
                }

                if (dateEditedStr.isNotEmpty()) {
                    val dtv = TextView(requireContext()).apply {
                        text = "Last updated: $dateEditedStr"
                        setPadding(48, 6, 8, 8)
                        setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
                    }
                    container.addView(dtv)
                }
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Trace data for ${battery.batteryName}")
            .setView(view)
            .setNegativeButton("Close", null)
            .create()
            .show()
    }

}
