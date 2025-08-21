package com.example.billkmotolinkltd.ui.reports

import android.annotation.SuppressLint
import android.app.AlertDialog
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
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.ui.Utility
import com.google.android.gms.location.LocationServices
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.launch

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
        val layoutOption1 = binding.layoutOption1
        val layoutOption2 = binding.layoutOption2
        val layoutOption3 = binding.layoutOption3

        // Define options
        val options = arrayOf("Mechanical Breakdown", "Police Arrest", "Accident")

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
                binding.inputOtherFormOfDescription.visibility = View.GONE

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

        val radioGroup1 = view.findViewById<RadioGroup>(R.id.layoutOption1)
        val radioGroup2 = view.findViewById<RadioGroup>(R.id.layoutOption2)
        val radioGroup3 = view.findViewById<RadioGroup>(R.id.layoutOption3)

        radioGroup1.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == R.id.mb5 && _binding != null && isAdded) {
                binding.inputOtherFormOfDescription.visibility = View.VISIBLE
            } else
                binding.inputOtherFormOfDescription.visibility = View.GONE
        }
        radioGroup2.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == R.id.pa9 && _binding != null && isAdded) {
                binding.inputOtherFormOfDescription.visibility = View.VISIBLE
            } else
                binding.inputOtherFormOfDescription.visibility = View.GONE
        }
        radioGroup3.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == R.id.ra5 && _binding != null && isAdded) {
                binding.inputOtherFormOfDescription.visibility = View.VISIBLE
            } else
                binding.inputOtherFormOfDescription.visibility = View.GONE
        }


        binding.btnSubmit.setOnClickListener {
            validateAndConfirmSubmission()
        }

    }
    private fun validateAndConfirmSubmission() {
        // Validate inputs first
        val reportType = binding.reportType.selectedItem?.toString()?.trim().orEmpty()
        val involvedBike = binding.involvedBike.selectedItem?.toString()?.trim().orEmpty()
        val reportDescription = getCheckedRadioValue().trim()

        if (reportType.isEmpty() || reportDescription.isEmpty() || involvedBike.isEmpty()) {
            showToast("All 3 values are required")
            return
        }

        showConfirmationDialog(reportType, involvedBike, reportDescription)
    }

    private fun showConfirmationDialog(reportType: String, bike: String, description: String) {
        AlertDialog.Builder(requireContext()).apply {
            // Custom styled title
            setTitle(createSpannable("Confirm Submission", Color.GREEN))

            // Custom styled message
            setMessage(createSpannable("Send report?", Color.GRAY))

            setIcon(R.drawable.success)
            setPositiveButton("Send") { _, _ -> submitReport() }
            setNegativeButton("No") { dialog, _ -> dialog.dismiss() }

            // Optional neutral button
            // setNeutralButton("More Info") { _, _ ->
            //     showToast("Deleting all reports is permanent.")
            // }

            create().apply {
                window?.setBackgroundDrawableResource(R.drawable.rounded_black)
                show()
            }
        }
    }

    private fun createSpannable(text: String, color: Int): SpannableString {
        return SpannableString(text).apply {
            setSpan(ForegroundColorSpan(color), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }


    private fun submitReport() {
        val reportType = binding.reportType.selectedItem?.toString()?.trim() ?: ""
        val involvedBike = binding.involvedBike.selectedItem?.toString()?.trim() ?: ""
        var reportDescription = getCheckedRadioValue()

        if (reportType.isEmpty() || reportDescription.isBlank() || involvedBike.isEmpty()) {
            Toast.makeText(requireContext(), "All 3 values are required", Toast.LENGTH_SHORT).show()
            return
        }

        if (reportDescription == "Other") {
            if (!isAdded || _binding == null) return
            val description = binding.inputOtherFormOfDescription.text
            if (description.isBlank() || description.length < 7) {
                Toast.makeText(requireContext(), "Describe your report better.", Toast.LENGTH_SHORT).show()
                return
            } else reportDescription = description.toString()
        }

        if (!isAdded || _binding == null) return
        binding.reportPBar.visibility = View.VISIBLE
        binding.btnSubmit.visibility = View.GONE

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
                            "time" to Timestamp.now(),
                            "location" to mapOf(
                                "latitude" to latitude,
                                "longitude" to longitude
                            )
                        )

                        // Use FieldValue.arrayUnion() to add a new report without overriding old ones
                        db.collection("general").document("general_variables")
                            .update("reports", FieldValue.arrayUnion(reportData))
                            .addOnSuccessListener {
                                if (!isAdded || _binding == null) return@addOnSuccessListener
                                binding.reportPBar.visibility = View.GONE
                                binding.btnSubmit.visibility = View.VISIBLE

                                lifecycleScope.launch {
                                    Utility.postTrace("Submitted a report on $reportType.")
                                    val roles = listOf("Admin", "CEO", "Systems, IT")
                                    Utility.notifyAdmins("$username just submitted a new $reportType report.", "Incidences & Accidents", roles)
                                }

                                Toast.makeText(requireContext(), "Report submitted successfully", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                if (!isAdded || _binding == null) return@addOnFailureListener
                                binding.reportPBar.visibility = View.GONE
                                binding.btnSubmit.visibility = View.VISIBLE
                                Toast.makeText(requireContext(), "Failed to submit report: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    if (!isAdded || _binding == null) return@addOnSuccessListener
                    binding.reportPBar.visibility = View.GONE
                    binding.btnSubmit.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "We couldn't get your username", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
            }
            .addOnFailureListener {
                if (!isAdded || _binding == null) return@addOnFailureListener
                binding.reportPBar.visibility = View.GONE
                binding.btnSubmit.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "Something is not right. Contact an Administrator", Toast.LENGTH_LONG).show()
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


    /*Get bike and company status*/

    private fun loadBikes() {
        if (!isAdded || _binding == null) return

        val currentUser = FirebaseAuth.getInstance().currentUser ?: run {
            showToast("No user logged in")
            return
        }

        // Load company status first (independent operation)
        loadCompanyStatus()

        // Load user-specific bike data
        loadUserBikes(currentUser)
    }

    private fun loadCompanyStatus() {
        FirebaseFirestore.getInstance()
            .collection("general")
            .document("general_variables")
            .get()
            .addOnSuccessListener { document ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                when (document.getString("companyState") ?: "Unknown") {
                    "Paused" -> {
                        binding.btnOperationsPaused.visibility = View.VISIBLE
                        binding.btnSubmit.visibility = View.GONE
                    }
                    "Continuing" -> {
                        binding.btnOperationsPaused.visibility = View.GONE
                        binding.btnSubmit.visibility = View.VISIBLE
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("CompanyStatus", "Failed to load company status", e)
            }
    }

    private fun loadUserBikes(currentUser: FirebaseUser) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .whereEqualTo("email", currentUser.email)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val userDoc = querySnapshot.documents.firstOrNull() ?: run {
                    showToast("User not found")
                    return@addOnSuccessListener
                }

                val currentUserName = userDoc.getString("userName") ?: run {
                    showToast("Invalid user data")
                    return@addOnSuccessListener
                }

                loadAssignedBikes(currentUserName)
            }
            .addOnFailureListener { e ->
                showToast("Failed to fetch user: ${e.message}")
                Log.e("UserData", "Failed to load user data", e)
            }
    }

    private fun loadAssignedBikes(currentUserName: String) {
        FirebaseFirestore.getInstance()
            .collection("general")
            .document("general_variables")
            .get()
            .addOnSuccessListener { document ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                val batteriesMap = document.get("batteries") as? Map<String, Map<String, Any>> ?: emptyMap()
                val bikesArray = batteriesMap.values
                    .asSequence()  // Use sequence for lazy evaluation
                    .filter { it["assignedRider"] == currentUserName }
                    .mapNotNull { it["assignedBike"] as? String }
                    .distinct()
                    .toList()

                if (bikesArray.isEmpty()) {
                    showToast("You haven't clocked in with any bike", Toast.LENGTH_LONG)
                    return@addOnSuccessListener
                }

                updateBikeSpinner(bikesArray)
            }
            .addOnFailureListener { e ->
                showToast("Failed to load batteries: ${e.message}")
                Log.e("BatteryData", "Failed to load battery data", e)
            }
    }

    private fun updateBikeSpinner(bikes: List<String>) {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            bikes
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.involvedBike.adapter = adapter
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        if (isAdded && _binding != null) {
            Toast.makeText(requireContext(), message, duration).show()
        }
    }

    /*Bike and company status check ends here*/


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
