package com.antidoomscroll.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ActionLogDao {

    @Insert
    suspend fun insert(actionLog: ActionLog)

    /** Get count of actions completed today */
    @Query("""
        SELECT COUNT(*) FROM action_logs 
        WHERE timestamp >= :startOfDay
    """)
    suspend fun getCountToday(startOfDay: Long): Int

    /** Get the last N action texts (used to avoid repeating recent actions) */
    @Query("""
        SELECT actionText FROM action_logs 
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    suspend fun getRecentActionTexts(limit: Int = 5): List<String>

    /** Get all actions from today, ordered by most recent */
    @Query("""
        SELECT * FROM action_logs 
        WHERE timestamp >= :startOfDay 
        ORDER BY timestamp DESC
    """)
    suspend fun getTodayActions(startOfDay: Long): List<ActionLog>

    /** Get all actions ever, ordered by most recent */
    @Query("""
        SELECT * FROM action_logs 
        ORDER BY timestamp DESC
        LIMIT 100
    """)
    suspend fun getAllActions(): List<ActionLog>
}
