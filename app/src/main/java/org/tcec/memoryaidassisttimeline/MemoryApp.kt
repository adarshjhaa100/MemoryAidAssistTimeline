package org.tcec.memoryaidassisttimeline

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MemoryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("MEMORY_CRASH", "FATAL CRASH on thread ${thread.name}", throwable)
            // Re-throw or let the default handler handle it (so app still crashes perceptibly)
            // But we want to ensure it's logged first.
        }
    }
}
