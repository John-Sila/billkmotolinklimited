package com.billkmotolink.ltd.ui.assets

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.billkmotolink.ltd.R
import com.billkmotolink.ltd.databinding.FragmentManageAssetsBinding
import com.billkmotolink.ltd.ui.globalDateKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.toString

class AssetsFragment: Fragment() {

    private var _binding: FragmentManageAssetsBinding? = null

    // This property is only valid between onCreateView and onDestroyView ofc.
    private val binding get() = _binding!!
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageAssetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            loadOperations()
            // loadBatteries()
        }

        binding.btnAddBike.setOnClickListener{
            val plateNumber = binding.inputPlateNumber.text.toString().trim()

            if (plateNumber.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a plate number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Add Bike")
            title.setSpan(ForegroundColorSpan(Color.GREEN), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("Confirm action.")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.success)

            alertDialog.setPositiveButton("Confirm") { _, _ ->
                addBike()
            }

            alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
            }

            alertDialog.create().apply {
                window?.setBackgroundDrawableResource(R.drawable.rounded_black)
                show()

                // Change button text colors after showing
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.GREEN)  // confirm button
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.RED)    // cancel button
            }
        }

        binding.btnAddDestination.setOnClickListener{
            val destination = binding.inputBatteryDestination.text.toString().trim()

            if (destination.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a valid location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Add Location")
            title.setSpan(ForegroundColorSpan(Color.GREEN), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("Confirm action.")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.success)

            alertDialog.setPositiveButton("Add") { _, _ ->
                addDestination()
            }

            alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
            }

            alertDialog.create().apply {
                window?.setBackgroundDrawableResource(R.drawable.rounded_black)
                show()

                // Change button text colors after showing
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.GREEN)  // confirm button
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.RED)    // cancel button
            }
        }

        binding.btnDeleteBike.setOnClickListener{

            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Delete Bike")
            title.setSpan(ForegroundColorSpan(Color.RED), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("Confirm action.")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.warn)

            alertDialog.setPositiveButton("Yes") { _, _ ->
                deleteBike()
            }

            alertDialog.setNegativeButton("No") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
            }

            alertDialog.create().apply {
                window?.setBackgroundDrawableResource(R.drawable.rounded_black)
                show()

                // Change button text colors after showing
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.GREEN)  // confirm button
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.RED)    // cancel button
            }
        }

        binding.btnAddBattery.setOnClickListener {
            val batteryName = binding.inputBatteryName.text.toString().trim()
            val selectedDestination = binding.batteryDestination.selectedItem as? String

            if (batteryName.isEmpty() || selectedDestination?.isEmpty() == true) {
                Toast.makeText(requireContext(), "Either the battery name or location is invalid.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val alertDialog = AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Add Battery")
            title.setSpan(ForegroundColorSpan(Color.GREEN), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("Confirm action.")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.success)

            alertDialog.setPositiveButton("Add") { _, _ ->
                addBattery(batteryName, selectedDestination.toString())
            }

            alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
            }

            alertDialog.create().apply {
                window?.setBackgroundDrawableResource(R.drawable.rounded_black)
                show()

                // Change button text colors after showing
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.GREEN)  // confirm button
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.RED)    // cancel button
            }

        }

        binding.btnDeleteBattery.setOnClickListener {
            val batteryName = binding.batteryToDelete.selectedItem as? String

            if (batteryName?.isEmpty() == true) {
                Toast.makeText(requireContext(), "No battery selected.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val alertDialog = AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Delete Battery")
            title.setSpan(ForegroundColorSpan(Color.RED), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("Confirm action.")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.success)

            alertDialog.setPositiveButton("Delete") { _, _ ->
                deleteBattery(batteryName)
            }

            alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
            }

            alertDialog.create().apply {
                window?.setBackgroundDrawableResource(R.drawable.rounded_black)
                show()

                // Change button text colors after showing
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.GREEN)  // confirm button
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.RED)    // cancel button
            }
        }

        binding.btnDeleteDestination.setOnClickListener {
            val destinationName = binding.destinationToDelete.selectedItem as? String

            if (destinationName?.isEmpty() == true) {
                Toast.makeText(requireContext(), "No destination selected.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val alertDialog = AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Delete Destination")
            title.setSpan(ForegroundColorSpan(Color.RED), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("Confirm action.")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.success)

            alertDialog.setPositiveButton("Delete") { _, _ ->
                deleteDestination(destinationName)
            }

            alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
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

    private fun deleteDestination(destinationName: String?) {
        if (destinationName.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Invalid destination", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            _binding.let {
                binding.deleteDestinationProgressBar.visibility = View.VISIBLE
                binding.btnDeleteDestination.visibility = View.GONE
            }
            val db = FirebaseFirestore.getInstance()
            val destinationsRef = db.collection("general").document("general_variables")

            // Remove the destination from the array
            destinationsRef.update("destinations", FieldValue.arrayRemove(destinationName))
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Destination deleted successfully", Toast.LENGTH_SHORT).show()
                    loadBikes() // Refresh UI/spinners
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to delete destination: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
        catch (_: Exception) {

        }
        finally {
            _binding.let {
                binding.deleteDestinationProgressBar.visibility = View.GONE
                binding.btnDeleteDestination.visibility = View.VISIBLE
            }
        }

    }

    private fun deleteBattery(batteryName: String?) {
        if (batteryName.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "No battery selected", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()
        val batteriesRef = db.collection("batteries")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.deleteBatteryProgressBar.visibility = View.VISIBLE
                binding.btnDeleteBattery.visibility = View.GONE

                // Step 1: Find the battery document by name (case-insensitive)
                val snapshot = withContext(Dispatchers.IO) {
                    batteriesRef.whereEqualTo("batteryName", batteryName).get().await()
                }

                if (snapshot.isEmpty) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "$batteryName not found", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Step 2: Delete the document(s)
                withContext(Dispatchers.IO) {
                    val batch = db.batch()
                    for (doc in snapshot.documents) {
                        batch.delete(doc.reference)
                    }
                    batch.commit().await()
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Battery deleted successfully", Toast.LENGTH_SHORT).show()
                    loadBatteries() // refresh list
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                binding.deleteBatteryProgressBar.visibility = View.GONE
                binding.btnDeleteBattery.visibility = View.VISIBLE
            }
        }
    }

    // load bikes, destinations, and company state
    private fun loadOperations() {
        val db = FirebaseFirestore.getInstance()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // --- Company State ---
                val companyDoc = withContext(Dispatchers.IO) { db.collection("general").document("general_variables").get().await() }
                val companyStatus = companyDoc.getString("companyState") ?: "Unknown"

                withContext(Dispatchers.Main) {
                    if (!isAdded || _binding == null) return@withContext
                    when (companyStatus) {
                        "Paused" -> {
                            binding.btnOperationsPaused.visibility = View.VISIBLE
                        }
                        "Continuing" -> {
                            lifecycleScope.launch {
                                loadBikes()
                                loadBatteries()

                                _binding?.let { binding ->
                                    binding.btnAddBike.isEnabled = true
                                    binding.btnAddDestination.isEnabled = true
                                    binding.btnAddBattery.isEnabled = true
                                    binding.btnDeleteDestination.isEnabled = true
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded && _binding != null) {
                        Toast.makeText(requireContext(), "Failed to load bikes: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /*this fetches bikes and destinations*/
    private fun loadBikes() {
        val db = FirebaseFirestore.getInstance()
        val bikesRef = db.collection("general").document("general_variables")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // --- Bikes ---
                val bikesDoc = withContext(Dispatchers.IO) { bikesRef.get().await() }
                if (!isAdded || _binding == null) return@launch

                val bikesMap = bikesDoc.get("bikes") as? Map<String, Map<String, Any>> ?: emptyMap()
                val bikePlates = mutableListOf<String>()
                val disabledBikes = mutableSetOf<String>()

                // Heavy loop moved off UI thread
                withContext(Dispatchers.Default) {
                    for ((plate, bikeData) in bikesMap) {
                        val isAssigned = (bikeData as? Map<*, *>)?.get("isAssigned") as? Boolean ?: false
                        bikePlates.add(plate)
                        if (isAssigned) disabledBikes.add(plate)
                    }
                }

                // Back to Main thread for adapter setup
                withContext(Dispatchers.Main) {
                    if (!isAdded || _binding == null) return@withContext
                    val adapter = object : ArrayAdapter<String>(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        bikePlates
                    ) {
                        override fun isEnabled(position: Int) = bikePlates[position] !in disabledBikes
                        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val view = super.getDropDownView(position, convertView, parent)
                            val textView = view as TextView
                            if (bikePlates[position] in disabledBikes) {
                                textView.setTextColor(Color.GRAY)
                            }
                            return view
                        }
                    }

                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.bikeToManage.adapter = adapter

                    val firstEnabledIndex = bikePlates.indexOfFirst { it !in disabledBikes }
                    if (firstEnabledIndex != -1) {
                        binding.bikeToManage.setSelection(firstEnabledIndex)
                        binding.bikeToManage.isEnabled = true
                        binding.btnDeleteBike.isEnabled = true
                    } else {
                        Toast.makeText(requireContext(), "All bikes are currently assigned and can't be deleted.", Toast.LENGTH_LONG).show()
                        binding.bikeToManage.isEnabled = false
                        binding.btnDeleteBike.isEnabled = false
                        binding.btnDeleteBike.setBackgroundColor(Color.LTGRAY)
                    }
                }

                // --- Destinations ---
                val destinationsList = bikesDoc.get("destinations") as? List<String> ?: emptyList()
                withContext(Dispatchers.Main) {
                    if (!isAdded || _binding == null) return@withContext

                    if (destinationsList.isNotEmpty()) {
                        val destAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, destinationsList)
                        destAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

                        // First spinner
                        binding.batteryDestination.adapter = destAdapter

                        // Second spinner (destinationToDelete)
                        val deleteAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, destinationsList)
                        deleteAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        binding.destinationToDelete.adapter = deleteAdapter
                    } else {
                        Toast.makeText(requireContext(), "No destinations found", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {

            }
        }
    }

    private fun loadBatteries() {
        val db = FirebaseFirestore.getInstance()
        val batteriesRef = db.collection("batteries")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Fetch all batteries
                val querySnapshot = withContext(Dispatchers.IO) { batteriesRef.get().await() }
                if (!isAdded || _binding == null) return@launch

                val batteryNames = mutableListOf<String>()
                val disabledBatteries = mutableSetOf<String>()

                withContext(Dispatchers.Default) {
                    for (doc in querySnapshot.documents) {
                        val data = doc.data ?: continue
                        val name = data["batteryName"] as? String ?: continue
                        val assignedRider = data["assignedRider"] as? String ?: "None"

                        batteryNames.add(name)
                        if (assignedRider != "None") {
                            disabledBatteries.add(name)
                        }
                    }
                    batteryNames.sort()
                }

                withContext(Dispatchers.Main) {
                    if (!isAdded || _binding == null) return@withContext

                    val adapter = object : ArrayAdapter<String>(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        batteryNames
                    ) {
                        override fun isEnabled(position: Int): Boolean {
                            return batteryNames[position] !in disabledBatteries
                        }

                        override fun getDropDownView(
                            position: Int,
                            convertView: View?,
                            parent: ViewGroup
                        ): View {
                            val view = super.getDropDownView(position, convertView, parent)
                            val textView = view as TextView
                            if (batteryNames[position] in disabledBatteries) {
                                textView.setTextColor(Color.GRAY)
                            }
                            return view
                        }
                    }

                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.batteryToDelete.adapter = adapter

                    val firstEnabledIndex = batteryNames.indexOfFirst { it !in disabledBatteries }
                    if (firstEnabledIndex != -1) {
                        binding.batteryToDelete.setSelection(firstEnabledIndex)
                        binding.batteryToDelete.isEnabled = true
                        binding.btnDeleteBattery.isEnabled = true
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "All batteries are currently assigned and can't be deleted.",
                            Toast.LENGTH_LONG
                        ).show()
                        binding.batteryToDelete.isEnabled = false
                        binding.btnDeleteBattery.isEnabled = false
                        binding.btnDeleteBattery.setBackgroundColor(Color.LTGRAY)
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded && _binding != null) {
                        Toast.makeText(
                            requireContext(),
                            "Failed to load batteries: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun deleteBike() {
        if (!isAdded || view == null || _binding == null) return // Prevent crash
        binding.progressBar2.post {
            binding.progressBar2.visibility = View.VISIBLE
            binding.btnDeleteBike.visibility = View.GONE
        }
        val selectedBike = binding.bikeToManage.selectedItem as? String

        if (selectedBike.isNullOrEmpty()) {
            binding.progressBar2.post {
                binding.progressBar2.visibility = View.GONE
                binding.btnDeleteBike.visibility = View.VISIBLE
            }
            Toast.makeText(requireContext(), "Please select a bike to delete", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("general").document("general_variables")

        docRef.get().addOnSuccessListener { document ->
            val bikesMap = document.get("bikes") as? Map<*, *> ?: return@addOnSuccessListener
            val selectedBikeData = bikesMap[selectedBike] as? Map<*, *> ?: return@addOnSuccessListener
            val isAssigned = selectedBikeData["isAssigned"] as? Boolean != false

            if (!isAssigned) {
                val update = mapOf<String, Any>(
                    "bikes.$selectedBike" to FieldValue.delete()
                )
                docRef.update(update)
                    .addOnSuccessListener {
                        binding.progressBar2.visibility = View.GONE
                        binding.btnDeleteBike.visibility = View.VISIBLE
                        loadBikes()
                        Toast.makeText(requireContext(), "$selectedBike deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        binding.progressBar2.visibility = View.GONE
                        binding.btnDeleteBike.visibility = View.VISIBLE
                        Toast.makeText(requireContext(), "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                binding.progressBar2.visibility = View.GONE
                binding.btnDeleteBike.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "$selectedBike is assigned and cannot be deleted", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            binding.progressBar2.visibility = View.GONE
            binding.btnDeleteBike.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Failed to check bike information. Try again later: ${e.message}", Toast.LENGTH_SHORT).show()
        }


    }

    private fun addBike() {
        if (!isAdded || view == null || _binding == null) return
        binding.progressBar1.post {
            binding.progressBar1.visibility = View.VISIBLE
            binding.btnAddBike.visibility = View.GONE
        }

        val plateNumber = binding.inputPlateNumber.text.toString().trim()

        if (plateNumber.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a plate number", Toast.LENGTH_SHORT).show()
            binding.progressBar1.post {
                binding.progressBar1.visibility = View.GONE
                binding.btnAddBike.visibility = View.VISIBLE
            }
            return
        }

        val db = FirebaseFirestore.getInstance()
        val bikesRef = db.collection("general").document("general_variables")

        bikesRef.get()
            .addOnSuccessListener { document ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                val currentBikes = document.get("bikes") as? Map<String, Map<String, Any>> ?: emptyMap()

                if (currentBikes.containsKey(plateNumber)) {
                    binding.progressBar1.post {
                        binding.progressBar1.visibility = View.GONE
                        binding.btnAddBike.visibility = View.VISIBLE
                    }
                    Toast.makeText(requireContext(), "Bike already exists", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val updatedBikes = currentBikes.toMutableMap()
                updatedBikes[plateNumber] = mapOf(
                    "isAssigned" to false,
                    "assignedRider" to "None"
                )

                bikesRef.update("bikes", updatedBikes)
                    .addOnSuccessListener {
                        binding.progressBar1.post {
                            binding.progressBar1.visibility = View.GONE
                            binding.btnAddBike.visibility = View.VISIBLE
                        }
                        Toast.makeText(requireContext(), "Bike added successfully", Toast.LENGTH_SHORT).show()
                        binding.inputPlateNumber.text.clear()
                        loadBikes()
                    }
                    .addOnFailureListener { e ->
                        binding.progressBar1.post {
                            binding.progressBar1.visibility = View.GONE
                            binding.btnAddBike.visibility = View.VISIBLE
                        }
                        Toast.makeText(requireContext(), "Failed to add bike: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                binding.progressBar1.post {
                    binding.progressBar1.visibility = View.GONE
                    binding.btnAddBike.visibility = View.VISIBLE
                }
                Toast.makeText(requireContext(), "Error fetching bikes: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addBattery(batteryName: String, destination: String) {
        if (!isAdded || _binding == null) return

        // Show loading
        binding.btnAddBattery.visibility = View.GONE
        binding.addBatteryPBar.visibility = View.VISIBLE

        val db = FirebaseFirestore.getInstance()
        val batteriesRef = db.collection("batteries")

        // Check for duplicate names (case-insensitive) by querying existing batteries
        batteriesRef
            .whereEqualTo("batteryNameLower", batteryName.lowercase())
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    Toast.makeText(requireContext(), "A battery with this name already exists.", Toast.LENGTH_SHORT).show()
                    resetAddBatteryUI()
                    return@addOnSuccessListener
                }

                // No duplicate, create a new battery document with auto-generated ID
                val newBatteryId = batteriesRef.document().id
                val nowTimestamp = Timestamp.now()
                val batteryData = mapOf(
                    "batteryName" to batteryName,
                    "batteryNameLower" to batteryName.lowercase(), // for easy duplicate check
                    "batteryLocation" to destination,
                    "assignedBike" to "None",
                    "assignedRider" to "None",
                    "offTime" to nowTimestamp,
                    "traces" to mapOf(
                        globalDateKey to mapOf(
                            "entries" to listOf("Battery was added"),
                            "dateEdited" to nowTimestamp
                        )
                    )
                )

                batteriesRef.document(newBatteryId)
                    .set(batteryData)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Battery added successfully.", Toast.LENGTH_SHORT).show()
                        loadBatteries() // refresh UI
                        resetAddBatteryUI()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Failed to add battery: ${e.message}", Toast.LENGTH_SHORT).show()
                        resetAddBatteryUI()
                    }

            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to check duplicates: ${e.message}", Toast.LENGTH_SHORT).show()
                resetAddBatteryUI()
            }
    }

    // Helper to reset the UI
    private fun resetAddBatteryUI() {
        if (!isAdded || _binding == null) return
        binding.btnAddBattery.visibility = View.VISIBLE
        binding.addBatteryPBar.visibility = View.GONE
        binding.inputBatteryName.text.clear()
    }

    private fun addDestination() {
        if (!isAdded || view == null || _binding == null) return // Prevent crash
        binding.progressBar3.post {
            binding.progressBar3.visibility = View.VISIBLE
            binding.btnAddDestination.visibility = View.GONE
        }
        val destination = binding.inputBatteryDestination.text.toString().trim()

        val db = FirebaseFirestore.getInstance()
        val destinationsRef = db.collection("general").document("general_variables")

        destinationsRef.get()
            .addOnSuccessListener { document ->
                if (!isAdded || _binding == null) return@addOnSuccessListener // Prevent crash

                val currentDestinations = document.get("destinations") as? MutableList<String> ?: mutableListOf()

                if (destination in currentDestinations) {
                    binding.progressBar3.post {
                        binding.progressBar3.visibility = View.GONE
                        binding.btnAddDestination.visibility = View.VISIBLE
                    }
                    Toast.makeText(requireContext(), "Destination already exists", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                currentDestinations.add(destination) // Append new bike

                destinationsRef.update("destinations", currentDestinations)
                    .addOnSuccessListener {
                        loadBikes()
                        binding.progressBar3.post {
                            binding.progressBar3.visibility = View.GONE
                            binding.btnAddDestination.visibility = View.VISIBLE
                        }
                        Toast.makeText(requireContext(), "Destination added successfully", Toast.LENGTH_SHORT).show()
                        binding.inputBatteryDestination.text.clear() // Clear input after adding
                    }
                    .addOnFailureListener { e ->
                        binding.progressBar3.post {
                            binding.progressBar3.visibility = View.GONE
                            binding.btnAddDestination.visibility = View.VISIBLE
                        }
                        Toast.makeText(requireContext(), "Failed to add destination: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                binding.progressBar3.post {
                    binding.progressBar3.visibility = View.GONE
                    binding.btnAddDestination.visibility = View.VISIBLE
                }
                Toast.makeText(requireContext(), "Error fetching destinations: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}