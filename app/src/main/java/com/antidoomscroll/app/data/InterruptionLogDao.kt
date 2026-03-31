package com.antidoomscroll.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface InterruptionLogDao {

    @Insert
    suspend fun insert(interruptionLog: InterruptionLog)

    /** Get total interruptions today */
    @Query("""
        SELECT COUNT(*) FROM interruption_logs 
        WHERE timestamp >= :startOfDay
    """)
    suspend fun getCountToday(startOfDay: Long): Int

    /** Get interruption count per app today (returns package name and count) */
    @Query("""
        SELECT packageName, COUNT(*) as count 
        FROM interruption_logs 
        WHERE timestamp >= :startOfDay 
        GROUP BY packageName 
        ORDER BY count DESC
    """)
    suspend fun getCountPerAppToday(startOfDay: Long): List<AppOpenCount>

    /** 
     * Get recent interruptions for a specific package within a time window.
     * Used for loop detection (3+ opens of same app in 2 minutes).
     */
    @Query("""
        SELECT COUNT(*) FROM interruption_logs 
        WHERE packageName = :packageName 
        AND timestamp >= :sinceTimestamp
    """)
    suspend fun getRecentCountForPackage(packageName: String, sinceTimestamp: Long): Int

    /** Update the action taken for the most recent interruption of a package */
    @Query("""
        UPDATE interruption_logs 
        SET actionTaken = :action 
        WHERE id = (
            SELECT id FROM interruption_logs 
            WHERE packageName = :packageName 
            ORDER BY timestamp DESC 
            LIMIT 1
        )
    """)
    suspend fun updateLastAction(packageName: String, action: String)
}

/** Simple data class for package name + count query results */
data class AppOpenCount(
    val packageName: String,
    val count: Int
)
