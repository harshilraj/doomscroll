package com.antidoomscroll.app.service

import android.app.KeyguardManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.antidoomscroll.app.AntiDoomscrollApp
import com.antidoomscroll.app.R
import com.antidoomscroll.app.data.AppDatabase
import com.antidoomscroll.app.data.InterruptionLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppMonitorService : Service() {

    companion object {
        private const val TAG = "BreakloopMonitor"
        private const val NOTIFICATION_ID = 1002
        private const val POLL_INTERVAL_MS = 1500L
        private const val LOOP_WINDOW_MS = 2 * 60 * 1000L
        private const val LOOP_THRESHOLD = 3

        private const val PREFS_NAME = "breakloop_prefs"
        private const val KEY_SELECTED_APPS = "selected_apps"
        private const val KEY_REINTERRUPT_MINUTES = "reinterrupt_minutes"

        val ALL_TARGET_PACKAGES = mapOf(
            "com.instagram.android" to "Instagram",
            "com.google.android.youtube" to "YouTube",
            "com.twitter.android" to "X (Twitter)",
            "com.reddit.frontpage" to "Reddit",
            "com.zhiliaoapp.musically" to "TikTok",
            "com.facebook.katana" to "Facebook",
            "com.snapchat.android" to "Snapchat"
        )

        val DEFAULT_SELECTED = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.twitter.android",
            "com.reddit.frontpage"
        )
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var database: AppDatabase
    private lateinit var prefs: SharedPreferences

    private var lastTriggeredPackage: String? = null

    // ── Re-interruption info ──
    private val KEY_CONTINUE_PACKAGE = "continue_package"


    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        database = AppDatabase.getInstance(this)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Register singleton for OverlayActivity to call startReInterruptTimer()
        com.antidoomscroll.app.ui.OverlayActivity.monitorService = this
        Log.d(TAG, "AppMonitorService created (singleton registered)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, AntiDoomscrollApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("")
            .setContentText("")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "AppMonitorService started — notification minimized")

        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        serviceScope.launch {
            Log.d(TAG, "Monitoring loop started")
            while (isActive) {
                checkForegroundApp()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun getSelectedPackages(): Set<String> {
        return prefs.getStringSet(KEY_SELECTED_APPS, null) ?: DEFAULT_SELECTED
    }

    /**
     * Gets the current foreground package from UsageStatsManager.
     */
    private fun getCurrentForegroundPackage(): String? {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (60 * 1000L)
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var latest: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (!event.packageName.isNullOrEmpty()) {
                    latest = event.packageName
                }
            }
        }
        return latest
    }

    private fun checkForegroundApp() {
        if (com.antidoomscroll.app.ui.OverlayActivity.isShowing) return

        val latestForegroundPackage = getCurrentForegroundPackage() ?: return
        val selectedPackages = getSelectedPackages()

        // State machine: if user exits the target app, cancel the pending alarm
        val reInterruptPackage = prefs.getString(KEY_CONTINUE_PACKAGE, null)
        if (reInterruptPackage != null && latestForegroundPackage != reInterruptPackage) {
            Log.d(TAG, "⏰ RE-INTERRUPT CANCELLED: user left $reInterruptPackage, now in $latestForegroundPackage")
            cancelReInterruptTimer()
        }

        // State machine: only trigger once per app-switch
        if (latestForegroundPackage == lastTriggeredPackage) return
        lastTriggeredPackage = latestForegroundPackage

        if (!selectedPackages.contains(latestForegroundPackage)) return

        val appName = ALL_TARGET_PACKAGES[latestForegroundPackage] ?: latestForegroundPackage
        Log.d(TAG, "🎯 TARGET DETECTED: $appName ($latestForegroundPackage)")

        processDetection(latestForegroundPackage, appName, System.currentTimeMillis())
    }

    private fun processDetection(packageName: String, appName: String, timestamp: Long) {
        serviceScope.launch {
            try {
                database.interruptionLogDao().insert(
                    InterruptionLog(packageName = packageName, timestamp = timestamp)
                )

                val recentCount = database.interruptionLogDao()
                    .getRecentCountForPackage(packageName, timestamp - LOOP_WINDOW_MS)

                val isLooping = recentCount >= LOOP_THRESHOLD
                Log.d(TAG, "Loop check: $appName opened $recentCount times in 2min (looping=$isLooping)")

                triggerOverlay(packageName, appName, isLooping)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing detection", e)
            }
        }
    }

    private fun triggerOverlay(packageName: String, appName: String, isLooping: Boolean) {
        val intent = Intent(this, com.antidoomscroll.app.ui.OverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(com.antidoomscroll.app.ui.OverlayActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(com.antidoomscroll.app.ui.OverlayActivity.EXTRA_APP_NAME, appName)
            putExtra(com.antidoomscroll.app.ui.OverlayActivity.EXTRA_IS_LOOPING, isLooping)
        }
        startActivity(intent)
    }

    // ═══════════════════════════════════════════════════════════
    // RE-INTERRUPTION TIMER (coroutine-based, reliable)
    // ═══════════════════════════════════════════════════════════

    /**
     * Called by OverlayActivity via broadcast when user taps "Continue anyway".
     * Schedules an exact AlarmManager trigger for re-interruption.
     */
    fun startReInterruptTimer(packageName: String) {
        val minutes = prefs.getInt(KEY_REINTERRUPT_MINUTES, 10)
        if (minutes <= 0) {
            Log.d(TAG, "⏰ RE-INTERRUPT: OFF (user set to 0)")
            return
        }

        val appName = ALL_TARGET_PACKAGES[packageName] ?: packageName
        val delayMs = minutes * 60 * 1000L
        val triggerTime = System.currentTimeMillis() + delayMs

        Log.d(TAG, "⏰ RE-INTERRUPT ALARM SCHEDULED: $appName → ${minutes}min ($delayMs ms) at $triggerTime")

        // Save in SharedPreferences (as requested)
        prefs.edit().putString(KEY_CONTINUE_PACKAGE, packageName).apply()

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(this, com.antidoomscroll.app.receiver.ReInterruptReceiver::class.java).apply {
            putExtra(com.antidoomscroll.app.receiver.ReInterruptReceiver.EXTRA_TARGET_PACKAGE, packageName)
        }

        // Create PendingIntent
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            999, // Unique request code
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel previous if any
        alarmManager.cancel(pendingIntent)

        // Set new alarm
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "⏰ Permission missing to set exact alarm.", e)
            // Fallback
            alarmManager.setWindow(
                android.app.AlarmManager.RTC_WAKEUP,
                triggerTime,
                60000L,
                pendingIntent
            )
        }
    }

    private fun cancelReInterruptTimer() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(this, com.antidoomscroll.app.receiver.ReInterruptReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            999,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        prefs.edit().remove(KEY_CONTINUE_PACKAGE).apply()
        Log.d(TAG, "⏰ RE-INTERRUPT ALARM CANCELLED internally")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        cancelReInterruptTimer()
        com.antidoomscroll.app.ui.OverlayActivity.monitorService = null
        serviceScope.cancel()
        Log.d(TAG, "AppMonitorService destroyed (singleton cleared)")
    }
}
