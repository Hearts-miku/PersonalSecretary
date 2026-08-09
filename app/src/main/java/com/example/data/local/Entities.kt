package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_work_logs")
data class DailyWorkLogEntity(
    @PrimaryKey val date: String, // Format: YYYY-MM-DD
    val rawNotes: String,
    val summaryMarkdown: String,
    val isSummarized: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "todo_items")
data class TodoItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val dateCreated: String, // YYYY-MM-DD
    val dateDue: String = "",
    val isCompleted: Boolean = false,
    val priority: String = "MEDIUM", // HIGH, MEDIUM, LOW
    val category: String = "Work",
    val sourceLogDate: String = ""
)

@Entity(tableName = "user_career_profile")
data class UserCareerProfileEntity(
    @PrimaryKey val id: Int = 1,
    val markdownContent: String,
    val skillsSummary: String = "",
    val workExperiences: String = "",
    val projectExperiences: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val apiKey: String = "",
    val baseUrl: String = "https://generativelanguage.googleapis.com/",
    val selectedModel: String = "gemini-3.5-flash",
    val autoSummaryHour: Int = 20,
    val autoSummaryMinute: Int = 0,
    val isAutoSummaryEnabled: Boolean = true,
    val resumeTemplate: String = "Modern", // Modern, Classic, Minimal
    val enableAntiInjection: Boolean = true,
    val enableAntiHallucination: Boolean = true,
    val lastScheduledRun: Long = 0L,
    val themeMode: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"
    val apiProvider: String = "GEMINI" // "GEMINI", "OPENAI", "ANTHROPIC", "CUSTOM"
)

@Entity(tableName = "experience_versions")
data class ExperienceVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "WORK" or "PROJECT"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val summaryNote: String = "AI 总结版本"
)
