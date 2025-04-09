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
import android.widget.Button

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
        getReports()



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

            val dialog = alertDialog.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

            dialog.show()


        }
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

                    // Reverse the list to show new reports first
                    val reversedReports = reportsList.reversed()

                    // Update the adapter with the reversed reports
                    if (reversedReports.isNotEmpty()) {
                        reportsAdapter.setReports(reversedReports)
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
