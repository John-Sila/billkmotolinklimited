package com.example.billkmotolinkltd

import android.app.Activity
import android.content.Intent
import android.os.Bundle

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

