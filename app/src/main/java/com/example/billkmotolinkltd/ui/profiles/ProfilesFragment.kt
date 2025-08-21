package com.example.billkmotolinkltd.ui.profiles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.billkmotolinkltd.databinding.FragmentProfilesBinding
import com.example.billkmotolinkltd.ui.ProfileUser
import com.example.billkmotolinkltd.ui.ProfileAdapter
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ProfilesFragment: Fragment() {
    private var _binding: FragmentProfilesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfilesBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        fetchInfo()
    }

    /* -------------------- RecyclerView Setup -------------------- */
    private lateinit var profileAdapter: ProfileAdapter

    private fun setupRecyclerView() {
        profileAdapter = ProfileAdapter(requireContext()).apply {
            setHasStableIds(true)
        }
        binding.recyclerViewProfiles.apply {
            adapter = profileAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
            itemAnimator = null
        }
    }


    /* -------------------- Data Fetch -------------------- */
    private fun fetchInfo() {
        if (!isAdded || _binding == null) return

        binding.profilesProgressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Step 1: Fetch from Firestore
                val snapshot = withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .get()
                        .await()
                }

                // Step 2: Process documents (Default dispatcher for CPU work)
                val userList = withContext(Dispatchers.Default) {
                    processUserDocuments(snapshot.documents)
                }

                // Step 3: Update UI (Main thread)
                withContext(Dispatchers.Main) {
                    if (!isAdded || _binding == null) return@withContext
                    updateRecyclerView(userList)
                    binding.profilesProgressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded && _binding != null) {
                        Toast.makeText(
                            requireContext(),
                            "Error loading users: ${e.message ?: "Unknown error"}",
                            Toast.LENGTH_SHORT
                        ).show()
                        binding.profilesProgressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    /* -------------------- Processing Logic -------------------- */
    @OptIn(UnstableApi::class)
    private fun processUserDocuments(documents: List<DocumentSnapshot>): List<ProfileUser> {
        return documents
            .mapNotNull { document ->
                try {
                    processSingleUserDocument(document)
                } catch (e: Exception) {
                    Log.e("ProfileProcessing", "Error processing user ${document.id}", e)
                    null
                }
            }
            .distinctBy { it.uid } // ✅ Deduplicate by UID
    }

    private fun processSingleUserDocument(document: DocumentSnapshot): ProfileUser? {
        val lastSeenClockInTime = document.getTimestamp("clockInTime") ?: Timestamp.now()
        val createdAt = document.getTimestamp("createdAt") ?: Timestamp.now()
        val assignedBike = document.getString("currentBike") ?: "None"
        val currentInAppBalance = document.getDouble("currentInAppBalance") ?: 0.0
        val targetAmount = document.getDouble("dailyTarget") ?: 0.0
        val email = document.getString("email") ?: "Rider"
        val fcm = document.getString("fcmToken") ?: "unknown"
        val gender = document.getString("gender") ?: "Unidentified"
        val bankAcc = document.getString("dtbAccNo") ?: "Nil"
        val hrsPerShift = document.getDouble("hrsPerShift") ?: 0.0
        val idNumber = document.getString("idNumber") ?: "Unidentified"
        val isActive = document.getBoolean("isActive") == true
        val isDeleted = document.getBoolean("isDeleted") == true
        val isClockedIn = document.getBoolean("isClockedIn") == true
        val lastSeenClockOutTime = document.getTimestamp("clockInTime") ?: Timestamp.now()
        val netClockedLastly = document.getDouble("netClockedLastly") ?: 0.0
        val pendingAmount = document.getDouble("pendingAmount") ?: 0.0
        val phoneNumber = document.getString("phoneNumber") ?: "Unknown"
        val requirements = document.get("requirements") as? Map<*, *>
        val requirementCount = requirements?.size ?: 0
        val sundayTarget = document.getDouble("sundayTarget") ?: 0.0
        val username = document.getString("userName") ?: ""
        var userRank = document.getString("userRank") ?: "Rider"
        val uid = document.id

        if (isDeleted || userRank == "CEO") return null

        userRank = if (userRank == "HR") "Human Resource" else userRank

        return ProfileUser(
            lastSeenClockInTime,
            createdAt,
            assignedBike,
            currentInAppBalance,
            targetAmount,
            email,
            fcm,
            gender,
            bankAcc,
            hrsPerShift,
            idNumber,
            isActive,
            isClockedIn,
            lastSeenClockOutTime,
            netClockedLastly,
            pendingAmount,
            phoneNumber,
            requirementCount,
            sundayTarget,
            username,
            userRank,
            uid
        )
    }

    /* -------------------- RecyclerView Update -------------------- */
    private fun updateRecyclerView(userList: List<ProfileUser>) {
        profileAdapter.submitList(userList)
    }

    /* -------------------- Cleanup -------------------- */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}