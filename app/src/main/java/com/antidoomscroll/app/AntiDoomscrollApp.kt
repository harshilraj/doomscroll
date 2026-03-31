package com.antidoomscroll.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.antidoomscroll.app.data.AppDatabase

class AntiDoomscrollApp : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Background Service",
                NotificationManager.IMPORTANCE_NONE  // Completely hidden on most devices
            ).apply {
                description = ""
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "breakloop_bg"
    }
}
