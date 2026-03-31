package com.antidoomscroll.app.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.antidoomscroll.app.R
import com.antidoomscroll.app.data.AppDatabase
import com.antidoomscroll.app.service.AppMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stats Activity — shows clean, minimal statistics for today.
 * 
 * Displays:
 * - Total interruptions today
 * - Total actions completed today
 * - Per-app open counts (Instagram: X, YouTube: Y, etc.)
 * 
 * No charts — just clean text, premium dark theme.
 */
class StatsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DoomscrollStats"
    }

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        database = AppDatabase.getInstance(this)

        findViewById<View>(R.id.btnStatsBack).setOnClickListener {
            finish()
        }

        loadStats()
    }

    /**
     * Loads all stats from Room DB and populates the UI.
     */
    private fun loadStats() {
        activityScope.launch {
            try {
                val startOfDay = getStartOfDay()

                val interruptionCount = withContext(Dispatchers.IO) {
                    database.interruptionLogDao().getCountToday(startOfDay)
                }

                val actionCount = withContext(Dispatchers.IO) {
                    database.actionLogDao().getCountToday(startOfDay)
                }

                val appCounts = withContext(Dispatchers.IO) {
                    database.interruptionLogDao().getCountPerAppToday(startOfDay)
                }

                // Update UI
                findViewById<TextView>(R.id.tvInterruptionCount).text = "$interruptionCount"
                findViewById<TextView>(R.id.tvActionCount).text = "$actionCount"

                // Show per-app breakdown
                val appOpensContainer = findViewById<LinearLayout>(R.id.appOpensContainer)
                appOpensContainer.removeAllViews()

                if (appCounts.isEmpty() && interruptionCount == 0) {
                    findViewById<TextView>(R.id.tvNoData).visibility = View.VISIBLE
                } else {
                    findViewById<TextView>(R.id.tvNoData).visibility = View.GONE

                    appCounts.forEach { appCount ->
                        val appName = AppMonitorService.ALL_TARGET_PACKAGES[appCount.packageName]
                            ?: appCount.packageName

                        val row = createAppRow(appName, appCount.count)
                        appOpensContainer.addView(row)
                    }
                }

                // Animate content in
                val content = findViewById<View>(android.R.id.content)
                content.alpha = 0f
                content.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

                Log.d(TAG, "Stats loaded: interruptions=$interruptionCount, actions=$actionCount, apps=${appCounts.size}")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading stats", e)
            }
        }
    }

    /**
     * Creates a single row showing app name and count.
     */
    private fun createAppRow(appName: String, count: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(12))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = params
        }

        val nameView = TextView(this).apply {
            text = appName
            setTextColor(resources.getColor(R.color.text_primary, theme))
            textSize = 16f
            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            layoutParams = params
        }

        val countView = TextView(this).apply {
            text = "$count"
            setTextColor(resources.getColor(R.color.accent, theme))
            textSize = 20f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }

        row.addView(nameView)
        row.addView(countView)

        return row
    }

    private fun dp(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun getStartOfDay(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}
