package com.example.billkmotolinkltd.ui.polls

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.billkmotolinkltd.databinding.FragmentPollsBinding
import com.example.billkmotolinkltd.ui.Option
import com.example.billkmotolinkltd.ui.Poll
import com.example.billkmotolinkltd.ui.PollsAdapter
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class PollsFragment : Fragment() {

    private var _binding: FragmentPollsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: PollsAdapter
    private val db = FirebaseFirestore.getInstance()
    private var listenerRegistration: ListenerRegistration? = null

    private var currentUserId: String? = null
    private var currentUserRank: String? = null
    private var currentUserName: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPollsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

    }

    private fun fetchUserContext() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        currentUserId = uid

        val userRef = db.collection("users").document(uid)
        userRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                currentUserRank = doc.getString("userRank")
                currentUserName = doc.getString("userName") // ensure your users/{uid} doc has rank
                adapter = PollsAdapter(currentUserId, currentUserRank, currentUserName)
                binding.pollsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
                binding.pollsRecyclerView.adapter = adapter
                listenForPolls()
            }
        }
    }

    private fun listenForPolls() {
        if (!isAdded || _binding == null) return

        lifecycleScope.launch {
            _binding?.pollsProgressBar?.visibility = View.VISIBLE

            val pollsRef = db.collection("polls").document("billk_polls")

            listenerRegistration = pollsRef.addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(context, "Listen failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    _binding?.pollsProgressBar?.visibility = View.GONE
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    Toast.makeText(context, "No polls available", Toast.LENGTH_SHORT).show()
                    adapter.submitList(emptyList())
                    showFragments()
                    return@addSnapshotListener
                }

                // snapshot.data is a Map<String, Any>
                val pollsMap = snapshot.data ?: emptyMap<String, Any>()

                val polls = pollsMap.mapNotNull { (id, data) ->
                    (data as? Map<*, *>)?.let {
                        Poll(
                            id = id,
                            title = it["title"] as? String ?: "",
                            description = it["description"] as? String ?: "",
                            options = (it["options"] as? List<Map<String, Any>>)?.map { optionMap ->
                                Option(
                                    name = optionMap["name"] as? String ?: "",
                                    votes = (optionMap["votes"] as? Long)?.toInt() ?: 0
                                )
                            } ?: emptyList(),
                            createdAt = it["createdAt"] as? Timestamp,
                            expiresAt = it["expiresAt"] as? Timestamp,
                            votedUIDs = it["votedUIDs"] as? List<String> ?: emptyList(),
                            allowedVoters = it["allowedVoters"] as? List<String> ?: emptyList(),
                            votedUserNames = it["votedUserNames"] as? List<String> ?: emptyList()
                        )
                    }
                }

                adapter.submitList(polls.sortedByDescending { it.createdAt })
                _binding?.pollsProgressBar?.visibility = View.GONE
            }
        }
    }

    private fun showFragments() {
        _binding?.let { binding ->
            binding.noPolls.visibility = View.VISIBLE
            binding.pollsRecyclerView.visibility = View.GONE
            binding.pollsProgressBar.visibility = View.GONE
        }
    }

    override fun onStart() {
        super.onStart()
        fetchUserContext()
    }

    override fun onStop() {
        super.onStop()
        listenerRegistration?.remove()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
