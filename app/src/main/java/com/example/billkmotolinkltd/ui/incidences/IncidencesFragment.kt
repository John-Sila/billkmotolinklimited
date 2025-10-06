package com.example.billkmotolinkltd.ui.incidences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentIncidencesBinding
import com.example.billkmotolinkltd.ui.ReportsAdapter
import com.google.firebase.firestore.FirebaseFirestore
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar

class IncidencesFragment : Fragment() {

    private var _binding: FragmentIncidencesBinding? = null
    private lateinit var reportsAdapter: ReportsAdapter

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIncidencesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize the reports adapter with an empty list initially
        reportsAdapter = ReportsAdapter(emptyList())

        // Set up the RecyclerView with the adapter and layout manager
        binding.reportsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.reportsRecyclerView.adapter = reportsAdapter

        // Fetch reports
        lifecycleScope.launch {
            deleteOldReports()
            getReports()
        }

        binding.btnClearReports.setOnClickListener {
            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Confirm Deletion")
            title.setSpan(ForegroundColorSpan(Color.RED), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("Are you sure you want to delete all reports? This action cannot be undone.")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.warn)

            alertDialog.setPositiveButton("Yes") { _, _ ->
                deleteReports() // Proceed with deletion if user confirms
            }

            alertDialog.setNegativeButton("No") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
            }

            alertDialog.setNeutralButton("More Info") { _, _ ->
                Toast.makeText(requireContext(), "Deleting all reports is permanent.", Toast.LENGTH_SHORT).show()
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

    private fun deleteOldReports() {
        // Show loading indicator if needed
        // binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            when (val result = cleanupReports()) {
                is CleanupResult.Success -> {
                    Log.d("ReportsCleanup", result.message)
                    // Show success message if needed
                    // Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                }
                is CleanupResult.Error -> {
                    Log.e("ReportsCleanup", result.errorMessage, result.exception)
                    // Show error message if needed
                    // Toast.makeText(requireContext(), "Cleanup failed", Toast.LENGTH_SHORT).show()
                }
                is CleanupResult.NotFound -> {
                    Log.w("ReportsCleanup", result.message)
                }
            }
            // Hide loading indicator
            // binding.progressBar.visibility = View.GONE
        }
    }
    private suspend fun cleanupReports(): CleanupResult = withContext(Dispatchers.IO) {
        try {
            val db = FirebaseFirestore.getInstance()
            val docRef = db.collection("general").document("general_variables")

            val snapshot = docRef.get().await()

            if (!snapshot.exists()) {
                return@withContext CleanupResult.NotFound("Document general/general_variables not found")
            }

            val reports = snapshot.get("reports") as? List<Map<String, Any>> ?: emptyList()

            // Calculate cutoff date
            val cutoffDate = Calendar.getInstance().apply {
                add(Calendar.MONTH, -2)
            }.time

            // Filter reports efficiently
            val filteredReports = reports.filterTo(ArrayList(reports.size)) { report ->
                val ts = report["time"] as? com.google.firebase.Timestamp
                val date = ts?.toDate()
                date != null && date.after(cutoffDate)
            }

            // Only update if there are changes
            if (filteredReports.size < reports.size) {
                docRef.update("reports", filteredReports).await()
                return@withContext CleanupResult.Success(
                    "Old reports deleted. Remaining = ${filteredReports.size}"
                )
            } else {
                return@withContext CleanupResult.Success("No old reports to delete")
            }

        } catch (e: Exception) {
            return@withContext CleanupResult.Error("Failed to clean up reports: ${e.message}", e)
        }
    }

    // Result sealed class for better error handling
    sealed class CleanupResult {
        data class Success(val message: String) : CleanupResult()
        data class Error(val errorMessage: String, val exception: Exception? = null) : CleanupResult()
        data class NotFound(val message: String) : CleanupResult()
    }




    private fun getReports() {
        // Fetch reports from Firestore
        db.collection("general")
            .document("general_variables")
            .get()
            .addOnSuccessListener { document ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                binding.reportsProgressBar.visibility = View.GONE
                if (document.exists()) {
                    // Retrieve reports from Firestore document
                    val reportsList = document.get("reports") as? List<Map<String, Any>> ?: emptyList()

                    // Sort reports by reportTime in descending order (newest first)
                    val sortedReports = reportsList.sortedByDescending { it["time"] as? Timestamp }

                    if (sortedReports.isNotEmpty()) {
                        reportsAdapter.setReports(sortedReports)
                    } else {
                        Toast.makeText(requireContext(), "No reports available", Toast.LENGTH_SHORT).show()
                        if (!isAdded || _binding == null) return@addOnSuccessListener
                        binding.btnClearReports.visibility = View.GONE
                        binding.text2.visibility = View.VISIBLE
                    }

                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Failed to load reports: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteReports() {
        val reportsRef = db.collection("general").document("general_variables")

        reportsRef.update("reports", emptyList<Map<String, Any>>()) // Clear reports in Firestore
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "All reports deleted", Toast.LENGTH_SHORT).show()
                reportsAdapter.setReports(emptyList()) // Clear UI list
                getReports()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to delete reports: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
