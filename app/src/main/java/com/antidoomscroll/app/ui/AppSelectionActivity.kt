package com.antidoomscroll.app.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.antidoomscroll.app.R
import com.antidoomscroll.app.service.AppMonitorService

class AppSelectionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BreakloopAppSel"
        private const val PREFS_NAME = "breakloop_prefs"
        private const val KEY_SELECTED_APPS = "selected_apps"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_selection)

        findViewById<android.widget.ImageButton>(R.id.btnAppSelBack).setOnClickListener {
            finish()
        }

        buildAppList()
    }

    private fun buildAppList() {
        val container = findViewById<LinearLayout>(R.id.appListContainer)
        container.removeAllViews()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val selectedApps = prefs.getStringSet(KEY_SELECTED_APPS, null)
            ?: AppMonitorService.DEFAULT_SELECTED

        val currentSelection = selectedApps.toMutableSet()

        AppMonitorService.ALL_TARGET_PACKAGES.forEach { (packageName, appName) ->
            val row = createAppRow(appName, packageName, currentSelection.contains(packageName)) { isChecked ->
                if (isChecked) {
                    currentSelection.add(packageName)
                } else {
                    currentSelection.remove(packageName)
                }
                // Save immediately
                prefs.edit().putStringSet(KEY_SELECTED_APPS, currentSelection.toSet()).apply()
                Log.d(TAG, "Updated selection: $currentSelection")
            }
            container.addView(row)
        }
    }

    private fun createAppRow(
        appName: String,
        packageName: String,
        isSelected: Boolean,
        onToggle: (Boolean) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.card_background)
            setPadding(dp(20), dp(18), dp(20), dp(18))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dp(10)
            layoutParams = params
        }

        val nameView = TextView(this).apply {
            text = appName
            setTextColor(resources.getColor(R.color.text_primary, theme))
            textSize = 17f
            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = params
        }

        val toggle = Switch(this).apply {
            isChecked = isSelected
            setOnCheckedChangeListener { _, checked ->
                onToggle(checked)
            }
        }

        row.addView(nameView)
        row.addView(toggle)
        return row
    }

    private fun dp(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
