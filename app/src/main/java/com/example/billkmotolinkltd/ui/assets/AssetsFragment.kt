package com.example.billkmotolinkltd.ui.assets

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
import androidx.fragment.app.Fragment
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentManageAssetsBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import kotlin.text.clear
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

        loadBikes()

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

            val dialog = alertDialog.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

            dialog.show()
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

            val dialog = alertDialog.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

            dialog.show()
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

            val dialog = alertDialog.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

            dialog.show()
        }

        binding.btnAddBattery.setOnClickListener {
            val batteryName = binding.inputBatteryName.text.toString().trim()
            val selectedDestination = binding.batteryDestination.selectedItem as? String

            if (batteryName.isEmpty() || selectedDestination?.isEmpty() == true) {
                Toast.makeText(requireContext(), "Either the battery name or location is invalid.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())

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

            val dialog = alertDialog.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_black) // (Optional) Custom background

            dialog.show()

        }

    }

    // load bikes, destinations, and company state
    private fun loadBikes() {
        val db = FirebaseFirestore.getInstance()
        val bikesRef = db.collection("general").document("general_variables")

        // Fetch bikes
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
                binding.bikeToManage.adapter = adapter

                // Find the first enabled bike
                val firstEnabledIndex = bikePlates.indexOfFirst { it !in disabledBikes }
                if (firstEnabledIndex != -1) {
                    binding.bikeToManage.setSelection(firstEnabledIndex)
                    binding.bikeToManage.isEnabled = true
                    binding.btnDeleteBike.isEnabled = true
                } else {
                    // All bikes are assigned (disabled)
                    Toast.makeText(requireContext(), "All bikes are currently assigned and can't be deleted.", Toast.LENGTH_LONG).show()
                    binding.bikeToManage.isEnabled = false
                    binding.btnDeleteBike.isEnabled = false
                    binding.btnDeleteBike.setBackgroundColor(Color.LTGRAY)
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

                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, destinationsList)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.batteryDestination.adapter = adapter
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to load destinations: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        // Check company state
        val cRef = db.collection("general").document("general_variables")
        cRef.get().addOnSuccessListener { companyDoc ->
            val companyStatus = companyDoc.getString("companyState") ?: "Unknown"
            if (!isAdded || view == null || _binding == null) return@addOnSuccessListener

            if (companyStatus == "Paused") {
                binding.btnOperationsPaused.visibility = View.VISIBLE
            } else if (companyStatus == "Continuing") {
                binding.btnAddBike.visibility = View.VISIBLE
                binding.btnAddDestination.visibility = View.VISIBLE
                binding.btnAddBattery.visibility = View.VISIBLE
                binding.btnDeleteBike.visibility = View.VISIBLE
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
                    "isAssigned" to false
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
        binding.btnAddBattery.visibility = View.GONE
        binding.addBatteryPBar.visibility = View.VISIBLE

        val db = FirebaseFirestore.getInstance()
        val generalRef = db.collection("general").document("general_variables")

        generalRef.get().addOnSuccessListener { document ->
            val currentBatteries = document.get("batteries") as? Map<String, Map<String, Any>> ?: emptyMap()

            // Check if battery with same name already exists (case-insensitive)
            val duplicateExists = currentBatteries.values.any { battery ->
                val existingName = battery["batteryName"] as? String
                existingName?.equals(batteryName, ignoreCase = true) == true
            }

            if (duplicateExists) {
                Toast.makeText(requireContext(), "A battery with this name already exists.", Toast.LENGTH_SHORT).show()
                if (!isAdded || _binding == null) return@addOnSuccessListener
                binding.btnAddBattery.visibility = View.VISIBLE
                binding.addBatteryPBar.visibility = View.GONE
                return@addOnSuccessListener
            }

            // No duplicate, proceed to add
            val nextIndex = currentBatteries.keys.mapNotNull { it.toIntOrNull() }.maxOrNull()?.plus(1) ?: 0

            val newBattery = mapOf(
                "batteryName" to batteryName,
                "batteryLocation" to destination,
                "assignedBike" to "None",
                "assignedRider" to "None",
                "offTime" to Timestamp.now()
            )

            val updatedBatteries = currentBatteries.toMutableMap()
            updatedBatteries[nextIndex.toString()] = newBattery

            generalRef.update("batteries", updatedBatteries)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Battery added successfully.", Toast.LENGTH_SHORT).show()
                    if (!isAdded || _binding == null) return@addOnSuccessListener
                    binding.btnAddBattery.visibility = View.VISIBLE
                    binding.addBatteryPBar.visibility = View.GONE
                    binding.inputBatteryName.text.clear()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to add battery: ${e.message}", Toast.LENGTH_SHORT).show()
                    if (!isAdded || _binding == null) return@addOnFailureListener
                    binding.btnAddBattery.visibility = View.VISIBLE
                    binding.addBatteryPBar.visibility = View.GONE
                }

        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Failed to fetch battery data: ${e.message}", Toast.LENGTH_SHORT).show()
            if (!isAdded || _binding == null) return@addOnFailureListener
            binding.btnAddBattery.visibility = View.VISIBLE
            binding.addBatteryPBar.visibility = View.GONE
        }
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