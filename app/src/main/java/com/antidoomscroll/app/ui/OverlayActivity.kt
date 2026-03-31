package com.antidoomscroll.app.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.antidoomscroll.app.R
import com.antidoomscroll.app.data.AppDatabase
import com.antidoomscroll.app.service.AppMonitorService
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OverlayActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BreakloopOverlay"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_IS_LOOPING = "extra_is_looping"
        private const val BUTTON_DELAY_MS = 2000L

        @Volatile
        var isShowing = false
            private set

        /**
         * Singleton reference to the running AppMonitorService.
         * Set by the service itself so OverlayActivity can call startReInterruptTimer().
         */
        @Volatile
        var monitorService: AppMonitorService? = null
    }

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: AppDatabase
    private var currentPackageName: String = ""
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isShowing = true
        database = AppDatabase.getInstance(this)
        setContentView(R.layout.overlay_layout)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: ""
        val isLooping = intent.getBooleanExtra(EXTRA_IS_LOOPING, false)

        currentPackageName = packageName
        Log.d(TAG, "Overlay triggered for $appName (looping=$isLooping)")

        setupContent(packageName, appName, isLooping)
        animateIn()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Back button intercepted")
            }
        })

        // Emergency Backup Fix: Auto-remove the overlay unconditionally after 15 seconds
        handler.postDelayed({
            if (!isDestroyed && !isFinishing) {
                Log.w(TAG, "⏰ Emergency Timeout triggered! Auto-removing stuck Overlay.")
                finishAndRemoveTask()
            }
        }, 15000L)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val appName = intent?.getStringExtra(EXTRA_APP_NAME) ?: ""
        val isLooping = intent?.getBooleanExtra(EXTRA_IS_LOOPING, false) ?: false
        currentPackageName = packageName
        setupContent(packageName, appName, isLooping)
    }

    private fun setupContent(packageName: String, appName: String, isLooping: Boolean) {
        val tvTitle = findViewById<TextView>(R.id.tvOverlayTitle)
        val tvSubtitle = findViewById<TextView>(R.id.tvOverlaySubtitle)
        val tvDetectedApp = findViewById<TextView>(R.id.tvDetectedApp)
        val buttonsContainer = findViewById<View>(R.id.buttonsContainer)
        val btnContinue = findViewById<MaterialButton>(R.id.btnContinueAnyway)
        val btnBetter = findViewById<MaterialButton>(R.id.btnDoSomethingBetter)
        val btnExit = findViewById<MaterialButton>(R.id.btnExitApp)

        if (isLooping) {
            tvTitle.text = getString(R.string.overlay_loop_title)
            tvSubtitle.text = getString(R.string.overlay_loop_subtitle)
        } else {
            tvTitle.text = getString(R.string.overlay_title)
            tvSubtitle.text = getString(R.string.overlay_subtitle)
        }

        tvDetectedApp.text = appName.uppercase()
        tvDetectedApp.visibility = View.VISIBLE

        btnContinue.isEnabled = false
        btnBetter.isEnabled = false
        btnExit.isEnabled = false
        buttonsContainer.alpha = 0f
        buttonsContainer.translationY = 20f

        handler.postDelayed({
            btnContinue.isEnabled = true
            btnBetter.isEnabled = true
            btnExit.isEnabled = true
            animateButtonsIn(buttonsContainer)
        }, BUTTON_DELAY_MS)

        // ─── "Continue anyway" ───
        btnContinue.setOnClickListener {
            addTouchFeedback(it)
            Log.d(TAG, "User chose: Continue anyway ($appName)")
            activityScope.launch {
                try {
                    database.interruptionLogDao().updateLastAction(packageName, "continued")
                } catch (e: Exception) {
                    Log.e(TAG, "Error logging continue action", e)
                }
            }

            // Start the re-interruption timer directly on the service
            Log.d(TAG, "⏰ Requesting re-interrupt timer for $packageName")
            monitorService?.startReInterruptTimer(packageName)
                ?: Log.w(TAG, "⏰ monitorService is null — re-interrupt timer NOT started")

            finishAndRemoveTask()
        }

        // ─── "Do something better" ───
        btnBetter.setOnClickListener {
            addTouchFeedback(it)
            Log.d(TAG, "User chose: Do something better ($appName)")
            activityScope.launch {
                try {
                    database.interruptionLogDao().updateLastAction(packageName, "did_better")
                } catch (e: Exception) {
                    Log.e(TAG, "Error logging better action", e)
                }
            }
            val actionIntent = Intent(this, MicroActionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_PACKAGE_NAME, packageName)
            }
            startActivity(actionIntent)
            finishAndRemoveTask()
        }

        // ─── "Exit app" ───
        btnExit.setOnClickListener {
            addTouchFeedback(it)
            Log.d(TAG, "User chose: Exit app ($appName)")
            activityScope.launch {
                try {
                    database.interruptionLogDao().updateLastAction(packageName, "exited")
                } catch (e: Exception) {
                    Log.e(TAG, "Error logging exit action", e)
                }
            }
            goHome()
            finishAndRemoveTask()
        }
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    private fun animateIn() {
        val content = findViewById<View>(R.id.overlayContent)
        content.alpha = 0f
        content.scaleX = 0.96f
        content.scaleY = 0.96f

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(content, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(content, "scaleX", 0.96f, 1f),
                ObjectAnimator.ofFloat(content, "scaleY", 0.96f, 1f)
            )
            duration = 250
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun animateButtonsIn(buttonsContainer: View) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(buttonsContainer, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(buttonsContainer, "translationY", 20f, 0f)
            )
            duration = 280
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun addTouchFeedback(view: View) {
        view.animate()
            .scaleX(0.97f)
            .scaleY(0.97f)
            .setDuration(80)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }
            .start()
    }

    override fun onDestroy() {
        super.onDestroy()
        isShowing = false
        handler.removeCallbacksAndMessages(null)
        activityScope.cancel()
        Log.d(TAG, "OverlayActivity destroyed")
    }
}
