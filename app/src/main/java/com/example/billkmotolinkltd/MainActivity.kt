package com.example.billkmotolinkltd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import androidx.core.view.get
import androidx.core.view.size

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

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        navController = findNavController(R.id.nav_host_fragment_content_main)

        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_reports, R.id.nav_clockouts, R.id.nav_user,
                R.id.nav_bike, R.id.nav_approvals, R.id.nav_hr, R.id.nav_incidences,
                R.id.nav_dailyreports, R.id.nav_weeklyreports, R.id.nav_data,
                R.id.nav_settings, R.id.nav_logout, R.id.nav_login
            ), drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Load the last opened page
        val lastSelectedPage = sharedPrefs.getInt("lastSelectedPage", R.id.nav_home)
        navController.navigate(lastSelectedPage)

        // Save last clicked menu item
        navView.setNavigationItemSelectedListener { menuItem ->
//            if (menuItem.toString() == "") return
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
        }

        updateActiveMenuItem()

        // Set logout click listener
        navView.menu.findItem(R.id.nav_logout)?.setOnMenuItemClickListener {
            logout()
            true
        }
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
                    val userRole = document.getString("userRank")
                    val menu = binding.navView.menu

                    // Default all items to hidden first
                    for (i in 0 until menu.size) {
                        menu[i].isVisible = false
                    }

                    when (userRole) {
                        "Admin" -> {
                            setMenuVisibility(
                                R.id.nav_home, R.id.nav_reports, R.id.nav_clockouts,
                                R.id.nav_approvals, R.id.nav_bike, R.id.nav_user,
                                R.id.nav_hr, R.id.nav_incidences, R.id.nav_dailyreports,
                                R.id.nav_weeklyreports, R.id.nav_data,
                                R.id.nav_settings, R.id.nav_logout
                            )
                        }
                        "CEO" -> {
                            setMenuVisibility(
                                R.id.nav_home, R.id.nav_approvals, R.id.nav_bike,
                                R.id.nav_user, R.id.nav_hr, R.id.nav_incidences,
                                R.id.nav_dailyreports, R.id.nav_weeklyreports, R.id.nav_data,
                                R.id.nav_settings, R.id.nav_logout
                            )
                        }
                        "Rider" -> {
                            setMenuVisibility(
                                R.id.nav_home, R.id.nav_reports,
                                R.id.nav_clockouts, R.id.nav_settings, R.id.nav_logout,

                            )
                        }
                        "HR" -> {
                            setMenuVisibility(
                                R.id.nav_home, R.id.nav_hr,
                                R.id.nav_settings, R.id.nav_logout
                            )
                        }
                    }
                } else {
                    Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error fetching user role: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setMenuVisibility(vararg itemIds: Int) {
        val menu = binding.navView.menu
        itemIds.forEach { menu.findItem(it).isVisible = true }
    }

    private fun logout() {
        auth.signOut()
    }

    private fun lockNavigationUI() {
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        binding.navView.visibility = View.GONE
        binding.appBarMain.toolbar.visibility = View.GONE
        binding.appBarMain.fab.visibility = View.GONE
    }

    private fun unlockNavigationUI() {
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        binding.navView.visibility = View.VISIBLE
        binding.appBarMain.toolbar.visibility = View.VISIBLE
        binding.appBarMain.fab.visibility = View.VISIBLE

        binding.navView.menu.findItem(R.id.nav_login)?.isVisible = false
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
        val navView: NavigationView = binding.navView
        val lastSelectedItemId = sharedPrefs.getInt("lastSelectedPage", R.id.nav_home) // Default to home
        if (lastSelectedItemId != 0) {
            navView.setCheckedItem(lastSelectedItemId) // Set the checked item
        }
    }

}
