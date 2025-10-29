package com.billkmotolink.ltd.ui.clockins

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.billkmotolink.ltd.R
import com.billkmotolink.ltd.databinding.FragmentClockinsBinding
import com.billkmotolink.ltd.ui.Utility
import com.billkmotolink.ltd.ui.globalDateKey
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.collections.iterator
import kotlin.toString

class ClockInFragment : Fragment() {
    private var _binding: FragmentClockinsBinding? = null

    // This property is only valid between onCreateView and onDestroyView ofc.
    private val binding get() = _binding!!
    private var userBike: String = "Unknown"

    private var derivedUserName: String = ""


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentClockinsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        handler.post(fetchRunnable)

        binding.btnClockIn.setOnClickListener {
            val alertDialog = AlertDialog.Builder(requireContext())

            val title = SpannableString("Clock In").apply {
                setSpan(ForegroundColorSpan(Color.GREEN), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            val message = SpannableString("Confirm action.").apply {
                setSpan(ForegroundColorSpan(Color.GRAY), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.warn)

            alertDialog.setPositiveButton("Proceed") { _, _ ->
                handleAssignBatteries()
            }
            alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                resetUI()
                dialog.dismiss()
            }

            val dialog = alertDialog.create().apply {
                window?.setBackgroundDrawableResource(R.drawable.rounded_black)
                show()

                // Change button text colors after showing
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.GREEN)  // confirm button
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.RED)    // cancel button
            }
            dialog.setCancelable(false)
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black)
        }

        binding.btnSwapBatteries.setOnClickListener {
            swapBatteries()
        }

        binding.btnChargeBatteries.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext()).apply {
                // Custom styled title
                setTitle(createSpannable("Clock Ins", Color.GREEN))

                // Custom styled message
                setMessage(createSpannable("Charge batteries?", Color.GRAY))
                setIcon(R.drawable.success)
                setPositiveButton("Charge") { _, _ -> chargeBatteries() }
                setNegativeButton("cancel") { dialog, _ -> dialog.dismiss() }

                create().apply {
                    window?.setBackgroundDrawableResource(R.drawable.rounded_black)
                    show()

                    getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.GREEN)  // confirm button
                    getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.RED)
                }
            }
        }

        binding.btnReloadBatteries.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext()).apply {
                // Custom styled title
                setTitle(createSpannable("Clock Ins", Color.GREEN))

                // Custom styled message
                setMessage(createSpannable("Reload batteries?", Color.GRAY))
                setIcon(R.drawable.success)
                setPositiveButton("Reload") { _, _ -> reloadBatteries() }
                setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }

                create().apply {
                    window?.setBackgroundDrawableResource(R.drawable.rounded_black)
                    show()

                    getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.GREEN)  // confirm button
                    getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.RED)
                }
            }
        }

        binding.btnManualBatteryDrop.setOnClickListener {

            if (!Utility.isInternetAvailable(requireContext())) {
                Toast.makeText(requireContext(), "You don't have internet.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            } else {
                if (_binding == null || !isAdded) return@setOnClickListener

                val db = FirebaseFirestore.getInstance()
                val docRef = db.collection("general").document("general_variables")

                docRef.get().addOnSuccessListener { snapshot ->
                    val destinations = snapshot.get("destinations") as? List<String> ?: emptyList()
                    showLocationDialog(destinations.sorted())
                }.addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to fetch destinations", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.clockInSwipeRefreshLayout.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                loadUserStatus()
                loadAvailableBatteriesAsCheckboxes()
                loadBikes()
                binding.clockInSwipeRefreshLayout.isRefreshing = false
            }
        }
        binding.clockInSwipeRefreshLayout.setColorSchemeResources(
            R.color.color4,
            R.color.color3,
            R.color.semiTransparent
        )
    }

    private fun createSpannable(text: String, color: Int): SpannableString {
        return SpannableString(text).apply {
            setSpan(ForegroundColorSpan(color), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun showLocationDialog(locations: List<String>) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_location_picker, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.locationSpinner)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, locations)
        spinner.adapter = adapter

        AlertDialog.Builder(requireContext())
            .setTitle("BILLK MOTOLINK LTD")
            .setView(dialogView)
            .setPositiveButton("Select") { _, _ ->
                val selectedLocation = spinner.selectedItem.toString()
                Toast.makeText(requireContext(), "Dropping batteries to $selectedLocation.", Toast.LENGTH_SHORT).show()
                // Use selectedLocation here (e.g., store it, pass to ViewModel, etc.)
                dropBatteriesManually(selectedLocation)
            }
            .setNegativeButton("Cancel", null)
            .create().apply {
                window?.setBackgroundDrawableResource(R.drawable.rounded_black)
                show()

                getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.GREEN)  // confirm button
                getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.RED)
            }
    }

    private fun dropBatteriesManually(location: String) {
        if (!isAdded || view == null || _binding == null) return

        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val currentUserEmail = auth.currentUser?.email ?: return

        // Step 1: Locate this user
        db.collection("users").whereEqualTo("email", currentUserEmail).get()
            .addOnSuccessListener { userSnapshot ->
                val userDoc = userSnapshot.documents.firstOrNull()
                if (userDoc == null) {
                    Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val userRef = db.collection("users").document(userDoc.id)
                val userName = userDoc.getString("userName") ?: "Unidentified"

                // Reset current bike
                userRef.update("currentBike", "None")
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Current user bike updated.", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Failed to update current bike: ${e.message}", Toast.LENGTH_SHORT).show()
                    }

                // Step 2: Query all batteries assigned to this user
                val batteriesRef = db.collection("batteries")
                batteriesRef.whereEqualTo("assignedRider", userName).get()
                    .addOnSuccessListener { batterySnapshot ->
                        if (batterySnapshot.isEmpty) {
                            Toast.makeText(requireContext(), "No batteries to drop.", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        val batch = db.batch()
                        val todayKey = globalDateKey

                        for (batteryDoc in batterySnapshot.documents) {
                            val batteryRef = batteriesRef.document(batteryDoc.id)
                            val traces = batteryDoc.get("traces") as? MutableMap<String, MutableMap<String, Any>> ?: mutableMapOf()

                            // Add a trace entry for drop
                            val todayEntries = traces[todayKey]?.get("entries") as? MutableList<String> ?: mutableListOf()
                            todayEntries.add("Dropped manually by $userName at ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}")
                            traces[todayKey] = mutableMapOf(
                                "entries" to todayEntries,
                                "dateEdited" to Timestamp.now()
                            )

                            batch.update(batteryRef, mapOf(
                                "assignedRider" to "None",
                                "assignedBike" to "None",
                                "batteryLocation" to location,
                                "offTime" to Timestamp.now(),
                                "traces" to traces
                            ))
                        }

                        // Commit batch
                        batch.commit()
                            .addOnSuccessListener {
                                Toast.makeText(requireContext(), "Dropped all assigned batteries.", Toast.LENGTH_SHORT).show()
                                loadAvailableBatteriesAsCheckboxes()
                                loadUserStatus()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(requireContext(), "Failed to drop batteries: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Failed to fetch batteries: ${e.message}", Toast.LENGTH_SHORT).show()
                    }

                // Step 3: Update bike status in general_variables
                val generalRef = db.collection("general").document("general_variables")
                val userBike = userDoc.getString("currentBike") ?: return@addOnSuccessListener
                generalRef.get().addOnSuccessListener { doc ->
                    val bikesMap = doc.get("bikes") as? MutableMap<String, MutableMap<String, Any>> ?: mutableMapOf()
                    if (bikesMap.containsKey(userBike)) {
                        bikesMap[userBike]?.apply {
                            set("isAssigned", false)
                            set("rider", "None")
                        }
                        generalRef.update("bikes", bikesMap)
                            .addOnSuccessListener {
                                Toast.makeText(requireContext(), "Bike updated.", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(requireContext(), "Failed to update bike: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to fetch user: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    } // function under new path

    val handler = Handler(Looper.getMainLooper())
    val fetchRunnable = object : Runnable {
        override fun run() {
            if (Utility.isInternetAvailable(requireContext())) {
                loadUserStatus()
                loadAvailableBatteriesAsCheckboxes()
                loadBikes()
            } else {
                Toast.makeText(requireContext(), "No internet connection. Couldn't reload batteries...", Toast.LENGTH_SHORT).show()
            }
            handler.postDelayed(this, 60000) // Run again in 1 minute
        }
    }

    private fun chargeBatteries() {
        val container = _binding?.batteriesToCharge
        val selectedLocation = _binding?.batteryChargeDestination?.selectedItem as? String
        val selectedBatteries = mutableListOf<String>()

        container?.let {
            for (i in 0 until it.childCount) {
                val view = it.getChildAt(i)
                if (view is CheckBox && view.isChecked) {
                    selectedBatteries.add(view.text.toString())
                }
            }
        }

        if (selectedBatteries.isEmpty()) {
            Toast.makeText(requireContext(), "Select at least one battery", Toast.LENGTH_SHORT).show()
            return
        }

        val batteryCount = selectedBatteries.size
        val batteryNames = selectedBatteries.joinToString(", ")
        val pBar = _binding?.batteryChargePBar
        val submitBtn = _binding?.btnChargeBatteries

        pBar?.visibility = View.VISIBLE
        submitBtn?.visibility = View.GONE
        binding.clockInPBar.visibility = View.VISIBLE
        binding.btnClockIn.visibility = View.GONE

        val db = FirebaseFirestore.getInstance()
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: return

        lifecycleScope.launch {
            try {
                // Fetch current user
                val querySnapshot = withContext(Dispatchers.IO) {
                    db.collection("users").whereEqualTo("email", currentUserEmail).get().await()
                }
                val userDoc = querySnapshot.documents.firstOrNull()
                val userId = userDoc?.id ?: run {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                        pBar?.visibility = View.GONE
                        submitBtn?.visibility = View.VISIBLE
                    }
                    return@launch
                }
                val userName = userDoc.getString("userName") ?: "Unknown"

                // Update batteries using new path
                for (batteryName in selectedBatteries) {
                    val batteryQuery = withContext(Dispatchers.IO) {
                        db.collection("batteries").whereEqualTo("batteryName", batteryName).get().await()
                    }
                    val batteryDoc = batteryQuery.documents.firstOrNull() ?: continue
                    val docRef = batteryDoc.reference

                    val traces = (batteryDoc.get("traces") as? MutableMap<String, MutableMap<String, Any>>) ?: mutableMapOf()
                    val todayKey = globalDateKey
                    val todayEntries = (traces[todayKey]?.get("entries") as? MutableList<String>) ?: mutableListOf()
                    todayEntries.add("Battery started charging by $userName at ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}")

                    traces[todayKey] = mutableMapOf(
                        "entries" to todayEntries,
                        "dateEdited" to Timestamp.now()
                    )

                    val updates = mapOf(
                        "batteryLocation" to "Charging at $selectedLocation",
                        "assignedBike" to "None",
                        "assignedRider" to "None",
                        "offTime" to Timestamp.now(),
                        "traces" to traces
                    )
                    withContext(Dispatchers.IO) { docRef.update(updates).await() }
                }

                // Update user to indicate charging
                withContext(Dispatchers.IO) {
                    db.collection("users").document(userId).update("isCharging", true).await()
                }

                withContext(Dispatchers.Main) {
                    loadBikes()
                    loadAvailableBatteriesAsCheckboxes()
                    loadUserStatus()

                    val grammar = if (batteryCount == 1) "y" else "ies"
                    lifecycleScope.launch {
                        Utility.postTrace("Began charging $batteryCount batter$grammar ($batteryNames) at $selectedLocation.")
                    }

                    Toast.makeText(requireContext(), "Batteries are now charging.", Toast.LENGTH_SHORT).show()
                    pBar?.visibility = View.GONE
                    submitBtn?.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error charging batteries: ${e.message}", Toast.LENGTH_SHORT).show()
                    pBar?.visibility = View.GONE
                    submitBtn?.visibility = View.VISIBLE
                }
            }
        }
    } // function updated

    private fun reloadBatteries() {
        val container = _binding?.batteriesToReload
        val selectedBatteries = mutableListOf<String>()

        container?.let {
            for (i in 0 until it.childCount) {
                val view = it.getChildAt(i)
                if (view is CheckBox && view.isChecked) {
                    selectedBatteries.add(view.text.toString())
                }
            }
        }

        if (selectedBatteries.isEmpty()) {
            Toast.makeText(requireContext(), "Select at least one battery", Toast.LENGTH_SHORT).show()
            return
        }

        val batteryCount = selectedBatteries.size
        val batteryNames = selectedBatteries.joinToString(", ")
        val pBar = _binding?.batteryReloadPBar
        val submitBtn = _binding?.btnReloadBatteries

        pBar?.visibility = View.VISIBLE
        submitBtn?.visibility = View.GONE

        val db = FirebaseFirestore.getInstance()
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: return

        lifecycleScope.launch {
            try {
                // Fetch current user
                val querySnapshot = withContext(Dispatchers.IO) {
                    db.collection("users").whereEqualTo("email", currentUserEmail).get().await()
                }
                val userDoc = querySnapshot.documents.firstOrNull()
                val userId = userDoc?.id ?: run {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                        pBar?.visibility = View.GONE
                        submitBtn?.visibility = View.VISIBLE
                    }
                    return@launch
                }

                val username = userDoc.getString("userName") ?: "Unidentified"
                val bike = userDoc.getString("currentBike") ?: "Unidentified"

                // Update batteries on new path
                for (batteryName in selectedBatteries) {
                    val batteryQuery = withContext(Dispatchers.IO) {
                        db.collection("batteries").whereEqualTo("batteryName", batteryName).get().await()
                    }
                    val batteryDoc = batteryQuery.documents.firstOrNull() ?: continue
                    val docRef = batteryDoc.reference

                    // Prepare trace
                    val traces = (batteryDoc.get("traces") as? MutableMap<String, MutableMap<String, Any>>) ?: mutableMapOf()
                    val todayKey = globalDateKey
                    val todayEntries = (traces[todayKey]?.get("entries") as? MutableList<String>) ?: mutableListOf()
                    todayEntries.add("Battery loaded by $username at ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}")

                    traces[todayKey] = mutableMapOf(
                        "entries" to todayEntries,
                        "dateEdited" to Timestamp.now()
                    )

                    val updates = mapOf(
                        "batteryLocation" to "In Motion",
                        "assignedRider" to username,
                        "assignedBike" to bike,
                        "offTime" to Timestamp.now(),
                        "traces" to traces
                    )
                    withContext(Dispatchers.IO) { docRef.update(updates).await() }
                }

                // Mark user as not charging
                withContext(Dispatchers.IO) {
                    db.collection("users").document(userId).update("isCharging", false).await()
                }

                withContext(Dispatchers.Main) {
                    loadBikes()
                    loadAvailableBatteriesAsCheckboxes()
                    loadUserStatus()

                    val grammar = if (batteryCount == 1) "y" else "ies"
                    lifecycleScope.launch {
                        Utility.postTrace("Loaded $batteryCount batter$grammar ($batteryNames).")
                    }

                    Toast.makeText(requireContext(), "Loaded.", Toast.LENGTH_SHORT).show()
                    pBar?.visibility = View.GONE
                    submitBtn?.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error loading batteries: ${e.message}", Toast.LENGTH_SHORT).show()
                    pBar?.visibility = View.GONE
                    submitBtn?.visibility = View.VISIBLE
                }
            }
        }
    } // function up to date

    /*clock in begins here*/
    private fun handleAssignBatteries() {
        if (!isAdded || view == null || _binding == null) return

        if (derivedUserName.isEmpty()) {
            Toast.makeText(requireContext(), "No username.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.clockInPBar.visibility = View.VISIBLE
        binding.btnClockIn.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // Validate mileage input
                val inputMileage = withContext(Dispatchers.Default) { binding.inputMileage.text.toString().trim() }
                if (!inputMileage.matches(Regex("^\\d+(\\.\\d+)?$"))) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Invalid mileage.", Toast.LENGTH_SHORT).show()
                        resetUI()
                    }
                    return@launch
                }
                val mileage = inputMileage.toDouble()

                val selectedBike = withContext(Dispatchers.Main) { _binding?.bikeToClockIn?.selectedItem as? String }
                if (selectedBike.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Please select a bike", Toast.LENGTH_SHORT).show()
                        resetUI()
                    }
                    return@launch
                }
                if (selectedBike.contains("DUMMY", ignoreCase = true)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Dummy bikes are only used by the IT Department.", Toast.LENGTH_LONG).show()
                        resetUI()
                    }
                    return@launch
                }

                // Collect selected batteries
                val selectedBatteries = withContext(Dispatchers.Default) {
                    val container = _binding?.batteryCheckboxContainer
                    val batteries = mutableListOf<String>()
                    container?.let {
                        for (i in 0 until it.childCount) {
                            val view = it.getChildAt(i)
                            if (view is CheckBox && view.isChecked) {
                                batteries.add(view.text.toString())
                            }
                        }
                    }
                    batteries
                }

                if (selectedBatteries.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Select at least one battery", Toast.LENGTH_SHORT).show()
                        resetUI()
                    }
                    return@launch
                }



                /*UPDATES*/
                val db = FirebaseFirestore.getInstance()

                // Update the selected bike in a transaction
                db.runTransaction { transaction ->
                    val generalRef = db.collection("general").document("general_variables")
                    val generalSnapshot = transaction.get(generalRef)

                    val bikesMap = generalSnapshot.get("bikes") as? MutableMap<String, Any> ?: mutableMapOf()
                    val selectedBikeData = bikesMap[selectedBike] as? MutableMap<String, Any> ?: mutableMapOf()
                    selectedBikeData["isAssigned"] = true
                    selectedBikeData["assignedRider"] = derivedUserName
                    bikesMap[selectedBike] = selectedBikeData
                    transaction.update(generalRef, "bikes", bikesMap)
                }.await()

                // Assign batteries using new path and add trace entries
                val batteryCollection = db.collection("batteries")
                for (batteryName in selectedBatteries) {
                    val batteryQuery = batteryCollection.whereEqualTo("batteryName", batteryName).get().await()
                    val batteryDoc = batteryQuery.documents.firstOrNull() ?: continue
                    val docRef = batteryDoc.reference
                    val traces = (batteryDoc.get("traces") as? MutableMap<String, MutableMap<String, Any>>) ?: mutableMapOf()

                    val todayKey = globalDateKey
                    val todayEntries = (traces[todayKey]?.get("entries") as? MutableList<String>) ?: mutableListOf()
                    todayEntries.add("Battery loaded through clock-in by $derivedUserName at ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}")

                    traces[todayKey] = mutableMapOf("entries" to todayEntries, "dateEdited" to Timestamp.now())

                    val updates = mapOf(
                        "assignedRider" to derivedUserName,
                        "assignedBike" to selectedBike,
                        "batteryLocation" to "In Use",
                        "offTime" to Timestamp.now(),
                        "traces" to traces
                    )
                    docRef.update(updates).await()
                }

                // Update current user document
                val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: return@launch
                val querySnapshot = db.collection("users").whereEqualTo("email", currentUserEmail).get().await()
                val userDoc = querySnapshot.documents.firstOrNull()
                if (userDoc == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                        resetUI()
                    }
                    return@launch
                }
                val userId = userDoc.id
                db.collection("users").document(userId).update(
                    mapOf(
                        "currentBike" to selectedBike,
                        "clockInTime" to Timestamp.now(),
                        "clockinMileage" to mileage,
                        "isClockedIn" to true
                    )
                ).await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Batteries assigned and bike updated successfully.", Toast.LENGTH_SHORT).show()
                    loadUserStatus()
                    loadAvailableBatteriesAsCheckboxes()
                    resetUI()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    resetUI()
                }
            }
        }
    } // function is up to date

    private fun resetUI() {
        if (!isAdded || view == null || _binding == null) return
        binding.clockInPBar.visibility = View.GONE
        binding.btnClockIn.visibility = View.VISIBLE
    }
    /*Clock ins end here*/






    /*Loading of available batteries starts here*/
    private fun loadAvailableBatteriesAsCheckboxes() {
        if (!isAdded || _binding == null) return
        val db = FirebaseFirestore.getInstance()
        val batteriesRef = db.collection("batteries")
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Fetch current user info
                val userSnapshot = withContext(Dispatchers.IO) {
                    db.collection("users").whereEqualTo("email", currentUserEmail).get().await()
                }
                val userDoc = userSnapshot.documents.firstOrNull()
                val userName = userDoc?.getString("userName")
                val isClockedIn = userDoc?.getBoolean("isClockedIn") == true

                if (userName.isNullOrEmpty()) {
                    binding.clockInPBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Fetch all batteries
                val batteriesSnapshot = withContext(Dispatchers.IO) { batteriesRef.get().await() }

                if (!isAdded || _binding == null) return@launch

                val batteryDocs = batteriesSnapshot.documents
                val unassignedBatteries = batteryDocs.filter {
                    !(it.getBoolean("isAssigned") ?: false)
                }.sortedBy { it.getString("batteryName") ?: "" }

                val containerClockIn = _binding?.batteryCheckboxContainer
                val containerLoad = _binding?.batteriesToOnLoadCheckboxContainer
                val containerOffload = _binding?.batteriesToOffloadCheckboxContainer
                val containerCharge = _binding?.batteriesToCharge
                val containerReload = _binding?.batteriesToReload

                // Clear all containers
                containerClockIn?.removeAllViews()
                containerLoad?.removeAllViews()
                containerOffload?.removeAllViews()
                containerCharge?.removeAllViews()
                containerReload?.removeAllViews()

                // Clock-in and load checkboxes (max 2 for clock-in)
                val selectedCheckBoxesClockIn = mutableListOf<CheckBox>()
                for (batteryDoc in unassignedBatteries) {
                    val batteryName = batteryDoc.getString("batteryName") ?: continue

                    val checkBoxClockIn = createLimitedCheckBox(batteryName, selectedCheckBoxesClockIn, 2)
                    val checkBoxLoad = createLimitedCheckBox(batteryName, selectedCheckBoxesClockIn, 1)

                    containerClockIn?.addView(checkBoxClockIn)
                    containerLoad?.addView(checkBoxLoad)
                }

                if (containerClockIn?.childCount == 0) {
                    Toast.makeText(requireContext(), "No unassigned batteries found", Toast.LENGTH_SHORT).show()
                    binding.noBatteriesToSwap.visibility = View.VISIBLE
                    binding.batterySwapDiv.visibility = View.GONE
                    binding.youCantClockIn.visibility = View.VISIBLE
                    containerLoad?.visibility = View.GONE
                }

                // Batteries assigned to this user (for offload and charge)
                val assignedBatteries = batteryDocs.filter {
                    it.getString("assignedRider") == userName
                }.sortedBy { it.getString("batteryName") ?: "" }

                var userBike: String? = null
                for (batteryDoc in assignedBatteries) {
                    val batteryName = batteryDoc.getString("batteryName") ?: continue
                    val assignedBike = batteryDoc.getString("assignedBike") ?: "None"

                    val offloadCheckbox = CheckBox(requireContext()).apply {
                        text = batteryName
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                    containerOffload?.addView(offloadCheckbox)
                    userBike = assignedBike

                    val chargeCheckbox = CheckBox(requireContext()).apply {
                        text = batteryName
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                    containerCharge?.addView(chargeCheckbox)
                }

                // Show/hide sections
                binding.batterySwapDiv.visibility = if (containerOffload?.childCount ?: 0 > 0) View.VISIBLE else View.GONE
                binding.batteryChargeDiv.visibility = if (containerCharge?.childCount ?: 0 > 0) View.VISIBLE else View.GONE

                // Reload section (only one battery can be loaded)
                if ((containerOffload?.childCount ?: 0) <= 1 && isClockedIn) {
                    val reloadContainer = containerReload
                    val selectedCheckBoxesReload = mutableListOf<CheckBox>()

                    val reloadCandidates = batteryDocs.filter {
                        (it.getString("assignedRider") ?: "None") == "None"
                    }.sortedBy { it.getString("batteryName") ?: "" }

                    for (batteryDoc in reloadCandidates) {
                        val batteryName = batteryDoc.getString("batteryName") ?: continue
                        val checkBox = createLimitedCheckBox(batteryName, selectedCheckBoxesReload, 1)
                        reloadContainer?.addView(checkBox)
                    }

                    reloadContainer?.visibility = if (reloadContainer?.childCount ?: 0 > 0) View.VISIBLE else View.GONE
                    binding.batteryReloadDiv.visibility = reloadContainer?.visibility ?: View.GONE
                } else {
                    binding.batteryReloadDiv.visibility = View.GONE
                }

            } catch (e: Exception) {
                if (isAdded && _binding != null) {
                    Toast.makeText(requireContext(), "Failed to load batteries: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    } /* function is up to date*/

    /** Helper function to create a CheckBox with selection limit */
    private fun createLimitedCheckBox(
        text: String,
        selectedList: MutableList<CheckBox>,
        maxSelected: Int
    ): CheckBox {
        return CheckBox(requireContext()).apply {
            this.text = text
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedList.add(this)
                    if (selectedList.size > maxSelected) {
                        val firstChecked = selectedList.removeAt(0)
                        firstChecked.isChecked = false
                    }
                } else {
                    selectedList.remove(this)
                }
            }
        }
    }

    /*loading batteries ends here*/




    private fun swapBatteries() {
        val selectedLocation = _binding?.batterySwapDestination?.selectedItem as? String ?: "Unknown"
        val offContainer = _binding?.batteriesToOffloadCheckboxContainer
        val onContainer = _binding?.batteriesToOnLoadCheckboxContainer

        val selectedOffloadBatteries = mutableListOf<String>()
        val selectedOnBatteries = mutableListOf<String>()

        offContainer?.let {
            for (i in 0 until it.childCount) {
                val view = it.getChildAt(i)
                if (view is CheckBox && view.isChecked) selectedOffloadBatteries.add(view.text.toString())
            }
        }

        onContainer?.let {
            for (i in 0 until it.childCount) {
                val view = it.getChildAt(i)
                if (view is CheckBox && view.isChecked) selectedOnBatteries.add(view.text.toString())
            }
        }

        if (selectedOffloadBatteries.isEmpty()) {
            Toast.makeText(requireContext(), "You are offloading nothing!", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedOnBatteries.isEmpty()) {
            Toast.makeText(requireContext(), "You haven't loaded a battery!", Toast.LENGTH_SHORT).show()
            return
        }

        val title = SpannableString("Swap Battery").apply {
            setSpan(ForegroundColorSpan(Color.GREEN), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val message = SpannableString("Confirm action.").apply {
            setSpan(ForegroundColorSpan(Color.GRAY), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        AlertDialog.Builder(requireContext()).apply {
            setTitle(title)
            setMessage(message)
            setIcon(R.drawable.success)
            setPositiveButton("Swap") { _, _ ->
                binding.batterySwapPBar.visibility = View.VISIBLE
                binding.btnSwapBatteries.visibility = View.GONE

                val db = FirebaseFirestore.getInstance()
                val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: return@setPositiveButton

                // Coroutine-based transaction
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        // Fetch userName
                        val userSnapshot = withContext(Dispatchers.IO) {
                            db.collection("users").whereEqualTo("email", currentUserEmail).get().await()
                        }
                        val userDoc = userSnapshot.documents.firstOrNull()
                        val userName = userDoc?.getString("userName") ?: run {
                            binding.batterySwapPBar.visibility = View.GONE
                            binding.btnSwapBatteries.visibility = View.VISIBLE
                            Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        // Process offload batteries
                        selectedOffloadBatteries.forEach { batteryName ->
                            val batteryQuery = db.collection("batteries").whereEqualTo("batteryName", batteryName).get().await()
                            val batteryDoc = batteryQuery.documents.firstOrNull() ?: return@forEach

                            val batteryRef = batteryDoc.reference
                            val todayKey = globalDateKey

                            // Update fields and traces
                            val traces = batteryDoc.get("traces") as? MutableMap<String, MutableMap<String, Any>> ?: mutableMapOf()
                            val todayEntries = traces[todayKey]?.get("entries") as? MutableList<String> ?: mutableListOf()
                            todayEntries.add("Battery dropped via swap by $userName at ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}")
                            traces[todayKey] = mutableMapOf("entries" to todayEntries, "dateEdited" to Timestamp.now())

                            batteryRef.update(
                                mapOf(
                                    "assignedRider" to "None",
                                    "assignedBike" to "None",
                                    "batteryLocation" to selectedLocation,
                                    "offTime" to Timestamp.now(),
                                    "traces" to traces
                                )
                            ).await()
                        }

                        // Process load batteries
                        selectedOnBatteries.forEach { batteryName ->
                            val batteryQuery = db.collection("batteries").whereEqualTo("batteryName", batteryName).get().await()
                            val batteryDoc = batteryQuery.documents.firstOrNull() ?: return@forEach

                            val batteryRef = batteryDoc.reference
                            val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
                            val todayKey = SimpleDateFormat("EEEE", Locale.getDefault()).format(today.time)

                            val traces = batteryDoc.get("traces") as? MutableMap<String, MutableMap<String, Any>> ?: mutableMapOf()
                            val todayEntries = traces[todayKey]?.get("entries") as? MutableList<String> ?: mutableListOf()
                            todayEntries.add("Battery loaded via swap by $userName at ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(
                                Date()
                            )}")
                            traces[todayKey] = mutableMapOf("entries" to todayEntries, "dateEdited" to Timestamp.now())

                            batteryRef.update(
                                mapOf(
                                    "assignedRider" to userName,
                                    "assignedBike" to getUserBike(),
                                    "batteryLocation" to "In Motion",
                                    "offTime" to Timestamp.now(),
                                    "traces" to traces
                                )
                            ).await()
                        }

                        // UI updates
                        loadUserStatus()
                        val onGrammar = if (selectedOnBatteries.size == 1) "y" else "ies"
                        val offGrammar = if (selectedOffloadBatteries.size == 1) "y" else "ies"
                        Utility.postTrace("Performed a battery swap by offloading ${selectedOffloadBatteries.size} batter$offGrammar (${selectedOffloadBatteries.joinToString(", ")}) and loading ${selectedOnBatteries.size} batter$onGrammar (${selectedOnBatteries.joinToString(", ")}).")
                        Toast.makeText(requireContext(), "Battery swap successful", Toast.LENGTH_SHORT).show()
                        loadAvailableBatteriesAsCheckboxes()

                        binding.batterySwapPBar.visibility = View.GONE
                        binding.btnSwapBatteries.visibility = View.VISIBLE

                    } catch (e: Exception) {
                        binding.batterySwapPBar.visibility = View.GONE
                        binding.btnSwapBatteries.visibility = View.VISIBLE
                        Toast.makeText(requireContext(), "Error swapping batteries: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            create().apply {
                window?.setBackgroundDrawableResource(R.drawable.rounded_black)
                show()
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.GREEN)
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.RED)
            }
        }
    } // function is updated


    // this function knows if we are clocked in, not clocked in, or error
    private fun loadUserStatus() {
        if (!isAdded || view == null || _binding == null) return
        val binding = _binding!!
        binding.ClockInsProgressBar.visibility = View.VISIBLE

        val db = FirebaseFirestore.getInstance()
        val generalRef = db.collection("general").document("general_variables")

        generalRef.get().addOnSuccessListener { companyDoc ->
            val companyStatus = companyDoc.getString("companyState") ?: "Unknown"
            if (!isAdded || view == null || _binding == null) return@addOnSuccessListener
            val binding = _binding!!

            if (companyStatus == "Paused") {
                binding.ClockInsProgressBar.visibility = View.GONE
                binding.btnOperationsPaused.visibility = View.VISIBLE
                return@addOnSuccessListener
            }

            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Toast.makeText(requireContext(), "No user is logged in.", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            val userId = currentUser.uid
            val userRef = db.collection("users").document(userId)

            userRef.get().addOnSuccessListener { userDoc ->
                if (!userDoc.exists() || !isAdded || view == null || _binding == null) return@addOnSuccessListener
                val binding = _binding!!
                binding.forRiders.visibility = View.VISIBLE

                val authEmail = currentUser.email ?: return@addOnSuccessListener
                db.collection("users").whereEqualTo("email", authEmail).get()
                    .addOnSuccessListener { querySnapshot ->
                        if (!isAdded || view == null || _binding == null) return@addOnSuccessListener
                        val binding = _binding!!
                        binding.ClockInsProgressBar.visibility = View.GONE

                        val userData = querySnapshot.documents.firstOrNull() ?: return@addOnSuccessListener
                        val userName = userData.getString("userName") ?: return@addOnSuccessListener
                        val isClockedIn = userData.getBoolean("isClockedIn") == true
                        val isWorkingOnSunday = userData.getBoolean("isWorkingOnSunday") == true
                        derivedUserName = userName

                        // Fetch batteries from the correct location: batteries/{uniqueID}
                        db.collection("batteries")
                            .whereEqualTo("assignedRider", userName)
                            .get()
                            .addOnSuccessListener { batterySnapshot ->
                                if (!isAdded || view == null || _binding == null) return@addOnSuccessListener
                                val binding = _binding!!

                                val hasAssignedBatteries = !batterySnapshot.isEmpty

                                if (hasAssignedBatteries) {
                                    // Rider has batteries
                                    binding.clockInDiv.visibility = View.GONE
                                    Toast.makeText(requireContext(), "You are clocked in", Toast.LENGTH_SHORT).show()

                                    if (!isClockedIn) {
                                        // Rider has batteries but is not clocked in
                                        binding.btnManualBatteryDrop.visibility = View.VISIBLE
                                        binding.clockedInDiv.visibility = View.GONE
                                        Toast.makeText(requireContext(), "Error on previous clockout detected.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        binding.clockedInDiv.visibility = View.VISIBLE
                                    }
                                } else {
                                    // Rider has no batteries
                                    if (isClockedIn) {
                                        binding.clockedInDiv.visibility = View.VISIBLE
                                        binding.clockInDiv.visibility = View.GONE
                                        Toast.makeText(requireContext(), "You are clocked in without batteries. Load some.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        // Not clocked in
                                        val calendar = Calendar.getInstance()
                                        val isSunday = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

                                        binding.btnClockedOut.visibility = View.GONE
                                        binding.btnClockIn.visibility = if (isSunday && !isWorkingOnSunday) View.GONE else View.VISIBLE
                                        binding.btnNoWorkingOnSunday.visibility = if (isSunday && !isWorkingOnSunday) View.VISIBLE else View.GONE

                                        binding.clockInDiv.visibility = View.VISIBLE
                                        binding.clockedInDiv.visibility = View.GONE
                                    }
                                }
                            }
                            .addOnFailureListener {
                                if (!isAdded || view == null || _binding == null) return@addOnFailureListener
                                _binding!!.ClockInsProgressBar.visibility = View.GONE
                                Toast.makeText(requireContext(), "Failed to fetch batteries: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener {
                        if (!isAdded || view == null || _binding == null) return@addOnFailureListener
                        Toast.makeText(requireContext(), "Failed to get user data: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }.addOnFailureListener {
                if (!isAdded || view == null || _binding == null) return@addOnFailureListener
                Toast.makeText(requireContext(), "Failed to fetch user data.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadBikes() /*and destinations*/ {
        val db = FirebaseFirestore.getInstance()
        val bikesRef = db.collection("general").document("general_variables")

        // Fetch bikes
        /*ONLY*/
        bikesRef.get()
            .addOnSuccessListener { document ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                val bikesMap = document.get("bikes") as? Map<String, Map<String, Any>> ?: emptyMap()
                val bikePlates = mutableListOf<String>()
                val disabledBikes = mutableSetOf<String>()

                for ((plate, bikeData) in bikesMap) {
                    val isAssigned = (bikeData as? Map<*, *>)?.get("isAssigned") as? Boolean ?: false
                    bikePlates.add(plate)
                    if (isAssigned) {
                        disabledBikes.add(plate)
                    }
                }

                // Custom Adapter
                val adapter = object : ArrayAdapter<String>(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    bikePlates
                ) {
                    override fun isEnabled(position: Int): Boolean {
                        return bikePlates[position] !in disabledBikes
                    }

                    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getDropDownView(position, convertView, parent)
                        val textView = view as TextView
                        val name = bikePlates[position]

                        when (name) {
                            in disabledBikes -> textView.setTextColor(Color.GRAY)
                            //else -> textView.setTextColor(Color.BLACK)/**/
                        }

                        return view
                    }
                }

                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.bikeToClockIn.adapter = adapter

                // Find the first enabled bike
                val firstEnabledIndex = bikePlates.indexOfFirst { it !in disabledBikes }
                if (firstEnabledIndex != -1) {
                    binding.bikeToClockIn.setSelection(firstEnabledIndex)

                    binding.bikeToClockIn.isEnabled = true
                    binding.btnClockIn.isEnabled = true
                    binding.btnClockIn.setBackgroundColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
                    )
                    binding.youCantClockIn.visibility = View.GONE
                } else {
                    // All bikes are assigned (disabled)
                    Toast.makeText(requireContext(), "All bikes are currently assigned", Toast.LENGTH_LONG).show()
                    binding.bikeToClockIn.isEnabled = false
                    binding.btnClockIn.isEnabled = false
                    binding.btnClockIn.setBackgroundColor(Color.LTGRAY)
                    binding.youCantClockIn.visibility = View.VISIBLE
                }


            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to load bikes: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        // Fetch destinations
        bikesRef.get()
            .addOnSuccessListener { document ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                val destinationsList = document.get("destinations") as? List<String> ?: emptyList()

                if (destinationsList.isEmpty()) {
                    Toast.makeText(requireContext(), "No destinations found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val sortedDestinations = destinationsList.sorted() // Sort alphabetically

                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortedDestinations)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.batterySwapDestination.adapter = adapter
                binding.batteryChargeDestination.adapter = adapter
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to load destinations: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    fun getUserBike(): String {
        return userBike
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(fetchRunnable)
        _binding = null
    }

}