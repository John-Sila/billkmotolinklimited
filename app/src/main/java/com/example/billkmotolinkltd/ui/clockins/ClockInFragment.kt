package com.example.billkmotolinkltd.ui.clockins

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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentClockinsBinding
import com.example.billkmotolinkltd.ui.Utility
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
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
            handleAssignBatteries()
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

                if (userDoc != null) {
                    val userRef = db.collection("users").document(userDoc.id)

                    // 1. SET CURRENT BIKE TO NONE
                    userRef.update("currentBike", "None")
                        .addOnSuccessListener {
                            context?.let { ctx ->
                                Toast.makeText(ctx, "Current user bike updated.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            context?.let { ctx ->
                                Toast.makeText(ctx, "Failed to update current bike: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }

                    // drop bikes and batteries
                    val userName = userDoc.getString("userName")
                    if (userName.isNullOrEmpty()) {
                        context?.let { ctx ->
                            Toast.makeText(ctx, "User not found. Try Logging out and in again.", Toast.LENGTH_LONG).show()
                        }
                        return@addOnSuccessListener
                    }

                    val generalRef = db.collection("general").document("general_variables")
                    db.runTransaction { transaction ->
                        val generalSnapshot = transaction.get(generalRef)
                        val batteriesMap = generalSnapshot.get("batteries") as? MutableMap<String, MutableMap<String, Any>>
                            ?: mutableMapOf()

                        /* 2. DROP BATTERIES */
                        for ((batteryKey, batteryData) in batteriesMap) {
                            if (batteryData["assignedRider"] == userName) {
                                batteryData["assignedRider"] = "None"
                                batteryData["assignedBike"] = "None"
                                batteryData["offTime"] = Timestamp.now()
                                batteryData["batteryLocation"] = location
                                batteriesMap[batteryKey] = batteryData
                            }
                        }
                        transaction.update(generalRef, "batteries", batteriesMap)

                        null
                    }
                        .addOnSuccessListener {
                            // 3. DROP BIKE
                            generalRef.get().addOnSuccessListener { document ->
                                val bikesMap = document.get("bikes") as? MutableMap<String, MutableMap<String, Any>> ?: mutableMapOf()

                                if (bikesMap.containsKey(userBike)) {
                                    bikesMap[userBike]?.apply {
                                        set("isAssigned", false)
                                        set("rider", "None")
                                    }

                                    generalRef.update("bikes", bikesMap)
                                        .addOnSuccessListener {
                                            context?.let { ctx ->
                                                Toast.makeText(ctx, "Bike updated.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            context?.let { ctx ->
                                                Toast.makeText(ctx, "Failed to drop bikes and batteries. Please retry: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                } else {
                                    context?.let { ctx ->
                                        Toast.makeText(ctx, "$userBike is not in database.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

                            context?.let { ctx ->
                                Toast.makeText(ctx, "Dropping completed.", Toast.LENGTH_SHORT).show()
                            }
                            if (_binding != null) {
                                binding.btnManualBatteryDrop.visibility = View.GONE
                                loadUserStatus()
                            }
                        }
                        .addOnFailureListener { e ->
                            context?.let { ctx ->
                                Toast.makeText(ctx, "Dropping failed. Retry failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    context?.let { ctx ->
                        Toast.makeText(ctx, "User not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                context?.let { ctx ->
                    Toast.makeText(ctx, "Failed to fetch user: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

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
        // Step 2: Determine the count and format the names
        val batteryCount = selectedBatteries.size
        val batteryNames = selectedBatteries.joinToString(", ") // Join names with a comma

        if (selectedBatteries.isEmpty()) {
            Toast.makeText(requireContext(), "Select at least one battery", Toast.LENGTH_SHORT).show()
            return
        }

        val pBar = _binding?.batteryChargePBar
        val submitBtn = _binding?.btnChargeBatteries
        pBar?.visibility = View.VISIBLE
        submitBtn?.visibility = View.GONE

        binding.clockInPBar.visibility = View.VISIBLE
        binding.btnClockIn.visibility = View.GONE

        val db = FirebaseFirestore.getInstance()
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: return
        // Fetch userName from users collection
        db.collection("users").whereEqualTo("email", currentUserEmail).get()
            .addOnSuccessListener { querySnapshot ->
                val userDoc = querySnapshot.documents.firstOrNull()

                val generalRef = db.collection("general").document("general_variables")
                // action updates batteries
                db.runTransaction { transaction ->
                    // 1. Update selected bike's isAssigned = true
                    val generalSnapshot = transaction.get(generalRef)
                    val batteriesMap = generalSnapshot.get("batteries") as? MutableMap<String, MutableMap<String, Any>> ?: mutableMapOf()

                    for ((batteryKey, batteryData) in batteriesMap) {
                        if (selectedBatteries.contains(batteryData["batteryName"])) {
                            batteryData["batteryLocation"] = "Charging at $selectedLocation"
                            batteryData["assignedBike"] = "None"
                            batteryData["assignedRider"] = "None"
                            batteryData["offTime"] = Timestamp.now()
                            batteriesMap[batteryKey] = batteryData
                        }
                    }

                    transaction.update(generalRef, "batteries", batteriesMap)
                }
                    .addOnSuccessListener {
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
                    .addOnFailureListener { e ->
                        pBar?.visibility = View.GONE
                        submitBtn?.visibility = View.VISIBLE
                        Toast.makeText(requireContext(), "Error assigning batteries: ${e.message}", Toast.LENGTH_SHORT).show()
                    }

                // action updates user to be marked as charging a battery
                db.collection("users").document(userDoc?.id.toString())
                    .update(
                        mapOf(
                            "isCharging" to true
                        )
                    )
                    .addOnSuccessListener {
                        // Toast.makeText(requireContext(), "Bike and clock-in time updated!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Failed to update: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                binding.clockInPBar.visibility = View.GONE
                binding.btnClockIn.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "Failed to fetch user: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

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
        // Step 2: Determine the count and format the names
        val batteryCount = selectedBatteries.size
        val batteryNames = selectedBatteries.joinToString(", ") // Join names with a comma

        if (selectedBatteries.isEmpty()) {
            Toast.makeText(requireContext(), "Select at least one battery", Toast.LENGTH_SHORT).show()
            return
        }
        val pBar = _binding?.batteryReloadPBar
        val submitBtn = _binding?.btnReloadBatteries
        pBar?.visibility = View.VISIBLE
        submitBtn?.visibility = View.GONE

        val db = FirebaseFirestore.getInstance()
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: return
        // Fetch userName from users collection
        db.collection("users").whereEqualTo("email", currentUserEmail).get()
            .addOnSuccessListener { querySnapshot ->
                val userDoc = querySnapshot.documents.firstOrNull()
                val username = userDoc?.getString("userName") ?: "Unidentified"
                val bike = userDoc?.getString("currentBike") ?: "Unidentified"

                val generalRef = db.collection("general").document("general_variables")
                // action updates batteries
                db.runTransaction { transaction ->
                    // 1. Update selected bike's isAssigned = true
                    val generalSnapshot = transaction.get(generalRef)
                    val batteriesMap = generalSnapshot.get("batteries") as? MutableMap<String, MutableMap<String, Any>> ?: mutableMapOf()

                    for ((batteryKey, batteryData) in batteriesMap) {
                        if (selectedBatteries.contains(batteryData["batteryName"])) {
                            batteryData["batteryLocation"] = "In Motion"
                            batteryData["assignedRider"] = username
                            batteryData["assignedBike"] = bike
                            batteryData["offTime"] = Timestamp.now()
                            batteriesMap[batteryKey] = batteryData
                        }
                    }

                    transaction.update(generalRef, "batteries", batteriesMap)
                }
                    .addOnSuccessListener {
                        loadBikes()
                        loadAvailableBatteriesAsCheckboxes()
                        loadUserStatus()

                        val grammar = if (batteryCount == 1) "y" else "ies"
                        lifecycleScope.launch {
                            Utility.postTrace("Loaded $batteryCount batter$grammar ($batteryNames).")
                        }

                        pBar?.visibility = View.GONE
                        submitBtn?.visibility = View.VISIBLE
                        Toast.makeText(requireContext(), "Loaded.", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        pBar?.visibility = View.GONE
                        submitBtn?.visibility = View.VISIBLE
                        Toast.makeText(requireContext(), "Error loading batteries: ${e.message}", Toast.LENGTH_SHORT).show()
                    }

                // action updates user to be marked as charging a battery
                db.collection("users").document(userDoc?.id.toString())
                    .update(
                        mapOf(
                            "isCharging" to false
                        )
                    )
                    .addOnSuccessListener {
                        // Toast.makeText(requireContext(), "Bike and clock-in time updated!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Failed to update: ${e.message}", Toast.LENGTH_SHORT).show()
                    }

            }
            .addOnFailureListener { e ->
                binding.clockInPBar.visibility = View.GONE
                binding.btnClockIn.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "Failed to fetch user: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /*clock in begins here*/
    private fun handleAssignBatteries() {
        if (!isAdded || view == null || _binding == null) return

        if (derivedUserName == "") {
            Toast.makeText(requireContext(), "No username.", Toast.LENGTH_SHORT).show()
            return
        }

        // Move UI updates to main thread
        binding.clockInPBar.visibility = View.VISIBLE
        binding.btnClockIn.visibility = View.GONE

        // Execute heavy operations in coroutine
        lifecycleScope.launch {
            try {
                // Validate input on background thread
                val inputMileage = withContext(Dispatchers.Default) {
                    binding.inputMileage.text.toString().trim()
                }

                val isValidNumber = inputMileage.matches(Regex("^\\d+(\\.\\d+)?$"))
                if (!isValidNumber) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Invalid mileage.", Toast.LENGTH_SHORT).show()
                        resetUI()
                    }
                    return@launch
                }
                val mileage = inputMileage.toDouble()

                val selectedBike = withContext(Dispatchers.Main) {
                    _binding?.bikeToClockIn?.selectedItem as? String
                }
                if (selectedBike.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Please select a bike", Toast.LENGTH_SHORT).show()
                        resetUI()
                    }
                    return@launch
                }
                if (selectedBike.contains("DUMMY", ignoreCase = true)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            "Dummy bikes are only used by the IT Department.",
                            Toast.LENGTH_LONG
                        ).show()
                        resetUI()
                    }
                    return@launch
                }

                // Firebase operations
                val db = FirebaseFirestore.getInstance()
                val docRef = db.collection("general").document("general_variables")

                // First Firebase call
                val document = try {
                    docRef.get().await()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error checking bike status: ${e.message}", Toast.LENGTH_SHORT).show()
                        resetUI()
                    }
                    return@launch
                }

                if (!document.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "General variables not found", Toast.LENGTH_SHORT).show()
                        resetUI()
                    }
                    return@launch
                }

                val bikes = document.get("bikes") as? Map<String, Map<String, Any>>
                val isAssigned = bikes?.get(selectedBike)?.get("isAssigned") as? Boolean

                if (isAssigned == true) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error: $selectedBike is already assigned", Toast.LENGTH_SHORT).show()
                        loadAvailableBatteriesAsCheckboxes()
                        resetUI()
                    }
                    return@launch
                }

                // Collect checked battery names
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

                val allAreRent = selectedBatteries.all { it.startsWith("RENT", ignoreCase = true) }
                if (allAreRent) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(),
                            "Cannot proceed: You have to select at least one internal (BK-) battery.",
                            Toast.LENGTH_LONG).show()
                        resetUI()
                    }
                    return@launch
                }

                val hasDummyBattery = selectedBatteries.any { it.contains("DUMMY", ignoreCase = true) }
                if (hasDummyBattery) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            "Dummy batteries are only used by the IT Department.",
                            Toast.LENGTH_LONG
                        ).show()
                        resetUI()
                    }
                    return@launch
                }

                // Check battery assignments
                val batteriesMap = document.get("batteries") as? Map<String, Map<String, Any>> ?: emptyMap()
                val conflictingBattery = batteriesMap.values.firstOrNull { battery ->
                    val name = battery["batteryName"] as? String
                    val rider = battery["assignedRider"] as? String
                    name != null && selectedBatteries.contains(name) && rider != null && rider != "None"
                }

                if (conflictingBattery != null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(),
                            "One or more selected batteries are already assigned.",
                            Toast.LENGTH_LONG).show()
                        resetUI()
                    }
                    return@launch
                }

                // Show confirmation dialog on main thread
                withContext(Dispatchers.Main) {
                    showConfirmationDialog(db, selectedBike, mileage, selectedBatteries)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    resetUI()
                }
            }
        }
    }

    private fun resetUI() {
        if (!isAdded || view == null || _binding == null) return
        binding.clockInPBar.visibility = View.GONE
        binding.btnClockIn.visibility = View.VISIBLE
    }

    private fun showConfirmationDialog(
        db: FirebaseFirestore,
        selectedBike: String,
        mileage: Double,
        selectedBatteries: List<String>
    ) {
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
            lifecycleScope.launch {
                try {
                    binding.clockInPBar.visibility = View.VISIBLE
                    binding.btnClockIn.visibility = View.GONE

                    val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: return@launch

                    // Second Firebase call
                    val querySnapshot = try {
                        db.collection("users").whereEqualTo("email", currentUserEmail).get().await()
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Failed to fetch user: ${e.message}", Toast.LENGTH_SHORT).show()
                            resetUI()
                        }
                        return@launch
                    }

                    val userDoc = querySnapshot.documents.firstOrNull()
                    if (userDoc == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                            resetUI()
                        }
                        return@launch
                    }

                    val userId = userDoc.id
                    val userName = userDoc.getString("userName")
                    if (userName.isNullOrEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                            resetUI()
                        }
                        return@launch
                    }

                    // Update user document
                    try {
                        db.collection("users").document(userId)
                            .update(
                                mapOf(
                                    "currentBike" to selectedBike,
                                    "clockInTime" to Timestamp.now(),
                                    "clockinMileage" to mileage,
                                    "isClockedIn" to true
                                )
                            ).await()
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Failed to update: ${e.message}", Toast.LENGTH_SHORT).show()
                            resetUI()
                        }
                        return@launch
                    }

                    // Transaction
                    try {
                        val batteryCount = selectedBatteries.size
                        val batteryNames = selectedBatteries.joinToString(", ")

                        db.runTransaction { transaction ->
                            val generalRef = db.collection("general").document("general_variables")
                            val generalSnapshot = transaction.get(generalRef)

                            // Update bikes
                            val bikesMap = generalSnapshot.get("bikes") as? MutableMap<String, Any> ?: mutableMapOf()
                            val selectedBikeData = bikesMap[selectedBike] as? MutableMap<String, Any> ?: mutableMapOf()
                            selectedBikeData["isAssigned"] = true
                            selectedBikeData["assignedRider"] = derivedUserName
                            bikesMap[selectedBike] = selectedBikeData
                            transaction.update(generalRef, "bikes", bikesMap)

                            // Update batteries
                            val batteriesMapTxn = generalSnapshot.get("batteries") as? MutableMap<String, MutableMap<String, Any>> ?: mutableMapOf()
                            for ((batteryKey, batteryData) in batteriesMapTxn) {
                                if (selectedBatteries.contains(batteryData["batteryName"])) {
                                    batteryData["assignedRider"] = userName
                                    batteryData["assignedBike"] = selectedBike
                                    batteryData["batteryLocation"] = "In Motion"
                                    batteryData["offTime"] = Timestamp.now()
                                    batteriesMapTxn[batteryKey] = batteryData
                                }
                            }
                            transaction.update(generalRef, "batteries", batteriesMapTxn)
                        }.await()

                        withContext(Dispatchers.Main) {
                            binding.clockInDiv.visibility = View.GONE
                            loadBikes()
                            loadAvailableBatteriesAsCheckboxes()
                            loadUserStatus()

                            val grammar = if (batteryCount == 1) "y" else "ies"
                            Utility.postTrace("Clocked in with $batteryCount batter$grammar ($batteryNames) and bike $selectedBike.")
                            val roles = listOf("Admin", "CEO", "Systems, IT")
                            Utility.notifyAdmins("$userName just clocked in.", "Clockins", roles)

                            Toast.makeText(requireContext(), "Batteries assigned successfully", Toast.LENGTH_SHORT).show()
                            resetUI()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Error assigning batteries: ${e.message}", Toast.LENGTH_SHORT).show()
                            resetUI()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        resetUI()
                    }
                }
            }
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
    /*Clock ins end here*/






    /*Loading batteries starts here*/
    private fun loadAvailableBatteriesAsCheckboxes() {
        val db = FirebaseFirestore.getInstance()
        val generalRef = db.collection("general").document("general_variables")

        // get unassigned batteries for clocking in and loading
        generalRef.get()
            .addOnSuccessListener { document ->
            if (!isAdded || view == null || _binding == null) return@addOnSuccessListener

            val batteriesMap = document.get("batteries") as? Map<String, Map<String, Any>> ?: emptyMap()

            val container = _binding?.batteryCheckboxContainer
            val containerLoad = _binding?.batteriesToOnLoadCheckboxContainer
            container?.removeAllViews()
            containerLoad?.removeAllViews()

            val sortedBatteries = batteriesMap.values.sortedBy { it["batteryName"] as? String ?: "" }

            val selectedCheckBoxes = mutableListOf<CheckBox>()
            for (batteryData in sortedBatteries) {
                val assignedRider = batteryData["assignedRider"] as? String ?: "None"
                val batteryName = batteryData["batteryName"] as? String ?: continue

                if (assignedRider == "None") {
                    // clock in batteries
                    val checkBox1 = CheckBox(requireContext()).apply {
                        text = batteryName
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        setOnCheckedChangeListener { buttonView, isChecked ->
                            if (isChecked) {
                                selectedCheckBoxes.add(this)

                                if (selectedCheckBoxes.size > 2) {
                                    // Uncheck the first selected checkbox
                                    val firstChecked = selectedCheckBoxes.removeAt(0)
                                    firstChecked.isChecked = false
                                }
                            } else {
                                selectedCheckBoxes.remove(this)
                            }
                        }
                    }

                    // load batteries //// both these and the above have to be r & b None & None
                    val checkBox2 = CheckBox(requireContext()).apply {
                        text = batteryName
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        setOnCheckedChangeListener { buttonView, isChecked ->
                            if (isChecked) {
                                selectedCheckBoxes.add(this)

                                if (selectedCheckBoxes.size > 2) {
                                    // Uncheck the first selected checkbox
                                    val firstChecked = selectedCheckBoxes.removeAt(0)
                                    firstChecked.isChecked = false
                                }
                            } else {
                                selectedCheckBoxes.remove(this)
                            }
                        }
                    }

                    container?.addView(checkBox1)
                    containerLoad?.addView(checkBox2)
                }
            }

            if (container?.childCount == 0) {
                // no batteries. we are full
                Toast.makeText(requireContext(), "No unassigned batteries found", Toast.LENGTH_SHORT).show()
                binding.noBatteriesToSwap.visibility = View.VISIBLE
                binding.batterySwapDiv.visibility = View.GONE
                binding.youCantClockIn.visibility = View.VISIBLE
                binding.batteriesToOnLoadCheckboxContainer.visibility = View.GONE
            }
        }
            .addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Failed to load batteries: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: return

        // Fetch userName from users collection
        db.collection("users").whereEqualTo("email", currentUserEmail).get()
            .addOnSuccessListener { querySnapshot ->
                val userDoc = querySnapshot.documents.firstOrNull()
                val userName = userDoc?.getString("userName")
                val isClockedIn = userDoc?.getBoolean("isClockedIn") == true

                if (userName.isNullOrEmpty()) {
                    binding.clockInPBar.visibility = View.GONE
                    // binding.btnClockIn.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                else {
                    generalRef.get().addOnSuccessListener { document ->
                        if (!isAdded || view == null || _binding == null) return@addOnSuccessListener

                        val batteriesMap =
                            document.get("batteries") as? Map<String, Map<String, Any>>
                                ?: emptyMap()

                        val container = _binding?.batteriesToOffloadCheckboxContainer
                        container?.removeAllViews()

                        // load batteries that are assigned to us so we can swap them
                        val sortedBatteries = batteriesMap.values.sortedBy { it["batteryName"] as? String ?: "" }
                        for (batteryData in sortedBatteries)  {
                            val assignedRider = batteryData["assignedRider"] as? String ?: "None"
                            val batteryName = batteryData["batteryName"] as? String ?: continue
                            val assignedBike = batteryData["assignedBike"] as? String ?: "None"
                            val location = batteryData["batteryLocation"] as? String ?: ""
                            if (assignedRider == userName) {
                                val checkBox = CheckBox(requireContext()).apply {
                                    text = batteryName
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    )
                                }
                                container?.addView(checkBox)
                                userBike = assignedBike
                            }
                        }
                        if (container?.childCount == 0) {
                            if (!isAdded || view == null || _binding == null) return@addOnSuccessListener
                            binding.batterySwapDiv.visibility = View.GONE
                            binding.batteryChargeDiv.visibility = View.GONE
                        }
                        else {
                            binding.batterySwapDiv.visibility = View.VISIBLE
                            binding.batteryChargeDiv.visibility = View.VISIBLE
                        }

                        // know if we can load more batteries
                        if ((container?.childCount == 1 || container?.childCount == 0) && isClockedIn) {
                            if (!isAdded || view == null || _binding == null) return@addOnSuccessListener
                            binding.batteryReloadDiv.visibility = View.VISIBLE

                            // load batteries not assigned to anyone so we can add for us
                            val reloadContainer = _binding?.batteriesToReload
                            reloadContainer?.removeAllViews()
                            val selectedCheckBoxes = mutableListOf<CheckBox>()
                            for (batteryData in sortedBatteries) {
                                val batteryName = batteryData["batteryName"] as? String ?: continue
                                val assignedRider = batteryData["assignedRider"] as? String ?: "None"

                                if (assignedRider == "None") {
                                    val checkBox = CheckBox(requireContext()).apply {
                                        text = batteryName
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT
                                        )

                                        setOnCheckedChangeListener { buttonView, isChecked ->
                                            if (isChecked) {
                                                selectedCheckBoxes.add(this)
                                                if (selectedCheckBoxes.size > 1) {
                                                    // Uncheck the first selected checkbox
                                                    val firstChecked = selectedCheckBoxes.removeAt(0)
                                                    firstChecked.isChecked = false
                                                }
                                            } else {
                                                selectedCheckBoxes.remove(this)
                                                Toast.makeText(requireContext(), "You can only load one battery at a time.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    reloadContainer?.addView(checkBox)
                                }
                            }
                            if (reloadContainer?.childCount == 0) {
                                if (!isAdded || _binding == null) return@addOnSuccessListener
                                binding.batteryReloadDiv.visibility = View.GONE
                            } else {
                                binding.batteryReloadDiv.visibility = View.VISIBLE
                            }
                        }
                        else {
                            if (!isAdded || view == null || _binding == null) return@addOnSuccessListener
                            binding.batteryReloadDiv.visibility = View.GONE
                        }

                        val chargerContainer = _binding?.batteriesToCharge
                        chargerContainer?.removeAllViews()

                        // load batteries that are assigned to us so we can charge
                        for ((_, batteryData) in batteriesMap) {
                            val batteryName = batteryData["batteryName"] as? String ?: continue
                            val location = batteryData["batteryLocation"] as? String ?: ""
                            val assignedRider = batteryData["assignedRider"] as? String ?: "None"
                            // if (location.substringBefore(" ") == "Charging") continue

                            if (assignedRider == userName) {
                                val checkBox = CheckBox(requireContext()).apply {
                                    text = batteryName
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    )
                                }
                                chargerContainer?.addView(checkBox)
                            }
                        }
                    }
                }
            }
    }

    /*loading batteries ends here*/




    private fun swapBatteries() {
        val selectedLocation = _binding?.batterySwapDestination?.selectedItem as? String
        val offContainer = _binding?.batteriesToOffloadCheckboxContainer
        val onContainer = _binding?.batteriesToOnLoadCheckboxContainer

        val selectedOffloadBatteries = mutableListOf<String>()
        val selectedOnBatteries = mutableListOf<String>()

        offContainer?.let {
            for (i in 0 until it.childCount) {
                val view = it.getChildAt(i)
                if (view is CheckBox && view.isChecked) {
                    selectedOffloadBatteries.add(view.text.toString())
                }
            }
        }

        onContainer?.let {
            for (i in 0 until it.childCount) {
                val view = it.getChildAt(i)
                if (view is CheckBox && view.isChecked) {
                    selectedOnBatteries.add(view.text.toString())
                }
            }
        }

        val onBatteriesCount = selectedOnBatteries.size
        val onBatteryNames = selectedOnBatteries.joinToString(", ")
        val offBatteriesCount = selectedOffloadBatteries.size
        val offBatteryNames = selectedOffloadBatteries.joinToString(", ")

        if (selectedOffloadBatteries.isEmpty()) {
            Toast.makeText(requireContext(), "You are offloading nothing!", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedOnBatteries.isEmpty()) {
            Toast.makeText(requireContext(), "You haven't loaded a battery!", Toast.LENGTH_SHORT).show()
            return
        }

        val alertDialog = AlertDialog.Builder(requireContext())
        val title = SpannableString("Swap Battery").apply {
            setSpan(ForegroundColorSpan(Color.GREEN), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val message = SpannableString("Confirm action.").apply {
            setSpan(ForegroundColorSpan(Color.GRAY), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        alertDialog.setTitle(title)
        alertDialog.setMessage(message)
        alertDialog.setIcon(R.drawable.success)

        alertDialog.setPositiveButton("Swap") { _, _ ->
            binding.batterySwapPBar.visibility = View.VISIBLE
            binding.btnSwapBatteries.visibility = View.GONE

            val db = FirebaseFirestore.getInstance()
            val generalRef = db.collection("general").document("general_variables")
            val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: return@setPositiveButton

            // Step 1: Get userName from email
            db.collection("users").whereEqualTo("email", currentUserEmail).get()
                .addOnSuccessListener { snapshot ->
                    val userDoc = snapshot.documents.firstOrNull()
                    val userName = userDoc?.getString("userName")
                    if (userName.isNullOrEmpty()) {
                        binding.batterySwapPBar.visibility = View.GONE
                        binding.btnSwapBatteries.visibility = View.VISIBLE
                        Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    // Step 2: Run ONE transaction
                    db.runTransaction { transaction ->
                        val generalSnapshot = transaction.get(generalRef)
                        val batteriesMap = generalSnapshot.get("batteries") as? MutableMap<String, MutableMap<String, Any>>
                            ?: mutableMapOf()

                        for ((key, data) in batteriesMap) {
                            val batteryName = data["batteryName"] as? String ?: continue

                            if (batteryName in selectedOffloadBatteries) {
                                data["assignedRider"] = "None"
                                data["assignedBike"] = "None"
                                data["batteryLocation"] = selectedLocation ?: "Unknown"
                                data["offTime"] = Timestamp.now()

                                // Utility.postTrace("Dropped a battery ($batteryName) during a swap at $selectedLocation.")
                            }

                            if (batteryName in selectedOnBatteries) {
                                data["assignedRider"] = userName
                                data["assignedBike"] = getUserBike()
                                data["batteryLocation"] = "In Motion"
                                data["offTime"] = Timestamp.now()

                                // Utility.postTrace("Loaded a battery ($batteryName) during a swap at $selectedLocation.")
                            }

                            batteriesMap[key] = data
                        }

                        transaction.update(generalRef, "batteries", batteriesMap)
                    }.addOnSuccessListener {
                        loadUserStatus()
                        val onGrammar = if (onBatteriesCount == 1) "y" else "ies"
                        val offGrammar = if (offBatteriesCount == 1) "y" else "ies"
                        lifecycleScope.launch {
                            Utility.postTrace("Performed a battery swap by offloading $offBatteriesCount batter${offGrammar} ($offBatteryNames) and loading $onBatteriesCount batter$onGrammar ($onBatteryNames).")
                        }
                        Toast.makeText(requireContext(), "Battery swap successful", Toast.LENGTH_SHORT).show()
                        loadAvailableBatteriesAsCheckboxes()
                        binding.batterySwapPBar.visibility = View.GONE
                        binding.btnSwapBatteries.visibility = View.VISIBLE
                    }.addOnFailureListener { e ->
                        binding.batterySwapPBar.visibility = View.GONE
                        binding.btnSwapBatteries.visibility = View.VISIBLE
                        Toast.makeText(requireContext(), "Error swapping batteries: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }.addOnFailureListener { e ->
                    binding.batterySwapPBar.visibility = View.GONE
                    binding.btnSwapBatteries.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "Failed to fetch user: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        alertDialog.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
        alertDialog.create().apply {
            window?.setBackgroundDrawableResource(R.drawable.rounded_black)
            show()

            // Change button text colors after showing
            getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.GREEN)  // confirm button
            getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.RED)    // cancel button
        }
    }

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
                        val clockouts = userData.get("clockouts") as? Map<*, *>
                        derivedUserName = userName

                        generalRef.get().addOnSuccessListener { generalDoc ->
                            if (!isAdded || view == null || _binding == null) return@addOnSuccessListener
                            val binding = _binding!!

                            val batteriesMap = generalDoc.get("batteries") as? Map<*, *> ?: emptyMap<Any, Any>()
                            val hasAssignedBatteries = batteriesMap.any { (_, batteryData) ->
                                val assigned = (batteryData as? Map<*, *>)?.get("assignedRider")
                                assigned == userName
                            }

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
                        }.addOnFailureListener {
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
                    binding.btnClockIn.setBackgroundColor(Color.GREEN)
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