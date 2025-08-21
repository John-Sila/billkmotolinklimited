package com.example.billkmotolinkltd

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.billkmotolinkltd.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.example.billkmotolinkltd.ui.Utility
import com.example.billkmotolinkltd.ui.approvals.ApprovalsFragment
import com.example.billkmotolinkltd.ui.clockins.ClockInFragment
import com.example.billkmotolinkltd.ui.clockouts.ClockoutsFragment
import com.example.billkmotolinkltd.ui.home.HomeFragment
import com.example.billkmotolinkltd.ui.settings.SettingsFragment
import com.example.billkmotolinkltd.ui.weeklyreports.WeeklyreportsFragment
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlin.random.Random
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext


class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var auth: FirebaseAuth
    private lateinit var authStateListener: FirebaseAuth.AuthStateListener
    private val sharedPrefs by lazy { getSharedPreferences("AppPrefs", Context.MODE_PRIVATE) }
    private var lastDestinationId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        navController = findNavController(R.id.nav_host_fragment_content_main)

        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navMainView: NavigationView = findViewById(R.id.nav_view_main)
        val navFooterView: NavigationView = findViewById(R.id.nav_view_footer)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home,
                R.id.nav_reports,
                R.id.nav_clockins,
                R.id.nav_clockouts,
                R.id.nav_corrections,
                R.id.nav_complain,
                R.id.nav_require,
                R.id.nav_user,
                R.id.nav_post_memo,
                R.id.nav_batteries,
                R.id.nav_manage_assets,
                R.id.nav_profiles,
                R.id.nav_approvals,
                R.id.nav_cashflows,
                R.id.nav_tracer,
                R.id.nav_restoration,
                R.id.nav_hr,
                R.id.nav_incidences,
                R.id.nav_dailyreports,
                R.id.nav_weeklyreports,
                R.id.nav_chatrooms,
                R.id.nav_mpesa,
                R.id.nav_settings,
                R.id.nav_logout

            ), drawerLayout
        )
        requestNotificationPermission()

        storeFcmToken()

        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navDrawerContainer.navViewMain.setupWithNavController(navController)

        // Load the last opened page
        val lastSelectedPage = sharedPrefs.getInt("lastSelectedPage", R.id.nav_home)
        // Check if the destination exists
        val destinationExists = try {
            navController.graph.findNode(lastSelectedPage) != null
        } catch (e: Exception) {
            false
        }
        // Navigate safely
        if (destinationExists) {
            navController.navigate(lastSelectedPage)
        } else {
            navController.navigate(R.id.nav_home)
        }

        // Save last clicked menu item
        navMainView.setNavigationItemSelectedListener { menuItem ->
            // if (menuItem.toString() == "") return
            sharedPrefs.edit().putInt("lastSelectedPage", menuItem.itemId).apply()
            navController.navigate(menuItem.itemId)
            drawerLayout.closeDrawer(GravityCompat.START) // Close drawer after selection
            true
        }
        navFooterView.setNavigationItemSelectedListener { menuItem ->
            sharedPrefs.edit().putInt("lastSelectedPage", menuItem.itemId).apply()
            navController.navigate(menuItem.itemId)
            drawerLayout.closeDrawer(GravityCompat.START) // Close drawer after selection
            true
        }

        // Capture the last visited destination
        navController.addOnDestinationChangedListener { _, destination, _ ->
            lastDestinationId = destination.id
        }

        // Firebase Auth State Listener
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            try {
                if (user == null) {
                    lockNavigationUI()
                    navController.navigate(R.id.nav_login)
                } else {
                    unlockNavigationUI()
                    loadUserPermissions(user.uid)
                    // Navigate to home only if coming from login
                    if (lastDestinationId == R.id.nav_login) {
                        navController.navigate(R.id.nav_home)
                    }
                }
            } finally {
            }
        }

        updateActiveMenuItem()

        fetchAndUploadFcmToken(FirebaseAuth.getInstance().currentUser?.uid.toString())

        // Set logout click listener
        navFooterView.menu.findItem(R.id.nav_logout)?.setOnMenuItemClickListener {
            showLogoutLoadingDialog(this@MainActivity)

            lifecycleScope.launch {
                delay(1000) // Wait 1 second before signing out
                FirebaseAuth.getInstance().signOut()
                Utility.postTrace("Logged out.")
                delay(3000) // Optional delay before dismissing
                dismissLogoutLoadingDialog(this@MainActivity)
            }

            true
        }


        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            lifecycleScope.launch {
                try {
                    handleFabClick()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,  // Fixed reference
                            "Error: ${e.message ?: "Unknown error"}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    }

    private fun showLoadingDialog(context: Context) {
        val intent = Intent(context, LoadingActivity::class.java)
        context.startActivity(intent)
    }

    private fun dismissLoadingDialog(context: Context) {
        val intent = Intent(context, LoadingActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        context.startActivity(intent)
    }

    private fun showLogoutLoadingDialog(context: Context) {
        val intent = Intent(context, LogoutLoadingActivity::class.java)
        context.startActivity(intent)
    }

    private fun dismissLogoutLoadingDialog(context: Context) {
        val intent = Intent(context, LogoutLoadingActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        context.startActivity(intent)
    }

    private suspend fun handleFabClick() {
        showLoadingDialog(this@MainActivity)
        val currentUser = FirebaseAuth.getInstance().currentUser ?: run {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    "User not logged in",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        val snapshot = withContext(Dispatchers.IO) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.uid)
                .get()
                .await()
        }

        val userRank = snapshot.getString("userRank") ?: ""
        val userName = snapshot.getString("userName") ?: "Unknown"
        val isClockedIn = snapshot.getBoolean("isClockedIn") ?: false

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController
        withContext(Dispatchers.Main) {
            dismissLoadingDialog(this@MainActivity)

            when {
                userRank == "Systems, IT" -> navController.navigate(R.id.nav_settings)
                userRank == "Admin" -> navController.navigate(R.id.nav_approvals)
                userRank == "CEO" -> navController.navigate(R.id.nav_weeklyreports)
                userRank == "HR" -> navController.navigate(R.id.nav_hr)
                userRank == "Rider" && isClockedIn -> navController.navigate(R.id.nav_clockouts)
                userRank == "Rider" && !isClockedIn -> navController.navigate(R.id.nav_clockins)
                else -> navController.navigate(R.id.nav_home)
            }

        }

    }

    private fun storeFcmToken() {
        val currentUser = Firebase.auth.currentUser
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUser?.uid ?: return@addOnCompleteListener)
                    .update("fcmToken", task.result)
            }
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                }
                shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS) -> {
                    showPermissionRationaleDialog()
                }
                else -> {
                    requestNotificationPermission()
                }
            }
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notification Permission Needed")
            .setMessage("This app needs notification permissions to alert you about important events")
            .setPositiveButton("Continue") { _, _ ->
                requestNotificationPermission()
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun requestNotificationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
            NOTIFICATION_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
            } else {
                // Permission denied
                Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 1001
    }

    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener(authStateListener)
    }

    override fun onStop() {
        super.onStop()
        auth.removeAuthStateListener(authStateListener)
    }

    private fun loadUserPermissions(userId: String) {
        val userRef = FirebaseFirestore.getInstance().collection("users").document(userId)

        userRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userRole = document.getString("userRank") ?: ""
                    val userName = document.getString("userName") ?: "an Unidentified user"
                    val mainMenu = findViewById<NavigationView>(R.id.nav_view_main).menu
                    val footerMenu = findViewById<NavigationView>(R.id.nav_view_footer).menu

                    // Default all items to hidden first
                    // Hide all items in the main menu
                    for (i in 0 until mainMenu.size()) {
                        mainMenu.getItem(i).isVisible = false
                    }

                    // Hide all items in the footer menu
                    for (i in 0 until footerMenu.size()) {
                        footerMenu.getItem(i).isVisible = false
                    }

                    when (userRole) {
                        "Admin" -> {
                            setMenuVisibility(
                                R.id.nav_home,
                                R.id.nav_reports,
                                R.id.nav_clockins,
                                R.id.nav_clockouts,
                                R.id.nav_corrections,
                                R.id.nav_require,
                                R.id.nav_user,
                                R.id.nav_post_memo,
                                R.id.nav_batteries,
                                R.id.nav_manage_assets,
                                R.id.nav_profiles,
                                R.id.nav_approvals,
                                R.id.nav_cashflows,
                                R.id.nav_tracer,
                                R.id.nav_hr,
                                R.id.nav_incidences,
                                R.id.nav_dailyreports,
                                R.id.nav_weeklyreports,
                                R.id.nav_settings,
                                R.id.nav_logout

                            )
                        }
                        "Systems, IT" -> {
                            setMenuVisibility(
                                R.id.nav_home,
                                R.id.nav_reports,
                                R.id.nav_clockins,
                                R.id.nav_clockouts,
                                R.id.nav_corrections,
                                R.id.nav_complain,
                                R.id.nav_require,
                                R.id.nav_user,
                                R.id.nav_post_memo,
                                R.id.nav_batteries,
                                R.id.nav_manage_assets,
                                R.id.nav_profiles,
                                R.id.nav_approvals,
                                R.id.nav_cashflows,
                                R.id.nav_tracer,
                                R.id.nav_restoration,
                                R.id.nav_hr,
                                R.id.nav_incidences,
                                R.id.nav_dailyreports,
                                R.id.nav_weeklyreports,
                                R.id.nav_chatrooms,
                                R.id.nav_mpesa,
                                R.id.nav_settings,
                                R.id.nav_logout
                            )
                        }
                        "CEO" -> {
                            setMenuVisibility(
                                R.id.nav_home,
                                R.id.nav_require,
                                R.id.nav_user,
                                R.id.nav_post_memo,
                                R.id.nav_batteries,
                                R.id.nav_manage_assets,
                                R.id.nav_profiles,
                                R.id.nav_approvals,
                                R.id.nav_cashflows,
                                R.id.nav_tracer,
                                R.id.nav_incidences,
                                R.id.nav_dailyreports,
                                R.id.nav_weeklyreports,
                                R.id.nav_settings,
                                R.id.nav_logout
                            )
                        }
                        "Rider" -> {
                            setMenuVisibility(
                                R.id.nav_home,
                                R.id.nav_reports,
                                R.id.nav_clockins,
                                R.id.nav_clockouts,
                                R.id.nav_corrections,
                                R.id.nav_batteries,
                                R.id.nav_settings,
                                R.id.nav_logout
                            )
                        }
                        "HR" -> {
                            setMenuVisibility(
                                R.id.nav_home,
                                R.id.nav_hr,
                                R.id.nav_settings,
                                R.id.nav_logout
                            )
                        }
                    }

                    // after we know their rank, now listen for notifs
                    listenForNotifications(userRole)

                } else {
                    Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error fetching user role: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setMenuVisibility(vararg itemIds: Int) {
        val mainMenu = findViewById<NavigationView>(R.id.nav_view_main).menu
        val footerMenu = findViewById<NavigationView>(R.id.nav_view_footer).menu

        itemIds.forEach { id ->
            val mainItem = mainMenu.findItem(id)
            val footerItem = footerMenu.findItem(id)

            if (mainItem != null) mainItem.isVisible = true
            if (footerItem != null) footerItem.isVisible = true
        }
    }

    private fun listenForNotifications(userRank: String) {
        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("notifications")
            .document("latest")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val data = snapshot.data ?: return@addSnapshotListener

                val roles = data["targetRoles"] as? List<*> ?: return@addSnapshotListener
                val title = data["title"] as? String ?: "Admin Alert"
                val body = data["body"] as? String ?: ""
                val prefs = getSharedPreferences("notifications", Context.MODE_PRIVATE)
                val lastSeen = prefs.getLong("last_seen_notification", 0L)
                val newTimestamp = snapshot.getTimestamp("timestamp")?.toDate()?.time ?: 0L

                if (newTimestamp > lastSeen && userRank in roles) {
                    try {
                        showLocalNotification(title, body)
                        prefs.edit() { putLong("last_seen_notification", newTimestamp) }
                    } catch (se: SecurityException) {
                        // User denied permission, handle gracefully (e.g., log or show a message)
                    }

                }
            }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showLocalNotification(title: String, body: String) {
        val channelId = "admin_alerts"
        val context = this

        // Create channel if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Admin Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for admins"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("fromNotification", true)
            putExtra("notificationTitle", title)
            putExtra("notificationBody", body)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.bml_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // For pre-Oreo devices
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Enables sound + vibration

        NotificationManagerCompat.from(context).notify(Random.nextInt(), builder.build())
    }

    fun fetchAndUploadFcmToken(userId: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .update("fcmToken", token)
                    .addOnSuccessListener {
                        println("FCM token updated for user $userId")
                    }
                    .addOnFailureListener { e ->
                        println("Failed to update FCM token: ${e.message}")
                    }
            }
        }
    }

    private fun logout() {
        auth.signOut()
    }

    private fun lockNavigationUI() {
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)

        findViewById<NavigationView>(R.id.nav_view_main).visibility = View.GONE
        findViewById<NavigationView>(R.id.nav_view_footer).visibility = View.GONE

        // Hide toolbar and FAB
        binding.appBarMain.toolbar.visibility = View.GONE
        binding.appBarMain.fab.visibility = View.GONE
    }


    private fun unlockNavigationUI() {
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        findViewById<NavigationView>(R.id.nav_view_main).visibility = View.VISIBLE
        var footer = findViewById<NavigationView>(R.id.nav_view_footer)
        footer.visibility = View.VISIBLE

        binding.appBarMain.toolbar.visibility = View.VISIBLE
        binding.appBarMain.fab.visibility = View.VISIBLE

        footer.menu.findItem(R.id.nav_login)?.isVisible = false
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (navController.currentDestination?.id == R.id.nav_home ||
            navController.currentDestination?.id == R.id.nav_login )
        {
            showExitConfirmationDialog()
        } else {
            super.onBackPressed()
        }
    }

    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit BillK")
            .setMessage("Are you sure you want to quit?")
            .setPositiveButton("Yes") { _, _ -> finishAffinity() }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun updateActiveMenuItem() {
        val lastSelectedItemId = sharedPrefs.getInt("lastSelectedPage", R.id.nav_home)

        val mainNavView = binding.navDrawerContainer.navViewMain
        val footerNavView = binding.navDrawerContainer.navViewFooter

        // Check if the item exists in the main menu
        if (mainNavView.menu.findItem(lastSelectedItemId) != null) {
            mainNavView.setCheckedItem(lastSelectedItemId)
        } else if (footerNavView.menu.findItem(lastSelectedItemId) != null) {
            footerNavView.setCheckedItem(lastSelectedItemId)
        }
    }


}
