package com.example.billkmotolinkltd.ui

import android.app.Application

class CrashListener : Application() {
    override fun onCreate() {
        super.onCreate()

        // Set our crash handler globally
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
    }
}
