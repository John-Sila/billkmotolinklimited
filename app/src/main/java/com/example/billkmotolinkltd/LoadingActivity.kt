package com.example.billkmotolinkltd

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView

class LoadingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        finish() // This closes the loading screen when it receives an intent
    }
}

class LogoutLoadingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.logout_load)

        val message = intent.getStringExtra("EXTRA_MESSAGE") ?: getString(R.string.logging_out)
        val textView = findViewById<TextView>(R.id.logoutLoadText)
        textView.text = message
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        finish() // This closes the loading screen when it receives an intent
    }
}

