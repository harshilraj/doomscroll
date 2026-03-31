package com.antidoomscroll.app.receiver

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.antidoomscroll.app.service.AppMonitorService
import com.antidoomscroll.app.ui.OverlayActivity

class ReInterruptReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BreakloopAlarm"
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
        if (targetPackage.isNullOrEmpty()) {
            Log.e(TAG, "⏰ Alarm fired but no target package provided")
            return
        }

        val appName = AppMonitorService.ALL_TARGET_PACKAGES[targetPackage] ?: targetPackage
        Log.d(TAG, "⏰ ALARM FIRED: checking re-interruption for $appName")

        // 1. Check Screen State
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            Log.d(TAG, "⏰ Screen is OFF. Skipping interruption.")
            return
        }

        // 2. Check Usage Access Permission
        if (!hasUsageStatsPermission(context)) {
            Log.w(TAG, "⏰ Usage access permission not granted. Skipping.")
            return
        }

        // 3. Get Current Foreground App
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        
        // As requested: query for last 15 seconds
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 15000L,
            now
        )

        if (usageStats.isNullOrEmpty()) {
            Log.d(TAG, "⏰ No usage stats found in the last 15 seconds. Skipping.")
            return
        }

        // Sort by lastTimeUsed DESC and pick top result
        val currentForegroundApp = usageStats
            .filter { it.packageName != null && !it.packageName.startsWith("com.android.systemui") }
            .maxByOrNull { it.lastTimeUsed }?.packageName

        Log.d(TAG, "⏰ Detected Foreground App: $currentForegroundApp")
        Log.d(TAG, "⏰ Expected Target App: $targetPackage")

        // 4. Compare and Trigger
        if (currentForegroundApp == targetPackage) {
            Log.d(TAG, "⏰ MATCH SUCCESS: User is STILL inside $appName! Triggering overlay...")
            
            // Check if overlay is already showing
            if (OverlayActivity.isShowing) {
                Log.d(TAG, "⏰ Overlay already showing. Skipping trigger.")
                return
            }

            // Trigger OverlayActivity directly
            val overlayIntent = Intent(context, OverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(OverlayActivity.EXTRA_PACKAGE_NAME, targetPackage)
                putExtra(OverlayActivity.EXTRA_APP_NAME, appName)
                putExtra(OverlayActivity.EXTRA_IS_LOOPING, false)
            }
            context.startActivity(overlayIntent)
        } else {
            Log.d(TAG, "⏰ MATCH FAILED: User left $appName. Doing nothing.")
        }
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
