package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyWorkLogDao {
    @Query("SELECT * FROM daily_work_logs ORDER BY date DESC")
    fun getAllLogs(): Flow<List<DailyWorkLogEntity>>

    @Query("SELECT * FROM daily_work_logs ORDER BY date DESC")
    suspend fun getAllLogsOnce(): List<DailyWorkLogEntity>

    @Query("SELECT * FROM daily_work_logs WHERE date = :date LIMIT 1")
    suspend fun getLogByDate(date: String): DailyWorkLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(log: DailyWorkLogEntity)
}

@Dao
interface TodoItemDao {
    @Query("SELECT * FROM todo_items ORDER BY isCompleted ASC, CASE priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END ASC, id DESC")
    fun getAllTodos(): Flow<List<TodoItemEntity>>

    @Query("DELETE FROM todo_items WHERE sourceLogDate = :sourceDate AND isCompleted = 0")
    suspend fun deletePendingTodosForDate(sourceDate: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: TodoItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodos(todos: List<TodoItemEntity>)

    @Query("UPDATE todo_items SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun setCompleted(id: Int, isCompleted: Boolean)

    @Query("DELETE FROM todo_items WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface UserCareerProfileDao {
    @Query("SELECT * FROM user_career_profile WHERE id = 1 LIMIT 1")
    fun getProfileFlow(): Flow<UserCareerProfileEntity?>

    @Query("SELECT * FROM user_career_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfile(): UserCareerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserCareerProfileEntity)
}

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: UserSettingsEntity)
}

@Dao
interface ExperienceVersionDao {
    @Query("SELECT * FROM experience_versions WHERE type = :type ORDER BY timestamp DESC")
    fun getVersionsByTypeFlow(type: String): Flow<List<ExperienceVersionEntity>>

    @Query("SELECT * FROM experience_versions WHERE type = :type ORDER BY timestamp DESC")
    suspend fun getVersionsByType(type: String): List<ExperienceVersionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: ExperienceVersionEntity): Long

    @Query("DELETE FROM experience_versions WHERE id = :id")
    suspend fun deleteVersionById(id: Int)
}
