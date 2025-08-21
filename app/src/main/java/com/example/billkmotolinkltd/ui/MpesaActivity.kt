package com.example.billkmotolinkltd.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.billkmotolinkltd.R
import com.example.billkmotolinkltd.databinding.ActivityMpesaBinding
import android.widget.RadioGroup
import android.widget.EditText
import android.widget.RadioButton
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MpesaActivity: AppCompatActivity() {
    private lateinit var binding: ActivityMpesaBinding
    private val predefinedMessages = arrayOf(
        "HD07OS1A0 Confirmed. Ksh30.00 sent to ALBINA  NAKOCHE 0705957581 on 13/8/25 at 6:22 PM. New M-PESA balance is Ksh93.80. Transaction cost, Ksh0.00.  Amount you can transact within the day is 499,847.00. Earn interest daily on Ziidi MMF,Dial *334#",
        "HD67WCF8C Confirmed. Ksh60.00 sent to FELISTERS  NYANGAU on 13/8/25 at 6:52 PM. New M-PESA balance is Ksh33.80. Transaction cost, Ksh0.00. Amount you can transact within the day is 499,787.00. Sign up for Lipa Na M-PESA Till online https://m-pesaforbusiness.co.ke\n",
        "HE6AOKBVK Confirmed.Ksh150.00 transferred from M-Shwari account on 14/8/25 at 11:33 AM. M-Shwari balance is Ksh15,827.92 .M-PESA balance is Ksh183.80 .Transaction cost Ksh.0.00",
        "HE0AOP3EY Confirmed. Ksh130.00 sent to Regina  Musyimi 0791700260 on 14/8/25 at 11:33 AM. New M-PESA balance is Ksh46.80. Transaction cost, Ksh7.00.  Amount you can transact within the day is 499,870.00. Earn interest daily on Ziidi MMF,Dial *334#\n",
        "HE8BATEXG confirmed.You bought Ksh10.00 of airtime on 14/8/25 at 1:46 PM.New M-PESA balance is Ksh36.80. Transaction cost, Ksh0.00. Amount you can transact within the day is 499,860.00. Start Investing today with Ziidi MMF & earn daily. Dial *334#."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMpesaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /* style the action and title bar */
        supportActionBar?.apply {
            setDisplayShowCustomEnabled(true)
            setDisplayShowTitleEnabled(false)
            // setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            elevation = 0f

            val customView = layoutInflater.inflate(R.layout.custom_mpesa_actionbar, null)
            setCustomView(customView)

            val titleTextView = customView.findViewById<TextView>(R.id.actionbar_title)
            titleTextView.text = "MPESA"

            val profileImageView = customView.findViewById<ImageView>(R.id.profile_image)
            profileImageView.setImageResource(R.drawable.ic_profile_placeholder)
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)


        // Calculate total top offset = status bar height + action bar height
        val actionBarHeight = obtainStyledAttributes(intArrayOf(android.R.attr.actionBarSize))
            .getDimensionPixelSize(0, 0)

        val statusBarHeightRes = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = if (statusBarHeightRes > 0) {
            resources.getDimensionPixelSize(statusBarHeightRes)
        } else 0

        val totalTopOffset = statusBarHeight + actionBarHeight

        // Apply padding so message container starts below both bars
        binding.messageContainer.setPadding(
            binding.messageContainer.paddingLeft,
            totalTopOffset,
            binding.messageContainer.paddingRight,
            binding.messageContainer.paddingBottom
        )

        showInputDialog()
    }

    private fun showInputDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_input, null)
        val startLetterEdit = dialogView.findViewById<EditText>(R.id.startLetterEdit)
        val nameEdit = dialogView.findViewById<EditText>(R.id.nameEdit)
        val phoneEdit = dialogView.findViewById<EditText>(R.id.phoneEdit)
        val amountEdit = dialogView.findViewById<EditText>(R.id.amountEdit)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroup)

        AlertDialog.Builder(this)
            .setTitle("Enter Details")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val startLetter = startLetterEdit.text.toString().trim().uppercase()
                val name = nameEdit.text.toString().trim()
                val amount = amountEdit.text.toString().trim()
                val selectedOption = dialogView.findViewById<RadioButton>(
                    radioGroup.checkedRadioButtonId
                )?.text.toString()
                val phoneNumber = phoneEdit.text.toString().trim()

                displayMessages(startLetter, selectedOption, name, amount, phoneNumber)
            }
            .setCancelable(false)
            .show()
    }

    private fun shiftDatesToYesterday(messages: Array<String>): Array<String> {
        val regex = Regex("""\b\d{1,2}/\d{1,2}/\d{2}\b""") // matches formats like 13/8/25
        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, -1)
        }
        val formatter = SimpleDateFormat("d/M/yy", Locale.getDefault())
        val yesterdayStr = formatter.format(yesterday.time)

        return messages.map { msg ->
            regex.replace(msg) { yesterdayStr }
        }.toTypedArray()
    }

    private fun displayMessages(
        startLetter: String,
        option: String,
        name: String,
        amount: String,
        phoneNumber: String,
    ) {
        val container = findViewById<LinearLayout>(R.id.messageContainer)
        container.removeAllViews()

        // Update the dates in the predefined messages before displaying
        val updatedMessages = shiftDatesToYesterday(predefinedMessages)

        // First 5 updated messages
        updatedMessages.forEachIndexed { index, msg ->
            val underlinedMsg = underlineSpecialParts("$startLetter$msg")
            container.addView(createMessageCard(underlinedMsg))

            // Add divider after 3rd message (index 2)
            if (index == 2) {
                container.addView(createTimeDivider())
            }

            // Add "unread message" after 5th message (index 4)
            if (index == 4) {
                container.addView(createUnreadDivider("1 unread message"))
            }
        }

        // 6th message depending on option
        val sixthMessage = if (option.equals("Pochi", ignoreCase = true)) {
            "${startLetter}HC91AM7BN Confirmed. Ksh${amount}.00 sent to $name on ${getDate()} at ${getTime()}. New M-PESA balance is Ksh16.80. Transaction cost, Ksh${transactionCost(amount)}.00. Amount you can transact within the day is 499,905.00. Sign up for Lipa Na M-PESA Till online https://m-pesaforbusiness.co.ke"
        } else {
            "${startLetter}HE0AOP3EY Confirmed. Ksh${amount}.00 sent to $name $phoneNumber on ${getDate()} at ${getTime()}. New M-PESA balance is Ksh46.80. Transaction cost, Ksh${transactionCost(amount)}.00. Amount you can transact within the day is 497,870.00. Earn interest daily on Ziidi MMF,Dial *334#\n"
        }

        container.addView(createMessageCard(underlineSpecialParts(sixthMessage)))
    }


    private fun transactionCost(amount: String): Int {
        // Extract numeric value (handles "Ksh30.00", "30", "30.00" etc.)
        val numericAmount = amount
            .replace("[^\\d.]".toRegex(), "") // remove non-numeric except dot
            .toDoubleOrNull() ?: return -1 // return -1 if invalid

        return when (numericAmount) {
            in 0.0..100.0 -> 0
            in 100.01..500.0 -> 7
            in 500.01..1000.0 -> 15
            in 1000.01..1500.0 -> 23
            in 1500.01..2500.0 -> 33
            in 2500.01..3500.0 -> 53
            in 3500.01..5000.0 -> 57
            else -> 5
        }
    }

    private fun getDate(): String {
        val formatter = SimpleDateFormat("d/M/yy", Locale.getDefault())
        return formatter.format(Date())
    }



    private fun createMessageCard(text: CharSequence): View {
        return TextView(this).apply {
            this.text = text
            textSize = 18f
            setLineSpacing(6f, 1.0f) // extra spacing between lines
            setPadding(24, 18, 24, 20)
            setBackgroundResource(R.drawable.bg_message_card)

            val params = LinearLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.9f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 10, 0, 0)
            layoutParams = params
        }
    }

    private fun getTime(): String {
        val calendar = Calendar.getInstance()
        val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return formatter.format(calendar.time)
    }

    private fun createTimeDivider(): TextView {
        // Get time 1 hour ago
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.HOUR_OF_DAY, -1)
        val formatter = SimpleDateFormat("hh:mm", Locale.getDefault())
        val time = formatter.format(calendar.time)

        return TextView(this).apply {
            text = time
            textSize = 12f
            setTextColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    Color.GRAY
                )
            )
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 20.dpToPx()
            params.bottomMargin = 20.dpToPx()
            layoutParams = params
        }
    }

    private fun createUnreadDivider(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    Color.GRAY
                )
            )
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 20.dpToPx()
            params.bottomMargin = 20.dpToPx()
            layoutParams = params
        }
    }

    private fun underlineSpecialParts(
        text: String,
        extraPhrases: List<String> = emptyList()
    ): SpannableString {
        val spannable = SpannableString(text)

        // Regex for numbers, dates, and USSD codes
        val numberPattern = "\\b\\d+(?:[.,]\\d+)?\\b".toRegex()
        val datePattern = "\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b".toRegex()
        val ussdPattern = "\\*\\d{1,3}#".toRegex()

        // Function to apply underline
        fun applyUnderline(pattern: Regex) {
            pattern.findAll(text).forEach { match ->
                spannable.setSpan(
                    UnderlineSpan(),
                    match.range.first,
                    match.range.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // Apply underline to detected types
        applyUnderline(numberPattern)
        applyUnderline(datePattern)
        applyUnderline(ussdPattern)

        // Apply underline to extra phrases provided externally
        for (phrase in extraPhrases) {
            val start = text.indexOf(phrase, ignoreCase = true)
            if (start >= 0) {
                spannable.setSpan(
                    UnderlineSpan(),
                    start,
                    start + phrase.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        return spannable
    }


    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()

}
