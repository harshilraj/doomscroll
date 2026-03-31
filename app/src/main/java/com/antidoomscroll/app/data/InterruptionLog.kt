package com.antidoomscroll.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing an interruption event.
 * Stored every time the overlay is triggered by a distracting app.
 */
@Entity(tableName = "interruption_logs")
data class InterruptionLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Package name of the detected distracting app */
    val packageName: String,

    /** Timestamp when the interruption occurred */
    val timestamp: Long = System.currentTimeMillis(),

    /** What the user chose: "continued" or "did_better" */
    val actionTaken: String = ""
)
