package com.example.billkmotolinkltd.ui.complaints

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentComplainBinding
import com.example.billkmotolinkltd.databinding.FragmentComplaintsBinding
import com.example.billkmotolinkltd.ui.ComplaintsAdapter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar

class ComplaintsFragment: Fragment() {

    private var _binding: FragmentComplaintsBinding? = null
    private val binding get() = _binding!!
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var recyclerView: RecyclerView
    private lateinit var complaintsAdapter: ComplaintsAdapter
    private val complaintsList = mutableListOf<Complaint>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentComplaintsBinding.inflate(inflater, container, false)
        val view = binding.root

        recyclerView = binding.complaintsRecyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        complaintsAdapter = ComplaintsAdapter(complaintsList)
        recyclerView.adapter = complaintsAdapter

        return view
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            deleteOldComplaints()
            fetchComplaints()
        }
    }


    data class Complaint(
        val message: String = "",
        val timestamp: com.google.firebase.Timestamp? = null,
        val uid: String = ""
    )

    private fun deleteOldComplaints() {
        val db = FirebaseFirestore.getInstance()
        val oneWeekAgo = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
        }.time // java.util.Date

        lifecycleScope.launch {
            try {
                // Fetch complaints older than 7 days
                val oldComplaints = withContext(Dispatchers.IO) {
                    db.collection("complains")
                        .document("user_complains")
                        .collection("all")
                        .whereLessThan("timestamp", oneWeekAgo)
                        .get()
                        .await()
                }

                if (oldComplaints.isEmpty) {
                    withContext(Dispatchers.Main) {
                        // Toast.makeText(requireContext(), "No old complaints to delete", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Batch delete
                val batch = db.batch()
                for (doc in oldComplaints.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Old complaints deleted", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error deleting old complaints: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchComplaints() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                _binding?.let {
                    binding.complaintsProgressBar.visibility = View.VISIBLE
                }
                val snapshot = withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance()
                        .collection("complains")
                        .document("user_complains")
                        .collection("all")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .get()
                        .await()
                }

                Log.d("ComplaintsDebug", "Fetched snapshot: size=${snapshot.size()}, isEmpty=${snapshot.isEmpty}")

                val newComplaints = snapshot.toObjects(Complaint::class.java)
                // Log.d("ComplaintsDebug", "Mapped complaints count=${newComplaints.size}")
                newComplaints.forEach { complaint ->
                    // Log.d("ComplaintsDebug", "Complaint: $complaint")
                }

                withContext(Dispatchers.Main) {
                    if (snapshot.isEmpty) {
                        // Toast.makeText(requireContext(), "No complaints found", Toast.LENGTH_SHORT).show()
                        showFragments()
                    } else {
                        // Toast.makeText(requireContext(), "Snapshot not empty", Toast.LENGTH_SHORT).show()
                        complaintsList.clear()
                        complaintsList.addAll(newComplaints)
                        complaintsAdapter.notifyDataSetChanged()

                        _binding?.let { binding ->

                            binding.noComplaints.visibility = View.GONE
                            binding.complaintsRecyclerView.visibility = View.VISIBLE
                            binding.complaintsProgressBar.visibility = View.GONE
                        }
                    }
                }
            }
            catch (e: Exception) {
                // Log.e("ComplaintsDebug", "Error fetching complaints", e)
                withContext(Dispatchers.Main) {
                    _binding?.let { binding ->
                        // binding.noComplaints.text = "Error loading complaints"
                        binding.noComplaints.visibility = View.VISIBLE
                        binding.complaintsRecyclerView.visibility = View.GONE
                    }
                }
            }
            finally {
                _binding?.let { binding ->
                    binding.complaintsProgressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun showFragments() {
        _binding?.let { binding ->
            binding.noComplaints.visibility = View.VISIBLE
            binding.complaintsRecyclerView.visibility = View.GONE
            binding.complaintsProgressBar.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel() // Clean up coroutines
        _binding = null
    }
}

