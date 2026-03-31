package com.antidoomscroll.app.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.antidoomscroll.app.R
import com.antidoomscroll.app.data.AppDatabase
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BreakloopMain"
        private const val PREFS_NAME = "breakloop_prefs"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_REINTERRUPT_MINUTES = "reinterrupt_minutes"
    }

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentPermissionStep = 0

    private lateinit var onboardingView: View
    private lateinit var permissionView: View
    private lateinit var homeView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        onboardingView = findViewById(R.id.onboardingView)
        permissionView = findViewById(R.id.permissionView)
        homeView = findViewById(R.id.homeView)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val onboardingDone = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

        if (!onboardingDone) {
            showOnboarding()
        } else if (allPermissionsGranted()) {
            showHome()
        } else {
            showPermissionFlow()
        }
    }

    override fun onResume() {
        super.onResume()
        if (permissionView.visibility == View.VISIBLE) {
            updatePermissionStep()
        }
        if (homeView.visibility == View.VISIBLE) {
            updateHomeStatus()
        }
    }

    // =============================
    // ONBOARDING
    // =============================

    private fun showOnboarding() {
        onboardingView.visibility = View.VISIBLE
        permissionView.visibility = View.GONE
        homeView.visibility = View.GONE

        onboardingView.alpha = 0f
        onboardingView.animate()
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .start()

        findViewById<MaterialButton>(R.id.btnGetStarted).setOnClickListener {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
            showPermissionFlow()
        }
    }

    // =============================
    // PERMISSION FLOW
    // =============================

    private fun showPermissionFlow() {
        onboardingView.visibility = View.GONE
        permissionView.visibility = View.VISIBLE
        homeView.visibility = View.GONE

        currentPermissionStep = when {
            !hasUsageAccess() -> 0
            !hasOverlayPermission() -> 1
            else -> {
                showHome()
                return
            }
        }
        updatePermissionStep()
    }

    private fun updatePermissionStep() {
        currentPermissionStep = when {
            !hasUsageAccess() -> 0
            !hasOverlayPermission() -> 1
            else -> {
                showHome()
                return
            }
        }

        val tvStep = findViewById<TextView>(R.id.tvStepIndicator)
        val tvTitle = findViewById<TextView>(R.id.tvPermissionTitle)
        val tvDesc = findViewById<TextView>(R.id.tvPermissionDesc)
        val tvStatus = findViewById<TextView>(R.id.tvPermissionStatus)
        val tvError = findViewById<TextView>(R.id.tvPermissionError)
        val btnEnable = findViewById<MaterialButton>(R.id.btnEnablePermission)
        val btnNext = findViewById<MaterialButton>(R.id.btnNextPermission)

        tvError.visibility = View.GONE
        tvStatus.visibility = View.GONE
        btnNext.visibility = View.GONE

        when (currentPermissionStep) {
            0 -> {
                tvStep.text = "STEP 1 OF 2"
                tvTitle.text = getString(R.string.permission_usage_title)
                tvDesc.text = getString(R.string.permission_usage_desc)

                if (hasUsageAccess()) {
                    tvStatus.visibility = View.VISIBLE
                    tvStatus.text = getString(R.string.permission_granted)
                    btnNext.visibility = View.VISIBLE
                    btnEnable.text = getString(R.string.permission_granted)
                    btnEnable.isEnabled = false
                } else {
                    btnEnable.text = getString(R.string.permission_enable)
                    btnEnable.isEnabled = true
                }

                btnEnable.setOnClickListener {
                    try {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (e: Exception) {
                        tvError.text = "Could not open settings. Please enable manually."
                        tvError.visibility = View.VISIBLE
                    }
                }
            }

            1 -> {
                tvStep.text = "STEP 2 OF 2"
                tvTitle.text = getString(R.string.permission_overlay_title)
                tvDesc.text = getString(R.string.permission_overlay_desc)

                if (hasOverlayPermission()) {
                    tvStatus.visibility = View.VISIBLE
                    tvStatus.text = getString(R.string.permission_granted)
                    btnNext.visibility = View.VISIBLE
                    btnNext.text = getString(R.string.permission_done)
                    btnEnable.text = getString(R.string.permission_granted)
                    btnEnable.isEnabled = false
                } else {
                    btnEnable.text = getString(R.string.permission_enable)
                    btnEnable.isEnabled = true
                }

                btnEnable.setOnClickListener {
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    } catch (e: Exception) {
                        tvError.text = "Could not open settings. Please enable manually."
                        tvError.visibility = View.VISIBLE
                    }
                }
            }
        }

        btnNext.setOnClickListener {
            if (allPermissionsGranted()) {
                showHome()
            } else {
                currentPermissionStep++
                updatePermissionStep()
            }
        }

        permissionView.alpha = 0f
        permissionView.animate()
            .alpha(1f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    // =============================
    // HOME SCREEN
    // =============================

    private fun showHome() {
        onboardingView.visibility = View.GONE
        permissionView.visibility = View.GONE
        homeView.visibility = View.VISIBLE

        homeView.alpha = 0f
        homeView.animate()
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()

        updateHomeStatus()

        // Start foreground monitor service
        val monitorIntent = Intent(this, com.antidoomscroll.app.service.AppMonitorService::class.java)
        startForegroundService(monitorIntent)

        // Navigation buttons
        findViewById<MaterialButton>(R.id.btnViewStats).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnSelectApps).setOnClickListener {
            startActivity(Intent(this, AppSelectionActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnCompletedActions).setOnClickListener {
            startActivity(Intent(this, TaskHistoryActivity::class.java))
        }

        // Permission status rows (tappable)
        findViewById<View>(R.id.permUsageRow).setOnClickListener {
            if (!hasUsageAccess()) {
                try { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } catch (_: Exception) {}
            }
        }

        findViewById<View>(R.id.permOverlayRow).setOnClickListener {
            if (!hasOverlayPermission()) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } catch (_: Exception) {}
            }
        }

        // Re-interruption setting
        setupReInterruptionSelector()
    }

    private fun setupReInterruptionSelector() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val currentMinutes = prefs.getInt(KEY_REINTERRUPT_MINUTES, 10)

        val btn5 = findViewById<MaterialButton>(R.id.btnReint5)
        val btn10 = findViewById<MaterialButton>(R.id.btnReint10)
        val btn15 = findViewById<MaterialButton>(R.id.btnReint15)
        val btnOff = findViewById<MaterialButton>(R.id.btnReintOff)

        fun highlightSelected(minutes: Int) {
            val buttons = listOf(btn5 to 5, btn10 to 10, btn15 to 15, btnOff to 0)
            buttons.forEach { (btn, value) ->
                if (value == minutes) {
                    btn.setTextColor(resources.getColor(R.color.accent, theme))
                    btn.alpha = 1f
                } else {
                    btn.setTextColor(resources.getColor(R.color.text_secondary, theme))
                    btn.alpha = 0.6f
                }
            }
        }

        highlightSelected(currentMinutes)

        val setMinutes = { minutes: Int ->
            prefs.edit().putInt(KEY_REINTERRUPT_MINUTES, minutes).apply()
            highlightSelected(minutes)
        }

        btn5.setOnClickListener { setMinutes(5) }
        btn10.setOnClickListener { setMinutes(10) }
        btn15.setOnClickListener { setMinutes(15) }
        btnOff.setOnClickListener { setMinutes(0) }
    }

    private fun updateHomeStatus() {
        val statusDot = findViewById<View>(R.id.statusDot)
        val tvStatusTitle = findViewById<TextView>(R.id.tvStatusTitle)
        val tvStatusDesc = findViewById<TextView>(R.id.tvStatusDesc)

        if (allPermissionsGranted()) {
            statusDot.setBackgroundResource(R.drawable.status_dot_active)
            tvStatusTitle.text = getString(R.string.home_monitoring_active)
            tvStatusDesc.text = getString(R.string.home_status_desc)
        } else {
            statusDot.setBackgroundResource(R.drawable.status_dot_inactive)
            tvStatusTitle.text = getString(R.string.home_monitoring_inactive)
            tvStatusDesc.text = getString(R.string.home_status_inactive_desc)
        }

        // Permission status indicators
        val tvUsage = findViewById<TextView>(R.id.tvPermUsageStatus)
        val tvOverlay = findViewById<TextView>(R.id.tvPermOverlayStatus)

        if (hasUsageAccess()) {
            tvUsage.text = getString(R.string.permission_granted)
            tvUsage.setTextColor(resources.getColor(R.color.success, theme))
        } else {
            tvUsage.text = getString(R.string.permission_not_granted)
            tvUsage.setTextColor(resources.getColor(R.color.error, theme))
        }

        if (hasOverlayPermission()) {
            tvOverlay.text = getString(R.string.permission_granted)
            tvOverlay.setTextColor(resources.getColor(R.color.success, theme))
        } else {
            tvOverlay.text = getString(R.string.permission_not_granted)
            tvOverlay.setTextColor(resources.getColor(R.color.error, theme))
        }

        // Load quick stats
        activityScope.launch {
            try {
                val db = AppDatabase.getInstance(this@MainActivity)
                val startOfDay = getStartOfDay()

                val interruptions = withContext(Dispatchers.IO) {
                    db.interruptionLogDao().getCountToday(startOfDay)
                }
                val actions = withContext(Dispatchers.IO) {
                    db.actionLogDao().getCountToday(startOfDay)
                }

                findViewById<TextView>(R.id.tvQuickInterruptions).text = "$interruptions"
                findViewById<TextView>(R.id.tvQuickActions).text = "$actions"
            } catch (e: Exception) {
                Log.e(TAG, "Error loading quick stats", e)
            }
        }
    }

    // =============================
    // PERMISSION CHECKS
    // =============================

    private fun hasUsageAccess(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun allPermissionsGranted(): Boolean {
        return hasUsageAccess() && hasOverlayPermission()
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
