package com.antidoomscroll.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for Anti-Doomscroll.
 * Stores action logs and interruption logs locally.
 * No cloud sync — fully offline.
 */
@Database(
    entities = [ActionLog::class, InterruptionLog::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun actionLogDao(): ActionLogDao
    abstract fun interruptionLogDao(): InterruptionLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "antidoomscroll_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
