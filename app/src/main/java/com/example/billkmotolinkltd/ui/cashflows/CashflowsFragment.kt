package com.example.billkmotolinkltd.ui.cashflows

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentCashflowsBinding
import com.example.billkmotolinkltd.databinding.FragmentIncidencesBinding
import com.example.billkmotolinkltd.databinding.FragmentProfilesBinding
import com.example.billkmotolinkltd.ui.CashFlow
import com.example.billkmotolinkltd.ui.CashflowAdapter
import com.example.billkmotolinkltd.ui.ReportsAdapter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class CashflowsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CashflowAdapter

    private var _binding: FragmentCashflowsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCashflowsBinding.inflate(inflater, container, false)
        recyclerView = binding.recyclerViewCashFlows  // use binding to get recyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fetchCashFlows()

        loadSearchBox(view)
    }

    private fun loadSearchBox(view: View) {
        val searchView = view.findViewById<SearchView>(R.id.searchView)
        recyclerView = view.findViewById(R.id.recyclerViewCashFlows)

        // Initialize adapter with empty list
        adapter = CashflowAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // Set up search functionality
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter.filter(newText)
                return true
            }
        })

        fetchCashFlows()
    }
    private fun fetchCashFlows() {
        val db = FirebaseFirestore.getInstance()
        binding.cashFlowsProgressBar.visibility = View.VISIBLE

        db.collection("general")
            .document("general_variables")
            .collection("cash_flows")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val cashFlowList = mutableListOf<CashFlow>()

                for (doc in querySnapshot.documents) {
                    cashFlowList.add(CashFlow(
                        id = doc.id,
                        message = doc.getString("message") ?: "",
                        time = doc.getString("time") ?: "",
                        isIncremental = doc.getBoolean("isIncremental") ?: false,
                        identity = doc.getString("transactionId") ?: "No ID"
                    ))
                }

                if (cashFlowList.isEmpty()) {
                    binding.noCashFlowsText.visibility = View.VISIBLE
                } else {
                    binding.noCashFlowsText.visibility = View.GONE
                }

                adapter.updateList(cashFlowList)
                binding.cashFlowsProgressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Log.e("CashFlowFragment", "Error fetching cash flows", e)
                Toast.makeText(requireContext(), "Failed to load cash flows", Toast.LENGTH_SHORT).show()
                binding.cashFlowsProgressBar.visibility = View.GONE
            }
    }

}
