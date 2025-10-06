package com.example.billkmotolinkltd.ui

import android.app.Application
// import org.maplibre.android.MapLibre
// import org.maplibre.android.WellKnownTileServer

class CrashListener : Application() {
    override fun onCreate() {
        super.onCreate()

        // Set our crash handler globally
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        /*maps*/
        /*MapLibre.getInstance(
            this,
            "dummy-api-key",   // not actually used for MapLibre tiles
            WellKnownTileServer.MapLibre
        )*/
    }
}
