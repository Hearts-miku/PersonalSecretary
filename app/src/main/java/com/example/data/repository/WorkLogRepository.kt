package com.example.data.repository

import android.content.Context
import com.example.data.ai.GeminiRepository
import com.example.data.local.*
import com.example.data.markdown.MarkdownFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WorkLogRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val logDao = db.dailyWorkLogDao()
    private val todoDao = db.todoItemDao()
    private val profileDao = db.userCareerProfileDao()
    private val settingsDao = db.userSettingsDao()
    private val versionDao = db.experienceVersionDao()

    val markdownManager = MarkdownFileManager(context)
    val aiRepository = GeminiRepository()

    // Flows for UI
    val allLogsFlow: Flow<List<DailyWorkLogEntity>> = logDao.getAllLogs()
    val allTodosFlow: Flow<List<TodoItemEntity>> = todoDao.getAllTodos()
    val profileFlow: Flow<UserCareerProfileEntity?> = profileDao.getProfileFlow()
    val settingsFlow: Flow<UserSettingsEntity?> = settingsDao.getSettingsFlow().map {
        it?.copy(apiKey = com.example.data.local.CryptoManager.decode(it.apiKey))
    }
    val workVersionsFlow: Flow<List<ExperienceVersionEntity>> = versionDao.getVersionsByTypeFlow("WORK")
    val projectVersionsFlow: Flow<List<ExperienceVersionEntity>> = versionDao.getVersionsByTypeFlow("PROJECT")

    fun getTodayString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
    }

    suspend fun getSettings(): UserSettingsEntity = withContext(Dispatchers.IO) {
        val s = settingsDao.getSettings() ?: UserSettingsEntity().also { settingsDao.saveSettings(it) }
        s.copy(apiKey = com.example.data.local.CryptoManager.decode(s.apiKey))
    }

    suspend fun saveSettings(settings: UserSettingsEntity) = withContext(Dispatchers.IO) {
        settingsDao.saveSettings(settings.copy(apiKey = com.example.data.local.CryptoManager.encode(settings.apiKey)))
    }

    suspend fun restoreLogsFromMarkdownIfNeeded() = withContext(Dispatchers.IO) {
        val existingLogs = logDao.getAllLogsOnce()
        if (existingLogs.isEmpty()) {
            val files = markdownManager.listAllDailySummaryFiles()
            for (file in files) {
                val dateStr = file.nameWithoutExtension
                if (dateStr.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) {
                    val content = file.readText()
                    if (content.isNotBlank()) {
                        logDao.insertOrUpdate(
                            DailyWorkLogEntity(
                                date = dateStr,
                                rawNotes = "",
                                summaryMarkdown = content,
                                isSummarized = true,
                                updatedAt = file.lastModified()
                            )
                        )
                    }
                }
            }
        }

        val existingProfile = profileDao.getProfile()
        if (existingProfile == null || existingProfile.markdownContent.isBlank()) {
            val profileContent = markdownManager.getCareerProfileContent()
            val workContent = markdownManager.getWorkExperiencesContent()
            val projectContent = markdownManager.getProjectExperiencesContent()

            if (profileContent.isNotBlank() || workContent.isNotBlank() || projectContent.isNotBlank()) {
                profileDao.insertOrUpdateProfile(
                    existingProfile?.copy(
                        markdownContent = profileContent.ifBlank { existingProfile.markdownContent },
                        workExperiences = workContent.ifBlank { existingProfile.workExperiences },
                        projectExperiences = projectContent.ifBlank { existingProfile.projectExperiences },
                        lastUpdated = System.currentTimeMillis()
                    ) ?: UserCareerProfileEntity(
                        id = 1,
                        markdownContent = profileContent,
                        workExperiences = workContent,
                        projectExperiences = projectContent,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun getSavedResume(): String = withContext(Dispatchers.IO) {
        markdownManager.getGeneratedResumeContent()
    }

    suspend fun saveResume(content: String) = withContext(Dispatchers.IO) {
        markdownManager.writeGeneratedResume(content)
    }

    // --- Page 1: Raw Input Handling ---

    suspend fun addRawNote(input: String, dateStr: String = getTodayString()) = withContext(Dispatchers.IO) {
        if (input.isBlank()) return@withContext

        // 1. Write to Type 2 Markdown File (temp/raw_notes.md)
        markdownManager.appendRawNote(input, dateStr)

        // 2. Update or insert in Room DB
        val existing = logDao.getLogByDate(dateStr)
        val newRaw = if (existing != null && existing.rawNotes.isNotBlank()) {
            "${existing.rawNotes}\n• $input"
        } else {
            "• $input"
        }

        val updatedEntity = DailyWorkLogEntity(
            date = dateStr,
            rawNotes = newRaw,
            summaryMarkdown = existing?.summaryMarkdown ?: "",
            isSummarized = false,
            updatedAt = System.currentTimeMillis()
        )
        logDao.insertOrUpdate(updatedEntity)
    }

    // --- AI Consolidation Engine (Manual or Scheduled) ---

    suspend fun triggerAISummarize(
        targetDate: String = getTodayString(),
        onProgress: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            onProgress("正在读取待处理工作记录...")
            val logEntity = logDao.getLogByDate(targetDate)
            val rawContent = logEntity?.rawNotes.orEmpty()

            if (rawContent.isBlank()) {
                return@withContext Result.failure(Exception("【$targetDate】没有待整理的原始输入数据"))
            }

            val settings = getSettings()

            // 1. AI Summarize Daily Work
            onProgress("AI 正在提炼【$targetDate】工作日志与总结...")
            val summaryRes = aiRepository.summarizeDailyWork(targetDate, rawContent, settings)
            if (summaryRes.isFailure) {
                return@withContext Result.failure(summaryRes.exceptionOrNull() ?: Exception("AI 总结失败"))
            }
            val summaryMarkdown = summaryRes.getOrNull().orEmpty()
            if (summaryMarkdown.isBlank()) {
                return@withContext Result.failure(Exception("AI 返回的总结为空，本次提炼中断。"))
            }

            // Write to Type 1 MD File (worklogs/YYYY-MM-DD.md)
            markdownManager.writeDailySummary(targetDate, summaryMarkdown)

            // Update Room
            logDao.insertOrUpdate(
                DailyWorkLogEntity(
                    date = targetDate,
                    rawNotes = rawContent,
                    summaryMarkdown = summaryMarkdown,
                    isSummarized = true,
                    updatedAt = System.currentTimeMillis()
                )
            )

            // 2. Extract Todos
            onProgress("AI 正在自动识别待办事项...")
            val todosRes = aiRepository.extractTodos(targetDate, rawContent, settings)
            if (todosRes.isSuccess) {
                val extractedList = todosRes.getOrDefault(emptyList())
                val entities = extractedList.map {
                    TodoItemEntity(
                        title = it.title,
                        description = it.description,
                        dateCreated = targetDate,
                        priority = it.priority,
                        category = it.category,
                        sourceLogDate = targetDate
                    )
                }
                if (entities.isNotEmpty()) {
                    todoDao.deletePendingTodosForDate(targetDate)
                    todoDao.insertTodos(entities)
                }
            }

            // 3. AI Evaluate Career Profile Update
            onProgress("AI 正在评估是否需要更新【用户职业履历】...")
            val currentProfileText = markdownManager.getCareerProfileContent()
            val evalRes = aiRepository.evaluateAndUpdateCareerProfile(currentProfileText, summaryMarkdown, settings)
            if (evalRes.isSuccess) {
                val evalObj = evalRes.getOrNull()
                if (evalObj != null && evalObj.shouldUpdate && evalObj.updatedProfileMarkdown.isNotBlank()) {
                    onProgress("检测到新的关键技能/产出，正在更新职业履历文档...")
                    val currentProfile = profileDao.getProfile() ?: UserCareerProfileEntity(id = 1, markdownContent = evalObj.updatedProfileMarkdown)
                    
                    markdownManager.writeCareerProfile(evalObj.updatedProfileMarkdown)
                    
                    // Save version backup
                    versionDao.insertVersion(
                        ExperienceVersionEntity(
                            type = "PROFILE",
                            content = currentProfile.markdownContent,
                            timestamp = System.currentTimeMillis(),
                            summaryNote = "AI 评估后自动更新"
                        )
                    )

                    markdownManager.writeCareerProfile(evalObj.updatedProfileMarkdown)
                    profileDao.insertOrUpdateProfile(
                        currentProfile.copy(
                            markdownContent = evalObj.updatedProfileMarkdown,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                } else {
                    onProgress("工作履历评估完毕（无须大幅调整履历文档）")
                }
            } else {
                onProgress("⚠️ AI 未能处理职业履历评估，已跳过")
            }

            // 4. Clear Type 2 temp raw notes for target date only
            onProgress("整理完毕，正在清理临时原始输入文档...")
            markdownManager.removeRawNotesForDate(targetDate)

            Result.success("【$targetDate】AI 工作日志整理完成！已更新总结、待办列表与职业文档。")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Page 4: Resume & Profile Generation ---

    suspend fun generateResume(templateStyle: String): Result<String> = withContext(Dispatchers.IO) {
        val profileMd = markdownManager.getCareerProfileContent()
        val settings = getSettings()
        aiRepository.generateUserResume(profileMd, templateStyle, settings)
    }

    private suspend fun getRecentLogsContext(): String {
        val maxChars = 25000
        var currentChars = 0
        return logDao.getAllLogsOnce()
            .filter { it.summaryMarkdown.isNotBlank() }
            .takeWhile { log ->
                val length = log.summaryMarkdown.length + log.date.length + 6
                if (currentChars + length > maxChars) {
                    false
                } else {
                    currentChars += length
                    true
                }
            }
            .joinToString("\n\n") { "### ${it.date}\n${it.summaryMarkdown}" }
    }

    suspend fun generateWorkExperiences(): Result<String> = withContext(Dispatchers.IO) {
        val profileMd = markdownManager.getCareerProfileContent()
        val recentLogs = getRecentLogsContext()
        val combinedContext = "$profileMd\n\n## 近期日常工作日志总结概览\n$recentLogs"
        val settings = getSettings()
        val current = profileDao.getProfile() ?: UserCareerProfileEntity(id = 1, markdownContent = profileMd)

        // Save old version if exists and no versions saved yet
        val existingVersions = versionDao.getVersionsByType("WORK")
        if (current.workExperiences.isNotBlank() && existingVersions.isEmpty()) {
            versionDao.insertVersion(
                ExperienceVersionEntity(
                    type = "WORK",
                    content = current.workExperiences,
                    timestamp = current.lastUpdated,
                    summaryNote = "初始备份版本"
                )
            )
        }

        val res = aiRepository.generateWorkExperiences(combinedContext, settings)
        if (res.isSuccess) {
            val content = res.getOrDefault("")
            markdownManager.writeWorkExperiences(content)
            profileDao.insertOrUpdateProfile(current.copy(workExperiences = content, lastUpdated = System.currentTimeMillis()))
            // Save new version
            versionDao.insertVersion(
                ExperienceVersionEntity(
                    type = "WORK",
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    summaryNote = "AI 提炼版本"
                )
            )
        }
        res
    }

    suspend fun generateProjectExperiences(): Result<String> = withContext(Dispatchers.IO) {
        val profileMd = markdownManager.getCareerProfileContent()
        val recentLogs = getRecentLogsContext()
        val combinedContext = "$profileMd\n\n## 近期日常工作日志总结概览\n$recentLogs"
        val settings = getSettings()
        val current = profileDao.getProfile() ?: UserCareerProfileEntity(id = 1, markdownContent = profileMd)

        // Save old version if exists and no versions saved yet
        val existingVersions = versionDao.getVersionsByType("PROJECT")
        if (current.projectExperiences.isNotBlank() && existingVersions.isEmpty()) {
            versionDao.insertVersion(
                ExperienceVersionEntity(
                    type = "PROJECT",
                    content = current.projectExperiences,
                    timestamp = current.lastUpdated,
                    summaryNote = "初始备份版本"
                )
            )
        }

        val res = aiRepository.generateProjectExperiences(combinedContext, settings)
        if (res.isSuccess) {
            val content = res.getOrDefault("")
            markdownManager.writeProjectExperiences(content)
            profileDao.insertOrUpdateProfile(current.copy(projectExperiences = content, lastUpdated = System.currentTimeMillis()))
            // Save new version
            versionDao.insertVersion(
                ExperienceVersionEntity(
                    type = "PROJECT",
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    summaryNote = "AI 提取版本"
                )
            )
        }
        res
    }

    suspend fun restoreExperienceVersion(version: ExperienceVersionEntity) = withContext(Dispatchers.IO) {
        val profileMd = markdownManager.getCareerProfileContent()
        val current = profileDao.getProfile() ?: UserCareerProfileEntity(id = 1, markdownContent = profileMd)
        if (version.type == "WORK") {
            markdownManager.writeWorkExperiences(version.content)
            profileDao.insertOrUpdateProfile(current.copy(workExperiences = version.content, lastUpdated = System.currentTimeMillis()))
        } else if (version.type == "PROJECT") {
            markdownManager.writeProjectExperiences(version.content)
            profileDao.insertOrUpdateProfile(current.copy(projectExperiences = version.content, lastUpdated = System.currentTimeMillis()))
        } else if (version.type == "PROFILE") {
            markdownManager.writeCareerProfile(version.content)
            profileDao.insertOrUpdateProfile(current.copy(markdownContent = version.content, lastUpdated = System.currentTimeMillis()))
        }
    }

    suspend fun deleteExperienceVersion(id: Int) = withContext(Dispatchers.IO) {
        versionDao.deleteVersionById(id)
    }

    suspend fun importWorkExperiences(content: String, sourceFileName: String) = withContext(Dispatchers.IO) {
        val profileMd = markdownManager.getCareerProfileContent()
        val current = profileDao.getProfile() ?: UserCareerProfileEntity(id = 1, markdownContent = profileMd)
        markdownManager.writeWorkExperiences(content)
        profileDao.insertOrUpdateProfile(current.copy(workExperiences = content, lastUpdated = System.currentTimeMillis()))
        versionDao.insertVersion(
            ExperienceVersionEntity(
                type = "WORK",
                content = content,
                timestamp = System.currentTimeMillis(),
                summaryNote = "文件导入: $sourceFileName"
            )
        )
    }

    suspend fun importProjectExperiences(content: String, sourceFileName: String) = withContext(Dispatchers.IO) {
        val profileMd = markdownManager.getCareerProfileContent()
        val current = profileDao.getProfile() ?: UserCareerProfileEntity(id = 1, markdownContent = profileMd)
        markdownManager.writeProjectExperiences(content)
        profileDao.insertOrUpdateProfile(current.copy(projectExperiences = content, lastUpdated = System.currentTimeMillis()))
        versionDao.insertVersion(
            ExperienceVersionEntity(
                type = "PROJECT",
                content = content,
                timestamp = System.currentTimeMillis(),
                summaryNote = "文件导入: $sourceFileName"
            )
        )
    }

    // --- Todo Actions ---

    suspend fun setTodoCompleted(id: Int, isCompleted: Boolean) = withContext(Dispatchers.IO) {
        todoDao.setCompleted(id, isCompleted)
    }

    suspend fun addTodo(todo: TodoItemEntity) = withContext(Dispatchers.IO) {
        todoDao.insertTodo(todo)
    }

    suspend fun deleteTodo(id: Int) = withContext(Dispatchers.IO) {
        todoDao.deleteById(id)
    }

    suspend fun updateCareerProfileManually(newContent: String) = withContext(Dispatchers.IO) {
        val profileMd = newContent
        markdownManager.writeCareerProfile(profileMd)
        val current = profileDao.getProfile() ?: UserCareerProfileEntity(id = 1, markdownContent = profileMd)
        if (current.markdownContent.isNotBlank()) {
            versionDao.insertVersion(
                ExperienceVersionEntity(
                    type = "PROFILE",
                    content = current.markdownContent,
                    timestamp = System.currentTimeMillis(),
                    summaryNote = "手动保存前备份"
                )
            )
        }
        profileDao.insertOrUpdateProfile(
            current.copy(
                markdownContent = newContent,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateWorkExperiencesManually(newContent: String) = withContext(Dispatchers.IO) {
        markdownManager.writeWorkExperiences(newContent)
        val current = profileDao.getProfile() ?: UserCareerProfileEntity(id = 1, markdownContent = markdownManager.getCareerProfileContent())
        if (current.workExperiences.isNotBlank()) {
            versionDao.insertVersion(
                ExperienceVersionEntity(
                    type = "WORK",
                    content = current.workExperiences,
                    timestamp = System.currentTimeMillis(),
                    summaryNote = "手动保存前备份"
                )
            )
        }
        profileDao.insertOrUpdateProfile(
            current.copy(
                workExperiences = newContent,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateProjectExperiencesManually(newContent: String) = withContext(Dispatchers.IO) {
        markdownManager.writeProjectExperiences(newContent)
        val current = profileDao.getProfile() ?: UserCareerProfileEntity(id = 1, markdownContent = markdownManager.getCareerProfileContent())
        if (current.projectExperiences.isNotBlank()) {
            versionDao.insertVersion(
                ExperienceVersionEntity(
                    type = "PROJECT",
                    content = current.projectExperiences,
                    timestamp = System.currentTimeMillis(),
                    summaryNote = "手动保存前备份"
                )
            )
        }
        profileDao.insertOrUpdateProfile(
            current.copy(
                projectExperiences = newContent,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }

    // --- Semantic Search ---

    suspend fun performSemanticSearch(query: String, onAiFailure: ((String) -> Unit)? = null): Result<List<com.example.data.ai.GeminiRepository.SemanticSearchResult>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.success(emptyList())

        val logs = logDao.getAllLogsOnce()
        val settings = getSettings()

        // 1. Try AI Semantic Search first
        val aiResult = aiRepository.semanticSearchLogs(query, logs, settings)
        if (aiResult.isSuccess && !aiResult.getOrNull().isNullOrEmpty()) {
            return@withContext aiResult
        }
        
        if (aiResult.isFailure) {
            onAiFailure?.invoke("⚠️ AI 搜索未能处理，已降级为本地关键字搜索")
        }

        // 2. Local fallback search
        val lowerQuery = query.lowercase().trim()
        val localMatches = logs.filter { log ->
            log.summaryMarkdown.lowercase().contains(lowerQuery) ||
            log.rawNotes.lowercase().contains(lowerQuery)
        }.map { log ->
            val text = if (log.summaryMarkdown.isNotBlank()) log.summaryMarkdown else log.rawNotes
            val idx = text.lowercase().indexOf(lowerQuery)
            val start = (idx - 25).coerceAtLeast(0)
            val end = (idx + lowerQuery.length + 50).coerceAtMost(text.length)
            val snippetText = if (idx != -1) text.substring(start, end) else text.take(80)

            com.example.data.ai.GeminiRepository.SemanticSearchResult(
                date = log.date,
                relevanceScore = 80,
                matchReason = "文本包含关键词 \"$query\"",
                snippet = snippetText.replace("\n", " ").trim()
            )
        }

        if (localMatches.isNotEmpty()) {
            return@withContext Result.success(localMatches)
        }

        return@withContext Result.success(aiResult.getOrDefault(emptyList()))
    }

    suspend fun exportDataToZip(outputStream: java.io.OutputStream) = withContext(Dispatchers.IO) {
        markdownManager.exportAllDataToZip(outputStream)
    }

    suspend fun getMarkdownDirectoryInfo(): Map<String, String> = withContext(Dispatchers.IO) {
        markdownManager.getMarkdownDirectoryInfo()
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        // Clear DB
        logDao.deleteAllLogs()
        todoDao.deleteAllTodos()
        profileDao.deleteAllProfiles()
        versionDao.deleteAllVersions()

        // Clear files
        markdownManager.clearAllData()
    }
}
