package com.example.billkmotolinkltd.ui.create_poll

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.FragmentCreatePollBinding
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.android.identity.util.UUID
import com.example.billkmotolinkltd.ui.Utility
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import java.util.Calendar

class CreatePollFragment: Fragment() {
    private var _binding: FragmentCreatePollBinding? = null
    private val binding get() = _binding!!

    private lateinit var optionsContainer: LinearLayout
    private lateinit var btnAddOption: Button
    private lateinit var btnRemoveOption: Button
    private lateinit var pollTitle: EditText
    private lateinit var pollDescription: EditText
    private lateinit var ceoVoter: CheckBox
    private lateinit var adminVoter: CheckBox
    private lateinit var itVoter: CheckBox
    private lateinit var riderVoter: CheckBox
    private lateinit var hrVoter: CheckBox
    private lateinit var progressBar: ProgressBar
    private lateinit var btnPostPoll: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreatePollBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.pollDatePicker.minDate = System.currentTimeMillis()
        optionsContainer = view.findViewById(R.id.optionsContainer)
        btnAddOption = view.findViewById(R.id.btnAddOption)
        btnRemoveOption = view.findViewById(R.id.btnRemoveOption)
        btnPostPoll = view.findViewById(R.id.btnPostPoll)

        pollTitle = view.findViewById(R.id.pollHeading)
        pollDescription = view.findViewById(R.id.pollDescription)
        hrVoter = view.findViewById(R.id.hrVoter)
        adminVoter = view.findViewById(R.id.adminVoter)
        ceoVoter = view.findViewById(R.id.ceoVoter)
        itVoter = view.findViewById(R.id.itVoter)
        riderVoter = view.findViewById(R.id.riderVoter)

        progressBar = view.findViewById(R.id.pollCreationProgressBar)

        btnAddOption.setOnClickListener { addOption() }
        btnRemoveOption.setOnClickListener { removeOption() }
        btnPostPoll.setOnClickListener { postPoll() }
    }

    private fun addOption() {
        val editText = EditText(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8, 8, 8, 8)
            }
            hint = "Enter option"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            textSize = 16f
        }
        optionsContainer.addView(editText)
    }
    private fun removeOption() {
        val childCount = optionsContainer.childCount
        if (childCount > 0) {
            optionsContainer.removeViewAt(childCount - 1)
        }
    }


    private fun postPoll() {
        if (_binding == null || !isAdded) return
        val db = FirebaseFirestore.getInstance()
        val pollsRef = db.collection("polls").document("billk_polls")
        val pollId = UUID.randomUUID().toString()

        val pollHeading = binding.pollHeading.text.toString().trim()
        val description = binding.pollDescription.text.toString().trim()

        // Gather options from the container
        val optionsList = mutableListOf<Map<String, Any>>()
        for (i in 0 until binding.optionsContainer.childCount) {
            val child = binding.optionsContainer.getChildAt(i)
            if (child is EditText) {
                val optionText = child.text.toString().trim()
                if (optionText.isNotEmpty()) {
                    optionsList.add(mapOf("name" to optionText, "votes" to 0))
                }
            }
        }

        if (pollHeading.isEmpty() || optionsList.isEmpty() || description.isEmpty()) {
            Toast.makeText(requireContext(), "One or more of either title, description or options is missing.", Toast.LENGTH_SHORT).show()
            return
        }
        if (optionsList.size < 2) {
            Toast.makeText(requireContext(), "Options must be at least 2.", Toast.LENGTH_SHORT).show()
            return
        }

        // Expiry from DatePicker
        val day = binding.pollDatePicker.dayOfMonth
        val month = binding.pollDatePicker.month
        val year = binding.pollDatePicker.year
        val cal = Calendar.getInstance().apply { set(year, month, day, 23, 59, 59) }
        val expiresAt = Timestamp(cal.time)

        /*voters*/
        val allowedVoters = mutableListOf<String>()
        if (binding.ceoVoter.isChecked) allowedVoters.add("CEO")
        if (binding.itVoter.isChecked) allowedVoters.add("Systems, IT")
        if (binding.adminVoter.isChecked) allowedVoters.add("Admin")
        if (binding.riderVoter.isChecked) allowedVoters.add("Rider")
        if (binding.hrVoter.isChecked) allowedVoters.add("HR")
        if (allowedVoters.isEmpty()) {
            Toast.makeText(requireContext(), "No ranks have been allowed to vote.", Toast.LENGTH_SHORT).show()
            return
        }


        val alertDialog = AlertDialog.Builder(requireContext()).apply {
            // Custom title with green color
            val title = SpannableString("Polls").apply {
                setSpan(ForegroundColorSpan(Color.GREEN), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            // Custom message with gray color
            val message = SpannableString("Confirm creation of a new poll.").apply {
                setSpan(ForegroundColorSpan(Color.GRAY), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            setTitle(title)
            setMessage(message)
            setIcon(R.drawable.success)

            setPositiveButton("Confirm") { _, _ ->
                lifecycleScope.launch {
                    progressBar.visibility = View.VISIBLE
                    btnPostPoll.visibility = View.GONE
                    // Poll data
                    val pollData = hashMapOf(
                        "title" to pollHeading,
                        "description" to description,
                        "options" to optionsList,
                        "createdAt" to Timestamp.now(),
                        "expiresAt" to expiresAt,
                        "votedUIDs" to emptyList<String>(),
                        "allowedVoters" to allowedVoters
                    )

                    // Merge into all_polls without overwriting others
                    pollsRef.set(
                        mapOf(pollId to pollData),
                        SetOptions.merge()
                    ).addOnSuccessListener {
                        Toast.makeText(context, "Poll posted successfully", Toast.LENGTH_SHORT).show()
                        lifecycleScope.launch {
                            resetPollForm()
                            Utility.notifyAdmins("A new poll has been posted.", "Polls", allowedVoters)
                        }
                    }.addOnFailureListener { e ->
                        Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        lifecycleScope.launch { resetPollForm() }
                    }
                }
            }

            setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        }

        alertDialog.create().apply {
            window?.setBackgroundDrawableResource(R.drawable.rounded_black)
            show()

            getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.GREEN)  // confirm button
            getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.RED)
        }
    }

    private fun resetPollForm() {
        // Reset title and description
        pollTitle.setText("")
        pollDescription.setText("")

        // Destroy all dynamically added options
        optionsContainer.removeAllViews()

        // Uncheck all voter rank checkboxes
        ceoVoter.isChecked = false
        itVoter.isChecked = false
        adminVoter.isChecked = false
        riderVoter.isChecked = false
        hrVoter.isChecked = false

        progressBar.visibility = View.GONE
        btnPostPoll.visibility = View.VISIBLE
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}