package com.antidoomscroll.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Boot Receiver — restarts monitoring when the device boots.
 * 
 * Note: The Accessibility Service is managed by Android itself and will
 * restart automatically if enabled. This receiver is here as a safety net
 * and for potential future use (e.g., starting other services on boot).
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DoomscrollBoot"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device booted — Starting AppMonitorService")
            try {
                if (context != null) {
                    val serviceIntent = Intent(context, com.antidoomscroll.app.service.AppMonitorService::class.java)
                    context.startForegroundService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AppMonitorService on boot", e)
            }
        }
    }
}
