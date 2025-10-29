package com.billkmotolink.ltd.ui.post_memo

import android.app.TimePickerDialog
import android.content.DialogInterface
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.billkmotolink.ltd.databinding.FragmentPostMemoBinding
import java.util.Calendar
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.graphics.Typeface
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.DatePicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.billkmotolink.ltd.ui.Utility
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import android.widget.TextView
import com.billkmotolink.ltd.R

class PostMemoFragment: Fragment() {
    private var _binding: FragmentPostMemoBinding? = null
    private val binding get() = _binding!!
    private var secondaryVenue: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostMemoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.timeText.setOnClickListener {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val timePickerDialog = TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
                val formattedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                binding.timeText.text = formattedTime
            }, hour, minute, false)

            timePickerDialog.show()
            timePickerDialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(
                ContextCompat.getColor(requireContext(), R.color.l4)
            )
            timePickerDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(
                ContextCompat.getColor(requireContext(), R.color.l3)
            )

        }

        val editText = binding.memoDescription

        binding.boldButton.setOnClickListener {
            val start = binding.memoDescription.selectionStart
            val end = binding.memoDescription.selectionEnd
            val editable = binding.memoDescription.text

            if (start >= 0 && end > start) {
                val spans = editable.getSpans(start, end, StyleSpan::class.java)
                var boldSpanFound = false

                for (span in spans) {
                    if (span.style == Typeface.BOLD) {
                        editable.removeSpan(span)
                        boldSpanFound = true
                    }
                }

                if (!boldSpanFound) {
                    editable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        binding.italicButton.setOnClickListener {
            val start = binding.memoDescription.selectionStart
            val end = binding.memoDescription.selectionEnd
            val editable = binding.memoDescription.text

            if (start >= 0 && end > start) {
                val spans = editable.getSpans(start, end, StyleSpan::class.java)
                var boldSpanFound = false

                for (span in spans) {
                    if (span.style == Typeface.ITALIC) {
                        editable.removeSpan(span)
                        boldSpanFound = true
                    }
                }

                if (!boldSpanFound) {
                    editable.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        binding.underlineButton.setOnClickListener {
            val start = binding.memoDescription.selectionStart
            val end = binding.memoDescription.selectionEnd
            val editable = binding.memoDescription.text

            if (start >= 0 && end > start) {
                val spans = editable.getSpans(start, end, UnderlineSpan::class.java)
                var underlineFound = false

                for (span in spans) {
                    editable.removeSpan(span)
                    underlineFound = true
                }

                if (!underlineFound) {
                    editable.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        binding.memoDescription.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                // Clear the default menu items
                menu?.clear()
                return true // Return true to allow selection but no menu
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                menu?.clear()
                return false
            }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false

            override fun onDestroyActionMode(mode: ActionMode?) {
                // Optional cleanup if needed
            }
        }

        binding.memoDatePicker.minDate = System.currentTimeMillis()

        binding.btnPostMemo.setOnClickListener {
            binding.btnPostMemo.visibility = View.GONE
            binding.memoProgressBar.visibility = View.VISIBLE

            val calendar = Calendar.getInstance()
            val heading = binding.memoHeading.text.toString().trim()
            val time = binding.timeText.text.toString().trim()
            val unHTMLDescr = binding.memoDescription.text.toString().trim()
            val htmlDescription = Html.toHtml(binding.memoDescription.text, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL).trim()
            if (heading.isEmpty() || unHTMLDescr.isEmpty() || time == "Select Time") {
                Toast.makeText(requireContext(), "Please fill all fields before posting.", Toast.LENGTH_LONG).show()
                binding.btnPostMemo.visibility = View.VISIBLE
                binding.memoProgressBar.visibility = View.GONE
                return@setOnClickListener
            }
            val selectedPrimaryVenue = binding.memoModeSpinner.selectedItem.toString()
            if (selectedPrimaryVenue == "Online" && secondaryVenue.isEmpty()) {
                Toast.makeText(requireContext(), "Select an online platform.", Toast.LENGTH_LONG).show()
                binding.btnPostMemo.visibility = View.VISIBLE
                binding.memoProgressBar.visibility = View.GONE
                return@setOnClickListener
            }
            val datePicker = binding.memoDatePicker
            val selectedDay = datePicker.dayOfMonth
            val selectedMonth = datePicker.month
            val selectedYear = datePicker.year

            val selectedTime = binding.timeText.text.toString()
            val (hour, minute) = selectedTime.split(":").map { it.toInt() }

            calendar.set(Calendar.YEAR, selectedYear)
            calendar.set(Calendar.MONTH, selectedMonth)
            calendar.set(Calendar.DAY_OF_MONTH, selectedDay)
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val memoTimestamp = Timestamp(calendar.time)
            val meetingVenueText = if (selectedPrimaryVenue == "Physical") selectedPrimaryVenue else secondaryVenue
            val memoData = mapOf(
                "heading" to heading,
                "description" to htmlDescription,
                "timestamp" to memoTimestamp,
                "postedOn" to Timestamp.now(),
                "venue" to meetingVenueText
            )

            val alertDialog = AlertDialog.Builder(requireContext())

            // Custom title with red color
            val title = SpannableString("Notifier")
            title.setSpan(ForegroundColorSpan(Color.BLUE), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Custom message with black color
            val message = SpannableString("Please confirm action.")
            message.setSpan(ForegroundColorSpan(Color.GRAY), 0, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            alertDialog.setTitle(title)
            alertDialog.setMessage(message)
            alertDialog.setIcon(R.drawable.success)

            alertDialog.setPositiveButton("Post Memo") { _, _ ->
                FirebaseFirestore.getInstance()
                    .collection("memos") // The collection to hold memos
                    .document("latest") // Always overwrite this one document
                    .set(memoData)
                    .addOnSuccessListener {

                        lifecycleScope.launch {
                            val roles = listOf("Admin", "CEO", "Systems, IT", "Rider")
                            Utility.notifyAdmins("A new memo was posted.", "Memos", roles)
                        }

                        Toast.makeText(requireContext(), "Memos will auto-delete 3 days after expiry!", Toast.LENGTH_SHORT).show()
                        binding.memoHeading.setText("")
                        binding.memoDescription.setText("")
                        binding.timeText.text = "Select Time"

                        binding.btnPostMemo.visibility = View.VISIBLE
                        binding.memoProgressBar.visibility = View.GONE
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Failed to post memo", Toast.LENGTH_SHORT).show()
                        binding.btnPostMemo.visibility = View.VISIBLE
                        binding.memoProgressBar.visibility = View.GONE
                    }
            }
            alertDialog.setCancelable(false)

            alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if user cancels
                binding.btnPostMemo.visibility = View.VISIBLE
                binding.memoProgressBar.visibility = View.GONE
            }

            alertDialog.create().apply {
                window?.setBackgroundDrawableResource(R.drawable.rounded_black)
                show()

                // Change button text colors after showing
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.GREEN)  // confirm button
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.RED)    // cancel button
            }

        }


        val meetOptionLayout = binding.meetOptionLayout
        val checkboxMeet = binding.checkboxMeet
        val teamsOptionLayout = binding.teamsOptionLayout
        val checkboxTeams = binding.checkboxTeams
        val zoomOptionLayout = binding.zoomOptionLayout
        val checkboxZoom = binding.checkboxZoom
        val whatsAppOptionLayout = binding.whatsAppOptionLayout
        val checkboxWhatsApp = binding.checkboxWhatsApp

        binding.memoModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selected = parent.getItemAtPosition(position).toString()
                binding.onlineOptionsLayout.visibility =
                    if (selected == "Online") View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }



        /* check items when the layout is clicked.
        * A single layout contains
        * 1. the checkbox
        * 2. the logo
        * 3. the name */
        meetOptionLayout.setOnClickListener {
            checkboxMeet.isChecked = !checkboxMeet.isChecked
            checkboxTeams.isChecked = false
            checkboxZoom.isChecked = false
            checkboxWhatsApp.isChecked = false
        }

        teamsOptionLayout.setOnClickListener {
            checkboxTeams.isChecked = !checkboxTeams.isChecked
            checkboxMeet.isChecked = false
            checkboxZoom.isChecked = false
            checkboxWhatsApp.isChecked = false
        }

        zoomOptionLayout.setOnClickListener {
            checkboxZoom.isChecked = !checkboxZoom.isChecked
            checkboxMeet.isChecked = false
            checkboxTeams.isChecked = false
            checkboxWhatsApp.isChecked = false
        }

        whatsAppOptionLayout.setOnClickListener {
            checkboxWhatsApp.isChecked = !checkboxWhatsApp.isChecked
            checkboxMeet.isChecked = false
            checkboxZoom.isChecked = false
            checkboxTeams.isChecked = false
        }

        /*when the check state of the individual checkbox changes,
        if it's a true, change the global parameter 'secondaryVenue'
        * then in the same execution, uncheck any other checked item in the context*/
        checkboxMeet.setOnCheckedChangeListener{_ , isChecked ->
            if (checkboxMeet.isChecked) {
                secondaryVenue = "Google Meet"
                checkboxZoom.isChecked = false
                checkboxTeams.isChecked = false
                checkboxWhatsApp.isChecked = false
            }
            else if (!checkboxMeet.isChecked
                && !checkboxZoom.isChecked
                && !checkboxTeams.isChecked
                && !checkboxWhatsApp.isChecked) {
                checkboxMeet.isChecked = true
                secondaryVenue = "Google Meet"
            }
        }
        checkboxTeams.setOnCheckedChangeListener{_ , isChecked ->
            if (checkboxTeams.isChecked) {
                secondaryVenue = "Microsoft Teams"
                checkboxZoom.isChecked = false
                checkboxMeet.isChecked = false
                checkboxWhatsApp.isChecked = false
            }
            else if (!checkboxTeams.isChecked
                && !checkboxZoom.isChecked
                && !checkboxMeet.isChecked
                && !checkboxWhatsApp.isChecked) {
                checkboxTeams.isChecked = true
                secondaryVenue = "Microsoft Teams"
            }
        }
        checkboxZoom.setOnCheckedChangeListener{_ , isChecked ->
            if (checkboxZoom.isChecked) {
                secondaryVenue = "Zoom"
                checkboxMeet.isChecked = false
                checkboxTeams.isChecked = false
                checkboxWhatsApp.isChecked = false
            }
            else if (!checkboxZoom.isChecked
                && !checkboxMeet.isChecked
                && !checkboxTeams.isChecked
                && !checkboxWhatsApp.isChecked) {
                checkboxZoom.isChecked = true
                secondaryVenue = "Zoom"
            }
        }
        checkboxWhatsApp.setOnCheckedChangeListener{_ , isChecked ->
            if (checkboxWhatsApp.isChecked) {
                secondaryVenue = "WhatsApp"
                checkboxZoom.isChecked = false
                checkboxTeams.isChecked = false
                checkboxMeet.isChecked = false
            }
            else if (!checkboxWhatsApp.isChecked
                && !checkboxZoom.isChecked
                && !checkboxTeams.isChecked
                && !checkboxMeet.isChecked) {
                checkboxWhatsApp.isChecked = true
                secondaryVenue = "WhatsApp"
            }
        }

        loadMemoPrimaryVenues()
        val memoDatePicker = binding.memoDatePicker
        styleDatePicker(memoDatePicker)
    }

    fun styleDatePicker(datePicker: DatePicker) {
        // Header background
        datePicker.findViewById<View>(Resources.getSystem().getIdentifier("date_picker_header", "id", "android"))
            ?.setBackgroundColor(ContextCompat.getColor(datePicker.context, R.color.brown3))

        // Text colors
        val idDay = Resources.getSystem().getIdentifier("day", "id", "android")
        val idMonth = Resources.getSystem().getIdentifier("month", "id", "android")
        val idYear = Resources.getSystem().getIdentifier("year", "id", "android")

        datePicker.findViewById<TextView>(idDay)?.setTextColor(ContextCompat.getColor(datePicker.context, R.color.brown4))
        datePicker.findViewById<TextView>(idMonth)?.setTextColor(ContextCompat.getColor(datePicker.context, R.color.brown3))
        datePicker.findViewById<TextView>(idYear)?.setTextColor(ContextCompat.getColor(datePicker.context, R.color.brown2))
    }

    private fun loadMemoPrimaryVenues() {
        val spinner = binding.memoModeSpinner
        val modes = listOf("Physical", "Online")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, modes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}