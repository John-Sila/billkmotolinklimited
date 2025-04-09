package com.example.billkmotolinkltd.ui.bikes

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
import androidx.lifecycle.ViewModelProvider
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentBikesBinding
import com.example.billkmotolinkltd.databinding.FragmentUsersBinding
import com.google.firebase.firestore.FirebaseFirestore

class BikesFragment : Fragment() {

    private var _binding: FragmentBikesBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBikesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadBikes() // this is also the function that checks company status
        if (!isAdded || _binding == null) return // Prevent crash

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
    }

    private fun addBike() {
        if (!isAdded || view == null || _binding == null) return // Prevent crash
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
                if (!isAdded || _binding == null) return@addOnSuccessListener // Prevent crash

                val currentBikes = document.get("bikes") as? MutableList<String> ?: mutableListOf()

                if (plateNumber in currentBikes) {
                    binding.progressBar1.post {
                        binding.progressBar1.visibility = View.GONE
                        binding.btnAddBike.visibility = View.VISIBLE
                    }
                    Toast.makeText(requireContext(), "Bike already exists", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                currentBikes.add(plateNumber) // Append new bike

                bikesRef.update("bikes", currentBikes)
                    .addOnSuccessListener {
                        binding.progressBar1.post {
                            binding.progressBar1.visibility = View.GONE
                            binding.btnAddBike.visibility = View.VISIBLE
                        }
                        Toast.makeText(requireContext(), "Bike added successfully", Toast.LENGTH_SHORT).show()
                        binding.inputPlateNumber.text.clear() // Clear input after adding
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

//    company status too
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
                binding.bikeToManage.adapter = adapter
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to load bikes: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        val cRef = db.collection("general").document("general_variables")
        cRef.get().addOnSuccessListener { companyDoc ->
        val companyStatus = companyDoc.getString("companyState") ?: "Unknown"
            if (!isAdded || view == null || _binding == null) return@addOnSuccessListener // Prevent crash
            if (companyStatus == "Paused") {
                binding.btnOperationsPaused1.visibility = View.VISIBLE
                binding.btnAddBike.visibility = View.GONE

                binding.btnOperationsPaused2.visibility = View.VISIBLE
                binding.btnDeleteBike.visibility = View.GONE
            } else if (companyStatus == "Continuing") {
                binding.btnOperationsPaused1.visibility = View.GONE
                binding.btnAddBike.visibility = View.VISIBLE

                binding.btnOperationsPaused2.visibility = View.GONE
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
        val bikesRef = db.collection("general").document("general_variables")

        bikesRef.get()
            .addOnSuccessListener { document ->
                if (!isAdded || _binding == null) return@addOnSuccessListener // Prevent crash

                val bikesList = document.get("bikes") as? MutableList<String> ?: mutableListOf()

                if (bikesList.contains(selectedBike)) {
                    bikesList.remove(selectedBike)

                    // Update Firestore with the modified list
                    bikesRef.update("bikes", bikesList)
                        .addOnSuccessListener {
                            binding.progressBar2.post {
                                binding.progressBar2.visibility = View.GONE
                                binding.btnDeleteBike.visibility = View.VISIBLE
                            }
                            Toast.makeText(requireContext(), "Bike deleted successfully", Toast.LENGTH_SHORT).show()
                            loadBikes() // Refresh spinner
                        }
                        .addOnFailureListener { e ->
                            binding.progressBar2.post {
                                binding.progressBar2.visibility = View.GONE
                                binding.btnDeleteBike.visibility = View.VISIBLE
                            }
                            Toast.makeText(requireContext(), "Failed to delete bike: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    binding.progressBar2.post {
                        binding.progressBar2.visibility = View.GONE
                        binding.btnDeleteBike.visibility = View.VISIBLE
                    }
                    Toast.makeText(requireContext(), "Bike not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error fetching bikes: ${e.message}", Toast.LENGTH_SHORT).show()
            }


    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}