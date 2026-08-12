package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DailyWorkLogEntity::class,
        TodoItemEntity::class,
        UserCareerProfileEntity::class,
        UserSettingsEntity::class,
        ExperienceVersionEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyWorkLogDao(): DailyWorkLogDao
    abstract fun todoItemDao(): TodoItemDao
    abstract fun userCareerProfileDao(): UserCareerProfileDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun experienceVersionDao(): ExperienceVersionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "work_log_resume_db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
