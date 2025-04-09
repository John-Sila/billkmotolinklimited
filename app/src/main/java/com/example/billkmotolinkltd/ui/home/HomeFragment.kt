package com.example.billkmotolinkltd.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.billkmotolinkltd.databinding.FragmentHomeBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.Executors
import android.util.Log
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.billkmotolinkltd.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var textViewWeather: TextView // Declare the TextView
    private lateinit var textViewUserName: TextView
    private lateinit var textViewSunday: TextView


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        requestLocation()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textViewWeather = view.findViewById(R.id.weather) // Initialize it
        textViewUserName = view.findViewById(R.id.userNameTextView)
        textViewSunday = view.findViewById(R.id.sundayStuff)

        // Call getWeather with some default lat/lon values
        getWeather(-1.3238, 36.9000) // begin with embakasi cords
        getUserData()

    }

    private fun getUserData() {
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
            val userId = currentUser.uid // Unique user ID
            val userRef = FirebaseFirestore.getInstance().collection("users").document(userId)

            userRef.get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val username = document.getString("userName") ?: ""
                        val pendingAmount = document.getDouble("pendingAmount") ?: 0.0
                        val targetAmount = document.getDouble("dailyTarget") ?: 0.0
                        val sundayTarget = document.getDouble("sundayTarget") ?: 0.0
                        val isWorkingOnSunday = document.getBoolean("isWorkingOnSunday") == true
                        val firstName = username.substringBefore(" ")
                        val isActive: Boolean = document.get("isActive") as Boolean
                        if (!isActive) {
                            FirebaseAuth.getInstance().signOut()
                            Toast.makeText(requireContext(), "You were logged out because this account has been flagged.", Toast.LENGTH_SHORT).show()
                        }


                        activity?.runOnUiThread {
                            if (!isAdded || view == null) return@runOnUiThread // Prevent crash
                            textViewUserName.visibility = View.VISIBLE
                            textViewUserName.text = "Hello ${firstName},"
                            binding.helloText.visibility = View.GONE



                            // Format the number with commas and two decimal places
                            val formattedPend = NumberFormat.getNumberInstance(Locale.US).apply {
                                minimumFractionDigits = 2
                                maximumFractionDigits = 2
                            }.format(pendingAmount)
                            binding.pendingAmountTextView.apply {
                                text = "Ksh. $formattedPend"
                                setTextColor(
                                    if (pendingAmount > 3000)
                                        ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                                    else
                                        ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
                                )
                            }


                            val formattedTarget = NumberFormat.getNumberInstance(Locale.US).apply {
                                minimumFractionDigits = 2
                                maximumFractionDigits = 2
                            }.format(targetAmount)
                            binding.expectedTargetTextView?.apply {
                                text = "Ksh. $formattedTarget"
                                setTextColor(
                                    ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark)
                                )
                            }

                            textViewSunday.visibility = View.VISIBLE
                            if (isWorkingOnSunday) {
                                val calendar = Calendar.getInstance()
                                val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                                val daysUntilSunday = (Calendar.SUNDAY - currentDayOfWeek + 7) % 7
                                calendar.add(Calendar.DAY_OF_YEAR, daysUntilSunday)

                                val nextSundayYear = calendar.get(Calendar.YEAR)
                                val nextSundayMonth = calendar.get(Calendar.MONTH) + 1 // Months are zero-based
                                val nextSundayDay = calendar.get(Calendar.DAY_OF_MONTH)

                                // Determine the description of how far away the next Sunday is
                                val dayDescription = when (daysUntilSunday) {
                                    0 -> "Today"
                                    1 -> "Tomorrow"
                                    else -> "In $daysUntilSunday days"
                                }

                                val formattedSundayTarget = NumberFormat.getNumberInstance(Locale.US).apply {
                                    minimumFractionDigits = 2
                                    maximumFractionDigits = 2
                                }.format(sundayTarget)

                                val sText = "You will be working this Sunday " +
                                        "(${dayDescription}) date ${nextSundayDay}/${nextSundayMonth}/${nextSundayYear} " +
                                        "with an expected target of Ksh. $formattedSundayTarget"
                                textViewSunday.apply {
                                    val formattedText = getString(
                                        R.string.custom_string,
                                        sText,
                                    )
                                    text = formattedText
                                    setTextColor(
                                        ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
                                    )
                                }
                            }
                            else {

                                val calendar = Calendar.getInstance()
                                val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                                val daysUntilSunday = (Calendar.SUNDAY - currentDayOfWeek + 7) % 7
                                calendar.add(Calendar.DAY_OF_YEAR, daysUntilSunday)
                                // Determine the description of how far away the next Sunday is
                                val dayDescription = when (daysUntilSunday) {
                                    0 -> "today"
                                    1 -> "tomorrow"
                                    else -> "on Sunday"
                                }
                                val sText = "You will not be working $dayDescription"
                                textViewSunday.apply {
                                    val formattedText = getString(
                                        R.string.custom_string,
                                        sText,
                                    )
                                    text = formattedText
                                    setTextColor(
                                        ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark)
                                    )
                                }
                            }




                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("UserInfo", "Error fetching user data: ${exception.message}")
                }

        } else {
            Log.e("UserInfo", "No user is logged in")
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "No user is logged in.", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun requestLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            getUserLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getUserLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                getWeather(location.latitude, location.longitude)
            } else {
                textViewWeather.text = "Unable to fetch location."
            }
        }
    }

    private fun getWeather(lat: Double, lon: Double) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        Executors.newSingleThreadExecutor().execute {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val json = response.body?.string()
                response.close() // Prevent memory leaks

                if (!json.isNullOrEmpty()) {
                    Log.d("WeatherAPI", "FULL API RESPONSE: $json")

                    val jsonObject = JSONObject(json)
                    val currentWeather = jsonObject.optJSONObject("current_weather")

                    if (currentWeather != null) {
                        val temperature = currentWeather.getDouble("temperature")
                        val windSpeed = currentWeather.getDouble("windspeed")
                        val windDirection = getWindDirection(currentWeather.getInt("winddirection"))
                        val weatherInfo = "It's currently $temperature°C with winds at $windSpeed km/hr due $windDirection."

                        activity?.runOnUiThread {
                            if (!isAdded || _binding == null) return@runOnUiThread
                            textViewWeather.text = weatherInfo
                        }
                    } else {
                        Log.e("WeatherError", "No current weather data found")
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "Unexpected API response format.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Empty response from API", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("WeatherError", "Exception: ${e.localizedMessage}")
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to get weather data. ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Function to determine wind direction
    private fun getWindDirection(degrees: Int): String {
        return when (degrees) {
            in 0..22, in 338..360 -> "North"
            in 23..67 -> "North East"
            in 68..112 -> "East"
            in 113..157 -> "South East"
            in 158..202 -> "South"
            in 203..247 -> "South West"
            in 248..292 -> "West"
            in 293..337 -> "North West"
            else -> "an unknown direction"
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}