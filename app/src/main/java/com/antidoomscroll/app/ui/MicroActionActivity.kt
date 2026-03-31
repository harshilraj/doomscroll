package com.antidoomscroll.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.antidoomscroll.app.R
import com.antidoomscroll.app.data.ActionLog
import com.antidoomscroll.app.data.AppDatabase
import com.antidoomscroll.app.engine.MicroActionEngine
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MicroActionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BreakloopAction"
    }

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var engine: MicroActionEngine
    private lateinit var database: AppDatabase
    private var selectedActionText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_micro_action)

        engine = MicroActionEngine(this)
        database = AppDatabase.getInstance(this)

        findViewById<View>(R.id.btnBack).setOnClickListener {
            goHome()
        }

        showActionCards()
    }

    private fun showActionCards() {
        val container = findViewById<LinearLayout>(R.id.actionCardsContainer)
        container.removeAllViews()

        val actions = engine.getRandomActions(3)
        Log.d(TAG, "Showing ${actions.size} random actions")

        actions.forEachIndexed { index, action ->
            val cardView = createActionCard(action, index)
            container.addView(cardView)

            cardView.alpha = 0f
            cardView.translationY = 30f
            cardView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay((index * 100).toLong())
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun createActionCard(action: MicroActionEngine.MicroAction, index: Int): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.action_card_background)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dp(12)
            layoutParams = params
            isClickable = true
            isFocusable = true
        }

        val typeLabel = TextView(this).apply {
            text = if (action.type == MicroActionEngine.ActionType.INPUT) "✍️ WRITE" else "⚡ DO"
            setTextColor(resources.getColor(R.color.accent, theme))
            textSize = 11f
            letterSpacing = 0.1f
        }

        val actionText = TextView(this).apply {
            text = action.text
            setTextColor(resources.getColor(R.color.text_primary, theme))
            textSize = 18f
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = dp(8)
            layoutParams = params
        }

        card.addView(typeLabel)
        card.addView(actionText)

        card.setOnClickListener { view ->
            view.animate()
                .scaleX(0.97f)
                .scaleY(0.97f)
                .setDuration(80)
                .withEndAction {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                }
                .start()
            selectAction(action)
        }

        return card
    }

    private fun selectAction(action: MicroActionEngine.MicroAction) {
        selectedActionText = action.text
        Log.d(TAG, "Selected action: ${action.text} (type=${action.type})")

        findViewById<View>(R.id.actionCardsContainer).visibility = View.GONE

        val inputContainer = findViewById<View>(R.id.inputContainer)
        val physicalContainer = findViewById<View>(R.id.physicalContainer)

        if (action.type == MicroActionEngine.ActionType.INPUT) {
            inputContainer.visibility = View.VISIBLE
            physicalContainer.visibility = View.GONE

            findViewById<TextView>(R.id.tvSelectedAction).text = action.text

            inputContainer.alpha = 0f
            inputContainer.animate()
                .alpha(1f)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator())
                .start()

            findViewById<MaterialButton>(R.id.btnSubmitInput).setOnClickListener {
                val input = findViewById<EditText>(R.id.etActionInput).text.toString().trim()
                if (input.isNotEmpty()) {
                    completeAction(action.text, input)
                } else {
                    Toast.makeText(this, "Write something first", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            inputContainer.visibility = View.GONE
            physicalContainer.visibility = View.VISIBLE

            findViewById<TextView>(R.id.tvPhysicalAction).text = action.text

            physicalContainer.alpha = 0f
            physicalContainer.animate()
                .alpha(1f)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator())
                .start()

            findViewById<MaterialButton>(R.id.btnMarkDone).setOnClickListener {
                completeAction(action.text, null)
            }
        }
    }

    /**
     * Saves the completed action to DB, then navigates user to HOME screen.
     * CRITICAL FIX: Does NOT return to the distracting app.
     */
    private fun completeAction(actionText: String, userInput: String?) {
        Log.d(TAG, "Completing action: $actionText (input=$userInput)")

        activityScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.actionLogDao().insert(
                        ActionLog(
                            actionText = actionText,
                            userInput = userInput,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }

                engine.recordActionUsed(actionText)

                Toast.makeText(
                    this@MicroActionActivity,
                    getString(R.string.action_completed),
                    Toast.LENGTH_SHORT
                ).show()

                // Go to phone home screen instead of back to distracting app
                goHome()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving action", e)
                Toast.makeText(
                    this@MicroActionActivity,
                    "Error saving. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    private fun dp(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}
