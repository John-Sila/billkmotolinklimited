package com.example.billkmotolinkltd.ui.batteries

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentBatteriesBinding
import com.example.billkmotolinkltd.ui.Battery
import com.example.billkmotolinkltd.ui.BatteryAdapter
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.example.billkmotolinkltd.ui.Utility

class BatteriesFragment : Fragment() {

    private var _binding: FragmentBatteriesBinding? = null
    private var userBike: String = "Unknown"

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBatteriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fetchBatteries()
        handler.post(fetchRunnable)
    }

    private fun fetchBatteries() {
        if (_binding == null || !isAdded) return
        binding.BatteriesProgressBar.visibility = View.VISIBLE
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("general").document("general_variables")

        docRef.get()
            .addOnSuccessListener { document ->
                if (_binding == null || !isAdded) return@addOnSuccessListener
                binding.BatteriesProgressBar.visibility = View.GONE
                val batteriesField = document.get("batteries") as? Map<String, Map<String, Any>> ?: emptyMap()

                val batteryList = batteriesField.mapNotNull { (_, value) ->
                    try {
                        Battery(
                            batteryName = value["batteryName"] as? String ?: "",
                            batteryLocation = value["batteryLocation"] as? String ?: "",
                            assignedBike = value["assignedBike"] as? String ?: "",
                            assignedRider = value["assignedRider"] as? String ?: "",
                            offTime = value["offTime"] as? Timestamp
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                val sortedBatteryList = batteryList.sortedBy { it.batteryName }
                if (sortedBatteryList.isEmpty()) {
                    binding.universalSection.visibility = View.GONE
                } else {
                    binding.universalSection.visibility = View.VISIBLE
                    val recyclerView = view?.findViewById<RecyclerView>(R.id.batteryRecyclerView)
                    recyclerView?.layoutManager = LinearLayoutManager(requireContext())
                    recyclerView?.adapter = BatteryAdapter(sortedBatteryList)
                }


            }
            .addOnFailureListener {
                if (_binding == null || !isAdded) return@addOnFailureListener
                binding.BatteriesProgressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Failed to load batteries", Toast.LENGTH_SHORT).show()
            }
    }

    val handler = Handler(Looper.getMainLooper())
    val fetchRunnable = object : Runnable {
        override fun run() {
            if (Utility.isInternetAvailable(requireContext())) {
                fetchBatteries()
            } else {
                Toast.makeText(requireContext(), "No internet connection. Couldn't reload batteries...", Toast.LENGTH_SHORT).show()
            }
            handler.postDelayed(this, 60000) // Run again in 1 minute
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(fetchRunnable)
        _binding = null
    }


}