package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiRepository
import com.example.data.local.DailyWorkLogEntity
import com.example.data.local.TodoItemEntity
import com.example.data.local.UserCareerProfileEntity
import com.example.data.local.UserSettingsEntity
import com.example.data.repository.WorkLogRepository
import com.example.data.utils.WorkLogFilterUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WorkLogViewModel(application: Application) : AndroidViewModel(application) {

    val repository = WorkLogRepository(application)

    // UI Input States
    private val _rawInputText = MutableStateFlow("")
    val rawInputText: StateFlow<String> = _rawInputText.asStateFlow()

    private val _selectedDate = MutableStateFlow(repository.getTodayString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Editing States
    private val _editingProfileText = MutableStateFlow<String?>(null)
    val editingProfileText: StateFlow<String?> = _editingProfileText.asStateFlow()
    
    private val _editingWorkExpText = MutableStateFlow<String?>(null)
    val editingWorkExpText: StateFlow<String?> = _editingWorkExpText.asStateFlow()
    
    private val _editingProjectExpText = MutableStateFlow<String?>(null)
    val editingProjectExpText: StateFlow<String?> = _editingProjectExpText.asStateFlow()

    private val _resumeMarkdown = MutableStateFlow("")
    val resumeMarkdown: StateFlow<String> = _resumeMarkdown.asStateFlow()

    private val _selectedResumeStyle = MutableStateFlow("Modern")
    val selectedResumeStyle: StateFlow<String> = _selectedResumeStyle.asStateFlow()

    private val _isGeneratingResume = MutableStateFlow(false)
    val isGeneratingResume: StateFlow<Boolean> = _isGeneratingResume.asStateFlow()

    private val _isGeneratingWorkExp = MutableStateFlow(false)
    val isGeneratingWorkExp: StateFlow<Boolean> = _isGeneratingWorkExp.asStateFlow()

    private val _isGeneratingProjectExp = MutableStateFlow(false)
    val isGeneratingProjectExp: StateFlow<Boolean> = _isGeneratingProjectExp.asStateFlow()

    // Semantic Search States
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchingLogs = MutableStateFlow(false)
    val isSearchingLogs: StateFlow<Boolean> = _isSearchingLogs.asStateFlow()

    private val _searchResults = MutableStateFlow<List<AiRepository.SemanticSearchResult>?>(null)
    val searchResults: StateFlow<List<AiRepository.SemanticSearchResult>?> = _searchResults.asStateFlow()

    private var activeAiJob: kotlinx.coroutines.Job? = null

    private val _lastDeletedTodo = MutableStateFlow<TodoItemEntity?>(null)
    val lastDeletedTodo: StateFlow<TodoItemEntity?> = _lastDeletedTodo.asStateFlow()

    fun cancelActiveAiJob() {
        activeAiJob?.cancel()
        activeAiJob = null
        _isProcessingAI.value = false
        _isGeneratingResume.value = false
        _isGeneratingWorkExp.value = false
        _isGeneratingProjectExp.value = false
        _isImportingFile.value = false
        _isSearchingLogs.value = false
        _isTestingConnection.value = false
        _aiStatusMessage.value = ""
        showSnack("已取消当前 AI 任务")
    }

    // AI Processing States
    private val _isProcessingAI = MutableStateFlow(false)
    val isProcessingAI: StateFlow<Boolean> = _isProcessingAI.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<String?>(null)
    val connectionTestResult: StateFlow<String?> = _connectionTestResult.asStateFlow()

    private val _isImportingFile = MutableStateFlow(false)
    val isImportingFile: StateFlow<Boolean> = _isImportingFile.asStateFlow()

    private val _aiStatusMessage = MutableStateFlow("")
    val aiStatusMessage: StateFlow<String> = _aiStatusMessage.asStateFlow()

    private val _snackMessage = MutableStateFlow<String?>(null)
    val snackMessage: StateFlow<String?> = _snackMessage.asStateFlow()

    private val _mdInfo = MutableStateFlow<Map<String, String>>(emptyMap())
    val mdInfo: StateFlow<Map<String, String>> = _mdInfo.asStateFlow()

    fun loadMdInfo() {
        viewModelScope.launch {
            _mdInfo.value = repository.getMarkdownDirectoryInfo()
        }
    }

    // Database Reactive Flows
    val allLogs: StateFlow<List<DailyWorkLogEntity>> = repository.allLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTodos: StateFlow<List<TodoItemEntity>> = repository.allTodosFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val careerProfile: StateFlow<UserCareerProfileEntity?> = repository.profileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val workVersions: StateFlow<List<com.example.data.local.ExperienceVersionEntity>> = repository.workVersionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projectVersions: StateFlow<List<com.example.data.local.ExperienceVersionEntity>> = repository.projectVersionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<UserSettingsEntity?> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Selected Date Log Flow
    val selectedDateLog: StateFlow<DailyWorkLogEntity?> = combine(allLogs, selectedDate) { logs, date ->
        logs.find { it.date == date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Unsummarized Raw Input Count
    val unsummarizedCount: StateFlow<Int> = allLogs.map { logs ->
        WorkLogFilterUtils.filterUnsummarizedLogs(logs).size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun onRawInputChanged(text: String) {
        _rawInputText.value = text
    }

    fun submitRawInput() {
        val text = _rawInputText.value.trim()
        if (text.isEmpty()) return

        val targetDate = _selectedDate.value.ifBlank { repository.getTodayString() }
        viewModelScope.launch {
            repository.addRawNote(text, targetDate)
            _rawInputText.value = ""
            val isToday = targetDate == repository.getTodayString()
            showSnack(if (isToday) "已归档今日日志！稍后AI将自动总结。" else "已归档【$targetDate】日志！稍后AI将自动总结。")
        }
    }

    fun selectDate(dateStr: String) {
        val today = repository.getTodayString()
        _selectedDate.value = WorkLogFilterUtils.clampDateNotAfterToday(dateStr, today)
    }

    fun triggerAISummarizeToday() {
        val today = repository.getTodayString()
        selectDate(today)
        triggerAISummarizeSelectedDate()
    }

    fun triggerAISummarizeSelectedDate() {
        val date = _selectedDate.value
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _isProcessingAI.value = true
            _aiStatusMessage.value = "正在对【$date】进行 AI 结构化整理..."
            try {
                val result = repository.triggerAISummarize(date) { msg ->
                    _aiStatusMessage.value = msg
                }
                if (result.isSuccess) {
                    showSnack(result.getOrNull() ?: "AI 总结完成")
                } else {
                    showSnack("AI 处理失败: ${result.exceptionOrNull()?.message}")
                }
            } finally {
                _isProcessingAI.value = false
            }
        }
    }

    fun triggerAISummarizeAllUnsummarized() {
        activeAiJob?.cancel()
        val initialDate = _selectedDate.value
        activeAiJob = viewModelScope.launch {
            val unsummarizedLogs = WorkLogFilterUtils.filterUnsummarizedLogs(allLogs.value)
            _isProcessingAI.value = true
            try {
                if (unsummarizedLogs.isEmpty()) {
                    // Inline execution to avoid re-assigning activeAiJob (N-26)
                    val date = initialDate
                    _aiStatusMessage.value = "正在对【$date】进行 AI 结构化整理..."
                    val result = repository.triggerAISummarize(date) { msg ->
                        _aiStatusMessage.value = msg
                    }
                    if (result.isSuccess) {
                        showSnack(result.getOrNull() ?: "AI 总结完成")
                    } else {
                        showSnack("AI 处理失败: ${result.exceptionOrNull()?.message}")
                    }
                    return@launch
                }

                val total = unsummarizedLogs.size
                var successCount = 0
                var failureCount = 0

                // Prioritize initialDate first if it is among unsummarized
                val sortedList = unsummarizedLogs.sortedByDescending { it.date == initialDate }

                for ((index, log) in sortedList.withIndex()) {
                    val date = log.date
                    _selectedDate.value = date
                    _aiStatusMessage.value = "正在对【$date】进行 AI 结构化整理 (${index + 1}/$total)..."
                    
                    val result = repository.triggerAISummarize(date) { msg ->
                        _aiStatusMessage.value = "【$date】$msg"
                    }
                    if (result.isSuccess) {
                        successCount++
                    } else {
                        failureCount++
                    }
                }

                if (failureCount == 0) {
                    if (total == 1) {
                        showSnack("【${sortedList.first().date}】AI 整理完成！")
                    } else {
                        showSnack("已成功整理全部 $successCount 个日期的工作日志！")
                    }
                } else {
                    showSnack("整理完成：$successCount 个成功，$failureCount 个失败")
                }
            } finally {
                _isProcessingAI.value = false
                _selectedDate.value = initialDate // Restore selected date (N-27)
            }
        }
    }

    fun triggerAISummarizeForUnsummarizedOrSelected() {
        triggerAISummarizeAllUnsummarized()
    }

    fun triggerManualScheduledTask() {
        viewModelScope.launch {
            _isProcessingAI.value = true
            _aiStatusMessage.value = "启动后台全量工作总结任务..."

            val result = repository.triggerAISummarize(repository.getTodayString()) { msg ->
                _aiStatusMessage.value = msg
            }

            _isProcessingAI.value = false
            if (result.isSuccess) {
                showSnack("后台定时整理任务模拟运行成功！")
            } else {
                showSnack("后台整理运行失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    init {
        viewModelScope.launch {
            repository.getSettings()
            repository.restoreLogsFromMarkdownIfNeeded()
            val savedResume = repository.getSavedResume()
            if (savedResume.isNotBlank()) {
                _resumeMarkdown.value = savedResume
            }
            val savedStyle = repository.getSavedResumeStyle()
            if (savedStyle.isNotBlank()) {
                _selectedResumeStyle.value = savedStyle
            }
        }
    }

    fun setSelectedResumeStyle(style: String) {
        _selectedResumeStyle.value = style
        repository.saveResumeStyle(style)
    }

    fun generateResume(style: String = _selectedResumeStyle.value) {
        setSelectedResumeStyle(style)
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _isGeneratingResume.value = true
            _aiStatusMessage.value = "正在使用 AI 生成《$style》风格敏感词保护简历..."
            try {
                val res = repository.generateResume(style)
                if (res.isSuccess) {
                    val resumeStr = res.getOrDefault("")
                    _resumeMarkdown.value = resumeStr
                    repository.saveResume(resumeStr)
                    showSnack("简历生成成功！已包含隐私占位符保护。")
                } else {
                    showSnack("简历生成失败: ${res.exceptionOrNull()?.message}")
                }
            } finally {
                _isGeneratingResume.value = false
            }
        }
    }

    fun generateWorkExperiences() {
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _isGeneratingWorkExp.value = true
            _aiStatusMessage.value = "AI 正在全量整理并提炼【工作经历】..."
            try {
                val res = repository.generateWorkExperiences()
                if (res.isSuccess) {
                    showSnack("工作经历提炼成功！")
                } else {
                    showSnack("工作经历生成失败: ${res.exceptionOrNull()?.message}")
                }
            } finally {
                _isGeneratingWorkExp.value = false
            }
        }
    }

    fun generateProjectExperiences() {
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _isGeneratingProjectExp.value = true
            _aiStatusMessage.value = "AI 正在全量提取核心【项目经历】..."
            try {
                val res = repository.generateProjectExperiences()
                if (res.isSuccess) {
                    showSnack("项目经历提炼成功！")
                } else {
                    showSnack("项目经历生成失败: ${res.exceptionOrNull()?.message}")
                }
            } finally {
                _isGeneratingProjectExp.value = false
            }
        }
    }

    fun toggleTodoCompleted(id: Int, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.setTodoCompleted(id, isCompleted)
        }
    }

    fun addTodo(title: String, desc: String, priority: String, category: String) {
        viewModelScope.launch {
            val newTodo = TodoItemEntity(
                title = title,
                description = desc,
                dateCreated = repository.getTodayString(),
                priority = priority,
                category = category
            )
            repository.addTodo(newTodo)
            showSnack("已添加待办事项")
        }
    }

    fun deleteTodo(id: Int) {
        viewModelScope.launch {
            val todo = allTodos.value.find { it.id == id }
            if (todo != null) {
                repository.deleteTodo(id)
                _lastDeletedTodo.value = todo
                showSnack("已删除待办: ${todo.title}")
            }
        }
    }

    fun undoDeleteTodo() {
        val todo = _lastDeletedTodo.value ?: return
        viewModelScope.launch {
            repository.addTodo(todo)
            _lastDeletedTodo.value = null
            showSnack("已恢复待办: ${todo.title}")
        }
    }

    fun updateSettings(newSettings: UserSettingsEntity) {
        viewModelScope.launch {
            repository.saveSettings(newSettings)
            showSnack("设置已保存")
        }
    }

    fun testAiConnection(baseUrl: String, apiKey: String, model: String) {
        val validation = AiRepository.validateEndpointConfig(baseUrl, apiKey, model)
        if (validation.isFailure) {
            val errMsg = validation.exceptionOrNull()?.message ?: "配置无效"
            _connectionTestResult.value = "连接失败: $errMsg"
            showSnack(errMsg)
            return
        }

        if (_isTestingConnection.value) return
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestResult.value = null
            val startTime = System.currentTimeMillis()
            try {
                val result = repository.testAiConnection(baseUrl.trim(), apiKey.trim(), model.trim())
                val duration = System.currentTimeMillis() - startTime

                if (result.isSuccess) {
                    val responseText = result.getOrDefault("").trim()
                    val snippet = if (responseText.length > 60) responseText.take(60) + "..." else responseText
                    val msg = "连接成功（耗时 ${duration}ms）\n模型响应：$snippet"
                    _connectionTestResult.value = msg
                    showSnack("API 连接测试成功 (${duration}ms)")
                } else {
                    val err = result.exceptionOrNull()?.message ?: "未知异常"
                    val msg = "连接失败: $err"
                    _connectionTestResult.value = msg
                    showSnack("连接测试失败: $err")
                }
            } finally {
                _isTestingConnection.value = false
            }
        }
    }

    fun clearConnectionTestResult() {
        _connectionTestResult.value = null
    }

    fun restoreExperienceVersion(version: com.example.data.local.ExperienceVersionEntity) {
        viewModelScope.launch {
            repository.restoreExperienceVersion(version)
            val typeName = if (version.type == "WORK") "工作经历" else "项目经历"
            showSnack("已恢复至历史版本 ($typeName)")
        }
    }

    fun deleteExperienceVersion(id: Int) {
        viewModelScope.launch {
            repository.deleteExperienceVersion(id)
            showSnack("已删除该历史版本")
        }
    }

    fun importWorkExperiences(content: String, sourceFileName: String, refineWithAi: Boolean = false) {
        viewModelScope.launch {
            _isImportingFile.value = true
            try {
                val finalContent = if (refineWithAi) {
                    val res = repository.aiRepository.refineImportedContent(content, "工作经历", repository.getSettings())
                    if (res.isFailure) {
                        showSnack("AI 提炼失败：${res.exceptionOrNull()?.message}")
                        return@launch
                    }
                    res.getOrNull() ?: content
                } else {
                    content
                }
                repository.importWorkExperiences(finalContent, sourceFileName)
                showSnack(if (refineWithAi) "工作经历已由 AI 提炼并导入！" else "工作经历文件已直接导入！")
            } finally {
                _isImportingFile.value = false
            }
        }
    }

    fun importProjectExperiences(content: String, sourceFileName: String, refineWithAi: Boolean = false) {
        viewModelScope.launch {
            _isImportingFile.value = true
            try {
                val finalContent = if (refineWithAi) {
                    val res = repository.aiRepository.refineImportedContent(content, "项目经历", repository.getSettings())
                    if (res.isFailure) {
                        showSnack("AI 提炼失败：${res.exceptionOrNull()?.message}")
                        return@launch
                    }
                    res.getOrNull() ?: content
                } else {
                    content
                }
                repository.importProjectExperiences(finalContent, sourceFileName)
                showSnack(if (refineWithAi) "项目经历已由 AI 提炼并导入！" else "项目经历文件已直接导入！")
            } finally {
                _isImportingFile.value = false
            }
        }
    }

    fun importCareerProfile(content: String, sourceFileName: String, refineWithAi: Boolean = false) {
        viewModelScope.launch {
            _isImportingFile.value = true
            try {
                val finalContent = if (refineWithAi) {
                    val res = repository.aiRepository.refineImportedContent(content, "职业档案全貌", repository.getSettings())
                    if (res.isFailure) {
                        showSnack("AI 提炼失败：${res.exceptionOrNull()?.message}")
                        return@launch
                    }
                    res.getOrNull() ?: content
                } else {
                    content
                }
                repository.updateCareerProfileManually(finalContent)
                showSnack(if (refineWithAi) "职业档案全貌已由 AI 提炼并导入！" else "职业档案全貌文件已直接导入！")
            } finally {
                _isImportingFile.value = false
            }
        }
    }

    fun startEditingProfile(initialText: String) {
        _editingProfileText.value = initialText
    }

    fun updateEditingProfileText(text: String) {
        _editingProfileText.value = text
    }

    fun cancelEditingProfile() {
        _editingProfileText.value = null
    }

    fun saveCareerProfileManually(content: String) {
        viewModelScope.launch {
            repository.updateCareerProfileManually(content)
            _editingProfileText.value = null
            showSnack("职业履历文档已手动更新")
        }
    }

    fun startEditingWorkExp(initialText: String) {
        _editingWorkExpText.value = initialText
    }

    fun updateEditingWorkExpText(text: String) {
        _editingWorkExpText.value = text
    }

    fun cancelEditingWorkExp() {
        _editingWorkExpText.value = null
    }

    fun saveWorkExpManually(content: String) {
        viewModelScope.launch {
            repository.updateWorkExperiencesManually(content)
            _editingWorkExpText.value = null
            showSnack("工作经历文档已手动更新")
        }
    }

    fun startEditingProjectExp(initialText: String) {
        _editingProjectExpText.value = initialText
    }

    fun updateEditingProjectExpText(text: String) {
        _editingProjectExpText.value = text
    }

    fun cancelEditingProjectExp() {
        _editingProjectExpText.value = null
    }

    fun saveProjectExpManually(content: String) {
        viewModelScope.launch {
            repository.updateProjectExperiencesManually(content)
            _editingProjectExpText.value = null
            showSnack("项目经历文档已手动更新")
        }
    }

    // --- Search Actions ---

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun performSemanticSearch(queryToSearch: String = _searchQuery.value) {
        val q = queryToSearch.trim()
        if (q.isBlank()) {
            _searchResults.value = null
            return
        }
        _searchQuery.value = q

        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _isSearchingLogs.value = true
            try {
                val res = repository.performSemanticSearch(q) { fallbackMsg ->
                    showSnack(fallbackMsg)
                }
                if (res.isSuccess) {
                    _searchResults.value = res.getOrDefault(emptyList())
                } else {
                    showSnack("搜索失败: ${res.exceptionOrNull()?.localizedMessage}")
                }
            } finally {
                _isSearchingLogs.value = false
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = null
        _isSearchingLogs.value = false
    }

    fun showSnack(msg: String) {
        _snackMessage.value = msg
    }

    fun clearSnack() {
        _snackMessage.value = null
    }

    fun exportAllDataToZip(outputStream: java.io.OutputStream) {
        viewModelScope.launch {
            try {
                repository.exportDataToZip(outputStream)
                showSnack("数据导出成功")
            } catch (e: Exception) {
                showSnack("导出失败: ${e.message}")
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    outputStream.close()
                }
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            try {
                repository.clearAllData()
                _rawInputText.value = ""
                _resumeMarkdown.value = ""
                _editingProfileText.value = null
                _editingWorkExpText.value = null
                _editingProjectExpText.value = null
                loadMdInfo()
                showSnack("所有记录的数据已清空")
            } catch (e: Exception) {
                showSnack("清空失败: ${e.message}")
            }
        }
    }
}
