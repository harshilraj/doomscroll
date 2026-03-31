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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskHistoryActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BreakloopHistory"
    }

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_history)

        database = AppDatabase.getInstance(this)

        findViewById<View>(R.id.btnHistoryBack).setOnClickListener {
            finish()
        }

        loadHistory()
    }

    private fun loadHistory() {
        activityScope.launch {
            try {
                val actions = withContext(Dispatchers.IO) {
                    database.actionLogDao().getAllActions()
                }

                val container = findViewById<LinearLayout>(R.id.historyContainer)
                val emptyView = findViewById<TextView>(R.id.tvHistoryEmpty)

                if (actions.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    container.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    container.visibility = View.VISIBLE
                    container.removeAllViews()

                    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

                    actions.forEachIndexed { index, action ->
                        val card = createHistoryCard(
                            action.actionText,
                            action.userInput,
                            dateFormat.format(Date(action.timestamp))
                        )
                        container.addView(card)

                        // Staggered fade in
                        card.alpha = 0f
                        card.translationY = 20f
                        card.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(250)
                            .setStartDelay((index * 50).toLong().coerceAtMost(500))
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    }
                }

                Log.d(TAG, "Loaded ${actions.size} completed actions")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading history", e)
            }
        }
    }

    private fun createHistoryCard(actionText: String, userInput: String?, timestamp: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_background)
            setPadding(dp(20), dp(16), dp(20), dp(16))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dp(10)
            layoutParams = params
        }

        // Action text
        val actionView = TextView(this).apply {
            text = actionText
            setTextColor(resources.getColor(R.color.text_primary, theme))
            textSize = 16f
        }
        card.addView(actionView)

        // User input (if any)
        if (!userInput.isNullOrBlank()) {
            val inputView = TextView(this).apply {
                text = "\"$userInput\""
                setTextColor(resources.getColor(R.color.accent, theme))
                textSize = 14f
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.topMargin = dp(6)
                layoutParams = params
            }
            card.addView(inputView)
        }

        // Timestamp
        val timeView = TextView(this).apply {
            text = timestamp
            setTextColor(resources.getColor(R.color.text_secondary, theme))
            textSize = 12f
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = dp(6)
            layoutParams = params
        }
        card.addView(timeView)

        return card
    }

    private fun dp(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}
