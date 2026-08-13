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
    version = 6,
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

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Since Android supports SQLite 3.35.0+ on API 34+ but older devices might not,
                // the safest way in standard Room is recreating the tables, 
                // OR we can just execute DROP COLUMN since we use API 24+? 
                // SQLite 3.35.0 introduced DROP COLUMN. For older versions, Room requires recreating the table.
                // However, since we just need to keep user data intact without crashes, and Room 2.7+ handles
                // drops if we use AutoMigration. But we are doing manual migration.
                
                // 1. Recreate todo_items
                db.execSQL("CREATE TABLE IF NOT EXISTS `todo_items_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `dateCreated` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `priority` TEXT NOT NULL, `category` TEXT NOT NULL, `sourceLogDate` TEXT NOT NULL)")
                db.execSQL("INSERT INTO `todo_items_new` (`id`, `title`, `description`, `dateCreated`, `isCompleted`, `priority`, `category`, `sourceLogDate`) SELECT `id`, `title`, `description`, `dateCreated`, `isCompleted`, `priority`, `category`, `sourceLogDate` FROM `todo_items`")
                db.execSQL("DROP TABLE `todo_items`")
                db.execSQL("ALTER TABLE `todo_items_new` RENAME TO `todo_items`")

                // 2. Recreate user_career_profile
                db.execSQL("CREATE TABLE IF NOT EXISTS `user_career_profile_new` (`id` INTEGER NOT NULL, `markdownContent` TEXT NOT NULL, `workExperiences` TEXT NOT NULL, `projectExperiences` TEXT NOT NULL, `lastUpdated` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("INSERT INTO `user_career_profile_new` (`id`, `markdownContent`, `workExperiences`, `projectExperiences`, `lastUpdated`) SELECT `id`, `markdownContent`, `workExperiences`, `projectExperiences`, `lastUpdated` FROM `user_career_profile`")
                db.execSQL("DROP TABLE `user_career_profile`")
                db.execSQL("ALTER TABLE `user_career_profile_new` RENAME TO `user_career_profile`")

                // 3. Recreate user_settings
                db.execSQL("CREATE TABLE IF NOT EXISTS `user_settings_new` (`id` INTEGER NOT NULL, `apiKey` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, `selectedModel` TEXT NOT NULL, `themeMode` TEXT NOT NULL, `apiProvider` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("INSERT INTO `user_settings_new` (`id`, `apiKey`, `baseUrl`, `selectedModel`, `themeMode`, `apiProvider`) SELECT `id`, `apiKey`, `baseUrl`, `selectedModel`, `themeMode`, `apiProvider` FROM `user_settings`")
                db.execSQL("DROP TABLE `user_settings`")
                db.execSQL("ALTER TABLE `user_settings_new` RENAME TO `user_settings`")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "work_log_resume_db"
                ).addMigrations(MIGRATION_5_6).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
