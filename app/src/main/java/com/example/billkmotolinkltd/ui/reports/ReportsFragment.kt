package com.example.billkmotolinkltd.ui.reports

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.billkmotolinkltd.databinding.FragmentReportsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.location.Location
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import com.example.billkmotolinkltd.R
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize fusedLocationClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        val spinner: Spinner = binding.reportType
        val spinner2: Spinner = binding.involvedBike
        val layoutOption1 = binding.layoutOption1
        val layoutOption2 = binding.layoutOption2
        val layoutOption3 = binding.layoutOption3

        // Define options
        val options = arrayOf("Mechanical Breakdown", "Police Arrest", "Traffic Accident")

        // Create an adapter
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, options)

        // Set the adapter to the spinner
        spinner.adapter = adapter

        // Handle selection events
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                // Hide all layouts first
                layoutOption1.visibility = View.GONE
                layoutOption2.visibility = View.GONE
                layoutOption3.visibility = View.GONE

                // Show the selected layout
                when (position) {
                    0 -> layoutOption1.visibility = View.VISIBLE
                    1 -> layoutOption2.visibility = View.VISIBLE
                    2 -> layoutOption3.visibility = View.VISIBLE
                }

                clearRadioSelections(binding.layoutOption1)
                clearRadioSelections(binding.layoutOption2)
                clearRadioSelections(binding.layoutOption3)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        loadBikes()


        binding.btnSubmit.setOnClickListener {
            val reportType = binding.reportType.selectedItem?.toString()?.trim() ?: ""
            val involvedBike = binding.involvedBike.selectedItem?.toString()?.trim() ?: ""
            val reportDescription = getCheckedRadioValue()

            if (reportType.isEmpty() || reportDescription.isEmpty() || involvedBike.isEmpty()) {
                Toast.makeText(requireContext(), "All 3 values are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Confirm Submission")
            title.setSpan(ForegroundColorSpan(Color.GREEN), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("Send report?")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.success)

            alertDialog.setPositiveButton("Yes") { _, _ ->
                submitReport()
            }

            alertDialog.setNegativeButton("No") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
            }

//            alertDialog.setNeutralButton("More Info") { _, _ ->
//                Toast.makeText(requireContext(), "Deleting all reports is permanent.", Toast.LENGTH_SHORT).show()
//            }

            val dialog = alertDialog.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

            dialog.show()

        }
    }


    private fun submitReport() {
        val reportType = binding.reportType.selectedItem?.toString()?.trim() ?: ""
        val involvedBike = binding.involvedBike.selectedItem?.toString()?.trim() ?: ""
        val reportDescription = getCheckedRadioValue()

        if (reportType.isEmpty() || reportDescription.isEmpty() || involvedBike.isEmpty()) {
            Toast.makeText(requireContext(), "All 3 values are required", Toast.LENGTH_SHORT).show()
            return
        }

        val timestamp = System.currentTimeMillis()
        val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

        val currentUser = FirebaseAuth.getInstance().currentUser
        val userEmail = currentUser?.email ?: return

        val db = FirebaseFirestore.getInstance()
        db.collection("users")
            .whereEqualTo("email", userEmail)  // Find user by email
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val username = documents.documents[0].getString("userName") ?: "Unknown User"
                    fetchCurrentLocation { latitude, longitude ->
                        val reportData = mapOf(
                            "username" to username,  // Add username
                            "reportType" to reportType,
                            "reportDescription" to reportDescription,
                            "involvedBike" to involvedBike,
                            "time" to formattedTime,
                            "location" to mapOf(
                                "latitude" to latitude,
                                "longitude" to longitude
                            )
                        )

                        // Use FieldValue.arrayUnion() to add a new report without overriding old ones
                        db.collection("general").document("general_variables")
                            .update("reports", FieldValue.arrayUnion(reportData))
                            .addOnSuccessListener {
                                Toast.makeText(requireContext(), "Report submitted successfully", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(requireContext(), "Failed to submit report: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    Toast.makeText(requireContext(), "We couldn't get your username", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
            }
            .addOnFailureListener {
            }
    }

    private fun getCheckedRadioValue(): String {
        val radioGroups = listOf(binding.layoutOption1, binding.layoutOption2, binding.layoutOption3)
        for (group in radioGroups) {
            if (group.visibility == View.VISIBLE) {
                val checkedId = group.checkedRadioButtonId
                if (checkedId != -1) {
                    return group.findViewById<RadioButton>(checkedId).text.toString()
                }
            }
        }
        return ""
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation(callback: (Double, Double) -> Unit) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                callback(location.latitude, location.longitude)
            } else {
                callback(0.0, 0.0) // Default values if location is unavailable
            }
        }.addOnFailureListener {
            callback(0.0, 0.0) // Handle failure
        }
    }

    private fun loadBikes() {
        val db = FirebaseFirestore.getInstance()
        val bikesRef = db.collection("general").document("general_variables")

        bikesRef.get()
            .addOnSuccessListener { document ->
                if (!isAdded || _binding == null) return@addOnSuccessListener // Prevent crash

                val bikesList = document.get("bikes") as? List<String> ?: emptyList()

                if (bikesList.isEmpty()) {
                    Toast.makeText(requireContext(), "No bikes found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Set up adapter
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, bikesList)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.involvedBike.adapter = adapter
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to load bikes: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        bikesRef.get().addOnSuccessListener { companyDoc ->
            if (!isAdded || view == null || _binding == null) return@addOnSuccessListener // Prevent crash

            val companyStatus = companyDoc.getString("companyState") ?: "Unknown"
            if (companyStatus == "Paused") {
                binding.btnOperationsPaused.visibility = View.VISIBLE
                binding.btnSubmit.visibility = View.GONE
            } else if (companyStatus == "Continuing") {
                binding.btnOperationsPaused.visibility = View.GONE
                binding.btnSubmit.visibility = View.VISIBLE
            }
        }

    }

    /**
     * Helper function to clear selected radio buttons in a given layout.
     */
    private fun clearRadioSelections(layout: ViewGroup) {
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            if (child is RadioGroup) {
                child.clearCheck() // Clear all selected radio buttons in the group
            } else if (child is RadioButton) {
                child.isChecked = false // Ensure individual radio buttons are unchecked
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
