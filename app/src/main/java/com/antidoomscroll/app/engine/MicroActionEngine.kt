package com.antidoomscroll.app.engine

import android.content.Context
import android.content.SharedPreferences

/**
 * Micro Action Engine — provides intentional, reflective micro-actions.
 * All actions are hardcoded (no AI, no network). Each action is either:
 * - INPUT: requires the user to write something
 * - PHYSICAL: requires the user to do something and mark it done
 *
 * Avoids repeating the last 5 used actions using SharedPreferences.
 */
class MicroActionEngine(context: Context) {

    enum class ActionType { INPUT, PHYSICAL }

    data class MicroAction(
        val text: String,
        val type: ActionType
    )

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "micro_actions_prefs", Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_RECENT_ACTIONS = "recent_action_texts"
        private const val MAX_RECENT = 5

        /** High-quality, intentional micro-actions */
        val ALL_ACTIONS = listOf(
            // INPUT actions — reflective, action-oriented
            MicroAction("Write 1 thing you actually need to do right now", ActionType.INPUT),
            MicroAction("List 1 task you've been avoiding", ActionType.INPUT),
            MicroAction("Plan your next 10 minutes clearly", ActionType.INPUT),
            MicroAction("Write down what you'd rather be doing right now", ActionType.INPUT),
            MicroAction("Send 1 important message you've been putting off", ActionType.INPUT),
            MicroAction("Write 1 thing you're grateful for today", ActionType.INPUT),
            MicroAction("What's the most important thing to finish today?", ActionType.INPUT),
            MicroAction("Write a quick note to your future self", ActionType.INPUT),
            MicroAction("Name 1 real goal you're working toward", ActionType.INPUT),
            MicroAction("What would make today feel productive?", ActionType.INPUT),

            // PHYSICAL actions — quick, grounding, doable
            MicroAction("Take a deep breath and reset", ActionType.PHYSICAL),
            MicroAction("Drink a glass of water", ActionType.PHYSICAL),
            MicroAction("Stand up and stretch for 30 seconds", ActionType.PHYSICAL),
            MicroAction("Put your phone face-down for 60 seconds", ActionType.PHYSICAL),
            MicroAction("Look out a window for 10 seconds", ActionType.PHYSICAL),
            MicroAction("Do 5 pushups or squats", ActionType.PHYSICAL),
            MicroAction("Walk to another room and back", ActionType.PHYSICAL),
            MicroAction("Wash your face with cold water", ActionType.PHYSICAL),
            MicroAction("Tidy one thing near you", ActionType.PHYSICAL),
            MicroAction("Close your eyes and count to 10", ActionType.PHYSICAL),
        )
    }

    /**
     * Returns [count] random actions, avoiding the last 5 used.
     */
    fun getRandomActions(count: Int = 3): List<MicroAction> {
        val recentTexts = getRecentActionTexts()
        val available = ALL_ACTIONS.filter { it.text !in recentTexts }
        val pool = if (available.size < count) ALL_ACTIONS else available
        return pool.shuffled().take(count)
    }

    fun recordActionUsed(actionText: String) {
        val recent = getRecentActionTexts().toMutableList()
        recent.add(0, actionText)
        while (recent.size > MAX_RECENT) {
            recent.removeAt(recent.size - 1)
        }
        prefs.edit()
            .putString(KEY_RECENT_ACTIONS, recent.joinToString("|||"))
            .apply()
    }

    private fun getRecentActionTexts(): List<String> {
        val stored = prefs.getString(KEY_RECENT_ACTIONS, "") ?: ""
        if (stored.isBlank()) return emptyList()
        return stored.split("|||").filter { it.isNotBlank() }
    }

    fun getActionType(actionText: String): ActionType {
        return ALL_ACTIONS.find { it.text == actionText }?.type ?: ActionType.PHYSICAL
    }
}
