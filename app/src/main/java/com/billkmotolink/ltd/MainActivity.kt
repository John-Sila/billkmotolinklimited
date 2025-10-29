package com.billkmotolink.ltd

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
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
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.billkmotolink.ltd.ui.Utility
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
import android.widget.TextView
import com.billkmotolink.ltd.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseUser

class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var auth: FirebaseAuth
    private lateinit var authStateListener: FirebaseAuth.AuthStateListener
    private val sharedPrefs by lazy { getSharedPreferences("AppPrefs", MODE_PRIVATE) }
    private var lastDestinationId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeWindow()
        initializeViews()
        initializeFirebase()
        initializeNavigation()
        setupNavigationListeners()
        setupAuthStateListener()
        setupLogoutListener()
        setupFabClickListener()
        loadInitialData()
        checkAndRequestSequentialPermissions()
    }

    private fun initializeWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    private fun initializeViews() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)
        window.statusBarColor = ContextCompat.getColor(this, R.color.d3)

    }

    private fun initializeFirebase() {
        auth = FirebaseAuth.getInstance()
    }

    private fun initializeNavigation() {
        // navController = findNavController(R.id.nav_host_fragment_content_main)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = navHostFragment.navController

        setupAppBarConfiguration()
        setupNavigationViews()
        loadLastOpenedPage()
    }

    private fun setupAppBarConfiguration() {
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
                R.id.nav_create_poll,
                R.id.nav_polls,
                R.id.nav_batteries,
                R.id.nav_manage_assets,
                R.id.nav_complaints,
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
            ), binding.drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    private fun setupNavigationViews() {
        binding.navDrawerContainer.navViewMain.setupWithNavController(navController)
    }

    private fun loadLastOpenedPage() {
        val lastSelectedPage = sharedPrefs.getInt("lastSelectedPage", R.id.nav_home)
        val destinationExists = try {
            navController.graph.findNode(lastSelectedPage) != null
        } catch (e: Exception) {
            false
        }

        if (destinationExists) {
            navController.navigate(lastSelectedPage)
        } else {
            navController.navigate(R.id.nav_home)
        }
    }

    private fun setupNavigationListeners() {
        setupMainNavigationListener()
        setupFooterNavigationListener()
        setupDestinationChangedListener()
    }

    private fun setupMainNavigationListener() {
        val navMainView: NavigationView = findViewById(R.id.nav_view_main)
        navMainView.setNavigationItemSelectedListener { menuItem ->
            saveLastSelectedPage(menuItem.itemId)
            navigateToDestination(menuItem.itemId)
            closeDrawer()
            true
        }
    }

    private fun setupFooterNavigationListener() {
        val navFooterView: NavigationView = findViewById(R.id.nav_view_footer)
        navFooterView.setNavigationItemSelectedListener { menuItem ->
            saveLastSelectedPage(menuItem.itemId)
            navigateToDestination(menuItem.itemId)
            closeDrawer()
            true
        }
    }

    private fun setupDestinationChangedListener() {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            lastDestinationId = destination.id
        }
    }

    private fun saveLastSelectedPage(itemId: Int) {
        sharedPrefs.edit { putInt("lastSelectedPage", itemId) }
    }

    private fun navigateToDestination(destinationId: Int) {
        navController.navigate(destinationId)
    }

    private fun closeDrawer() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun setupAuthStateListener() {
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            handleAuthStateChange(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(authStateListener)
    }

    private fun handleAuthStateChange(user: FirebaseUser?) {
        if (user == null) {
            handleUnauthenticatedUser()
        } else {
            handleAuthenticatedUser(user)
        }
    }

    private fun handleUnauthenticatedUser() {
        lockNavigationUI()
        navController.navigate(R.id.nav_login)
    }

    private fun handleAuthenticatedUser(user: FirebaseUser) {
        unlockNavigationUI()
        loadUserPermissions(user.uid)
        navigateToHomeIfComingFromLogin()
    }

    private fun navigateToHomeIfComingFromLogin() {
        if (lastDestinationId == R.id.nav_login) {
            navController.navigate(R.id.nav_home)
        }
    }

    private fun setupLogoutListener() {
        val navFooterView: NavigationView = findViewById(R.id.nav_view_footer)
        navFooterView.menu.findItem(R.id.nav_logout)?.setOnMenuItemClickListener {
            handleLogout()
            true
        }
    }

    private fun handleLogout() {
        showLogoutLoadingDialog(this@MainActivity)
        lifecycleScope.launch {
            delay(1000)
            FirebaseAuth.getInstance().signOut()
            Utility.postTrace("Logged out.")
            delay(3000)
            dismissLogoutLoadingDialog(this@MainActivity)
        }
    }

    private fun setupFabClickListener() {
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            lifecycleScope.launch {
                handleFabClickWithErrorHandling()
            }
        }
    }

    private suspend fun handleFabClickWithErrorHandling() {
        try {
            handleFabClick()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${e.message ?: "Unknown error"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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

        supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = findNavController(R.id.nav_host_fragment_content_main)
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

    private fun loadInitialData() {
        storeFcmToken()
        updateActiveMenuItem()
        fetchAndUploadFcmToken(FirebaseAuth.getInstance().currentUser?.uid.toString())
        loadVName()
        // createSnackBar()
    }
    private fun createSnackBar(string: String) {
        Snackbar.make(
            findViewById(android.R.id.content),
            string,
            Snackbar.LENGTH_LONG
        ).setAction("") {
            // Handle undo action
        }.show()
    }

    private fun loadVName() {
        // Local info
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        val appName = applicationInfo.loadLabel(packageManager).toString()

        val versionText = findViewById<TextView>(R.id.version_text)
        val outdatedText = findViewById<TextView>(R.id.outdated_text)
        versionText.text = "$appName v$versionName"

        // Fetch Firestore latest version async
        lifecycleScope.launch {
            try {
                val latestVersion = withContext(Dispatchers.IO) {
                    val docRef = FirebaseFirestore.getInstance()
                        .collection("general")
                        .document("general_variables")
                        .get()
                        .await()

                    docRef.getString("latest_version") // returns String? (nullable)
                }

                latestVersion?.let {
                    if (it != versionName) {
                        withContext(Dispatchers.Main) {
                            // versionText.append("\nUpdate available: $it")
                            val spannable = SpannableString("OUTDATED")
                            spannable.setSpan(ForegroundColorSpan(Color.RED), 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            // spannable.setSpan(TypefaceSpan("serif"), 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                            outdatedText.text = spannable
                            // Or show dialog/Toast/Snackbar to notify update
                        }
                    } else withContext(Dispatchers.Main) {
                        outdatedText.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // You may want to log or show a subtle error indicator
            }
        }
    }

    /*Initialization ends here*/












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






    /*location starts here*/
    private val allPermissions = mutableListOf<String>()
    private var currentPermissionIndex = 0
    private val PERMISSION_REQUEST_CODE = 2001
    private fun checkAndRequestSequentialPermissions() {
        allPermissions.clear()

        // Location base
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            allPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            allPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Background location (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            allPermissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        // Foreground Service - Location (Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            allPermissions.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }

        // Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            allPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestNextPermission() {
        if (currentPermissionIndex < allPermissions.size) {
            val permission = allPermissions[currentPermissionIndex]
            ActivityCompat.requestPermissions(
                this,
                arrayOf(permission),
                PERMISSION_REQUEST_CODE
            )
        } else {
            // startSafeLocationService()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isEmpty() || permissions.isEmpty()) {
                Log.w("Permissions", "Permission result came back empty. Possibly canceled or activity recreated.")
                return
            }

            val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED

            if (granted) {
                currentPermissionIndex++
                if (currentPermissionIndex < allPermissions.size) {
                    requestNextPermission()
                } else {
                    // startSafeLocationService()
                }
            } else {
                Log.w("Permissions", "Permission ${permissions[0]} denied. Will re-request on next launch.")
            }
        }
    }
    /*locations end here*/







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
                                R.id.nav_create_poll,
                                R.id.nav_polls,
                                R.id.nav_batteries,
                                R.id.nav_manage_assets,
                                R.id.nav_complaints,
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
                                R.id.nav_create_poll,
                                R.id.nav_polls,
                                R.id.nav_batteries,
                                R.id.nav_manage_assets,
                                R.id.nav_complaints,
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
                                R.id.nav_create_poll,
                                R.id.nav_polls,
                                R.id.nav_batteries,
                                R.id.nav_manage_assets,
                                R.id.nav_complaints,
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
                                R.id.nav_complain,
                                R.id.nav_polls,
                                R.id.nav_batteries,
                                R.id.nav_settings,
                                R.id.nav_logout
                            )
                        }
                        "HR" -> {
                            setMenuVisibility(
                                R.id.nav_home,
                                R.id.nav_hr,
                                R.id.nav_complain,
                                R.id.nav_polls,
                                R.id.nav_settings,
                                R.id.nav_logout
                            )
                        }
                    }

                    // after we know their rank, now listen for notifs
                    listenForNotifications(userRole)

                }
                else {
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
                val prefs = getSharedPreferences("notifications", MODE_PRIVATE)
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
        val channelId = "alerts"
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
            val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra("fromNotification", true)
        intent.putExtra("notificationTitle", title)
        intent.putExtra("notificationBody", body)

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

    override fun onResume() {
        super.onResume()
        // Also re-verify when user returns from settings or background
        checkAndRequestSequentialPermissions()
    }


}
