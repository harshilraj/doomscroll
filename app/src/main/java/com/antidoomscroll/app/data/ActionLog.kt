package com.antidoomscroll.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a completed micro-action.
 * Stored when user chooses "Do something better" and completes an action.
 */
@Entity(tableName = "action_logs")
data class ActionLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** The text of the action that was completed */
    val actionText: String,

    /** Optional user input (for INPUT-type actions) */
    val userInput: String? = null,

    /** Timestamp when the action was completed */
    val timestamp: Long = System.currentTimeMillis()
)
