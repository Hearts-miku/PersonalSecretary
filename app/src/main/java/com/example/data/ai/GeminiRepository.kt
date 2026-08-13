package com.example.data.ai

import com.example.BuildConfig
import com.example.data.local.UserSettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Interface and implementation for Gemini AI calls.
 * Implements summarization, todo extraction, career profile update, and resume generation.
 */
class GeminiRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Resolves the API key to use (Settings override -> BuildConfig -> empty).
     */
    private fun getEffectiveApiKey(settings: UserSettingsEntity?): String {
        val userKey = settings?.apiKey?.trim()
        if (!userKey.isNullOrEmpty()) return userKey
        
        val provider = settings?.apiProvider ?: "GEMINI"
        if (provider == "GEMINI") {
            val buildKey = BuildConfig.GEMINI_API_KEY.trim()
            if (buildKey.isNotEmpty() && buildKey != "MY_GEMINI_API_KEY") return buildKey
        }
        
        return ""
    }

    private fun getEffectiveBaseUrl(settings: UserSettingsEntity?): String {
        val url = settings?.baseUrl?.trim()
        val provider = settings?.apiProvider ?: "GEMINI"
        if (!url.isNullOrEmpty()) {
            return if (url.endsWith("/")) url else "$url/"
        }
        return when (provider) {
            "OPENAI" -> "https://api.openai.com/v1/"
            "ANTHROPIC" -> "https://api.anthropic.com/"
            "CUSTOM" -> "https://api.openai.com/v1/"
            else -> "https://generativelanguage.googleapis.com/"
        }
    }

    private fun getEffectiveModel(settings: UserSettingsEntity?): String {
        val model = settings?.selectedModel?.trim()
        val provider = settings?.apiProvider ?: "GEMINI"
        if (!model.isNullOrEmpty()) return model
        return when (provider) {
            "OPENAI" -> "gpt-4o"
            "ANTHROPIC" -> "claude-3-5-sonnet-20241022"
            "CUSTOM" -> "gpt-4o"
            else -> "gemini-2.5-flash"
        }
    }

    /**
     * General method to generate content using selected provider (Gemini, OpenAI, Anthropic, Custom).
     */
    suspend fun generateContent(
        prompt: String,
        systemInstruction: String? = null,
        settings: UserSettingsEntity? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getEffectiveApiKey(settings)
            val baseUrl = getEffectiveBaseUrl(settings)
            val model = getEffectiveModel(settings)
            val provider = settings?.apiProvider ?: "GEMINI"

            if (apiKey.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("未配置 API Key。请在 [设置] 页面中选择对应服务商并输入您的 API Key。")
                )
            }

            when (provider) {
                "OPENAI", "CUSTOM" -> callOpenAiApi(baseUrl, apiKey, model, prompt, systemInstruction)
                "ANTHROPIC" -> callAnthropicApi(baseUrl, apiKey, model, prompt, systemInstruction)
                else -> callGeminiApi(baseUrl, apiKey, model, prompt, systemInstruction)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun callGeminiApi(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        systemInstruction: String?
    ): Result<String> {
        val fullUrl = "${baseUrl}v1beta/models/$model:generateContent"
        val requestJson = JSONObject()

        if (!systemInstruction.isNullOrBlank()) {
            val sysContent = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", systemInstruction))
                })
            }
            requestJson.put("systemInstruction", sysContent)
        }

        val contentsArray = JSONArray().apply {
            val contentObj = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", prompt))
                })
            }
            put(contentObj)
        }
        requestJson.put("contents", contentsArray)

        val genConfig = JSONObject().apply {
            put("temperature", 0.2)
        }
        requestJson.put("generationConfig", genConfig)

        val body = requestJson.toString().toRequestBody(jsonMediaType)
        val httpRequest = Request.Builder()
            .url(fullUrl)
            .addHeader("x-goog-api-key", apiKey)
            .post(body)
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val responseStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(responseStr, response.code)
                return Result.failure(Exception(errorMsg))
            }

            val resJson = JSONObject(responseStr)
            val candidates = resJson.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val finishReason = firstCandidate.optString("finishReason", "")
                if (finishReason == "SAFETY" || finishReason == "RECITATION" || finishReason == "MAX_TOKENS" || finishReason == "LENGTH") {
                    return Result.failure(Exception("Gemini 输出因限制产生截断 ($finishReason)"))
                }
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = parts.getJSONObject(0).optString("text", "")
                    return Result.success(text)
                }
            }
            return Result.failure(Exception("Gemini 未返回有效回答"))
        }
    }

    private fun callOpenAiApi(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        systemInstruction: String?
    ): Result<String> {
        val endpoint = if (baseUrl.endsWith("chat/completions") || baseUrl.endsWith("chat/completions/")) {
            baseUrl
        } else {
            "${baseUrl}chat/completions"
        }

        val messages = JSONArray()
        if (!systemInstruction.isNullOrBlank()) {
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemInstruction)
            })
        }
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        })

        val requestJson = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            if (!model.startsWith("o1") && !model.startsWith("o3")) {
                put("temperature", 0.2)
            }
        }

        val body = requestJson.toString().toRequestBody(jsonMediaType)
        val httpRequest = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val responseStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(responseStr, response.code)
                return Result.failure(Exception(errorMsg))
            }

            val resJson = JSONObject(responseStr)
            val choices = resJson.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val finishReason = firstChoice.optString("finish_reason", "")
                if (finishReason == "length") {
                    return Result.failure(Exception("OpenAI 输出因 length 产生截断"))
                }
                val message = firstChoice.optJSONObject("message")
                val content = message?.optString("content", "") ?: ""
                if (content.isNotBlank()) {
                    return Result.success(content)
                }
            }
            return Result.failure(Exception("OpenAI 兼容 API 未返回有效内容"))
        }
    }

    private fun callAnthropicApi(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        systemInstruction: String?
    ): Result<String> {
        val endpoint = if (baseUrl.contains("v1/messages")) baseUrl else "${baseUrl}v1/messages"

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val requestJson = JSONObject().apply {
            put("model", model)
            put("max_tokens", 8192)
            if (!systemInstruction.isNullOrBlank()) {
                put("system", systemInstruction)
            }
            put("messages", messages)
            put("temperature", 0.2)
        }

        val body = requestJson.toString().toRequestBody(jsonMediaType)
        val httpRequest = Request.Builder()
            .url(endpoint)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body)
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val responseStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(responseStr, response.code)
                return Result.failure(Exception(errorMsg))
            }

            val resJson = JSONObject(responseStr)
            val stopReason = resJson.optString("stop_reason", "")
            if (stopReason == "max_tokens") {
                return Result.failure(Exception("Anthropic 输出因 max_tokens 产生截断"))
            }
            val contentArr = resJson.optJSONArray("content")
            if (contentArr != null && contentArr.length() > 0) {
                val firstObj = contentArr.getJSONObject(0)
                val text = firstObj.optString("text", "")
                if (text.isNotBlank()) {
                    return Result.success(text)
                }
            }
            return Result.failure(Exception("Anthropic API 未返回有效内容"))
        }
    }

    /**
     * AI Feature 1: Summarize Raw Inputs into structured Daily Work Log Markdown.
     */
    suspend fun summarizeDailyWork(
        dateStr: String,
        rawNotes: String,
        settings: UserSettingsEntity? = null
    ): Result<String> {
        val cleanRaw = AISafetyManager.sanitizeUserInput(rawNotes)
        val prompt = """
            请将以下用户在【$dateStr】记录的原始工作内容，整理为一份高可读性、专业、结构化的 Markdown 工作总结文档。

            <user_raw_content>
            $cleanRaw
            </user_raw_content>

            排版要求：
            1. 包含标题：# $dateStr 工作总结
            2. 包含模块：
               - ## 核心完成事项 (列出重点完成的任务与关键产出)
               - ## 技术细节与改进 (用到的技术、遇到的问题与解决方法)
               - ## 今日主要工作成果与数据 (如适用)
            3. 格式清晰，使用 Markdown 列表和粗体高亮。
        """.trimIndent()

        val systemInst = """
            ${AISafetyManager.SYSTEM_GUARDRAIL_PROMPT}
            你的职责是将用户的日常输入提炼为专业的日间工作总结。
        """.trimIndent()

        return generateContent(prompt, systemInst, settings)
    }

    /**
     * AI Feature 2: Extract Todo items from raw notes.
     * Returns JSON string or list of todo items.
     */
    suspend fun extractTodos(
        dateStr: String,
        rawNotes: String,
        settings: UserSettingsEntity? = null
    ): Result<List<ExtractedTodo>> {
        val cleanRaw = AISafetyManager.sanitizeUserInput(rawNotes)
        val prompt = """
            请分析以下用户原始工作记录，从中提取出明确需要在后续或今日跟进的【待办事项】(Todo items)。

            <user_raw_content>
            $cleanRaw
            </user_raw_content>

            请严格按照 JSON 数组格式返回（不要包含任何额外 Markdown 代码块或文字！）：
            [
              {
                "title": "待办事项简短标题",
                "description": "详细描述（可选）",
                "priority": "HIGH" 或 "MEDIUM" 或 "LOW",
                "category": "工作分类（如：开发/会议/文档/测试）"
              }
            ]
            如果记录中没有提及任何未完成或需要后续跟进的任务，请返回空数组 `[]`。
        """.trimIndent()

        val systemInst = """
            ${AISafetyManager.SYSTEM_GUARDRAIL_PROMPT}
            你是一个精准的任务提取助手，只能提取用户显式或直接暗示的待办项，严禁凭空添加任务。
        """.trimIndent()

        val result = generateContent(prompt, systemInst, settings)
        return result.map { jsonStr ->
            parseTodosFromJson(jsonStr)
        }
    }

    /**
     * AI Feature 3: Determine if career profile & skills need update, and update if needed.
     * Requirement: "工作经历与技能不必每天更新，让AI进行判断是否需要更新"
     */
    suspend fun evaluateAndUpdateCareerProfile(
        currentProfileMarkdown: String,
        newDailySummaries: String,
        settings: UserSettingsEntity? = null
    ): Result<CareerProfileUpdateResult> {
        val cleanProfile = AISafetyManager.sanitizeUserInput(currentProfileMarkdown)
        val cleanNewLogs = AISafetyManager.sanitizeUserInput(newDailySummaries)

        val prompt = """
            请判断最新整理的工作总结内容，是否包含了新增的【核心工作经历、重大项目成果、新掌握的技术栈或突破】。

            【当前用户职业履历文档】:
            <user_raw_content>
            $cleanProfile
            </user_raw_content>

            【最新添加的工作总结】:
            <user_raw_content>
            $cleanNewLogs
            </user_raw_content>

            请按照以下 JSON 格式输出：
            {
              "shouldUpdate": true 或 false,
              "reason": "判断依据说明",
              "updatedProfileMarkdown": "如果 shouldUpdate 为 true，请输出更新合并后的完整用户职业履历 Markdown 文档；若为 false，则填入空字符串"
            }
        """.trimIndent()

        val systemInst = """
            ${AISafetyManager.SYSTEM_GUARDRAIL_PROMPT}
            你是用户的职业经历总结AI专家。只有当新日志包含实质性的新工作职责、项目成果或新技能时才更新履历，避免无意义的频繁微调。
        """.trimIndent()

        val res = generateContent(prompt, systemInst, settings)
        return res.map { jsonText ->
            parseCareerUpdateFromJson(jsonText, currentProfileMarkdown)
        }
    }

    /**
     * AI Feature 4: Generate User Resume based on User Career Profile.
     * Requirement: Privacy protection with placeholders like [姓名], [电话], [邮箱].
     */
    suspend fun generateUserResume(
        careerProfileMarkdown: String,
        templateStyle: String = "Modern",
        settings: UserSettingsEntity? = null
    ): Result<String> {
        val cleanProfile = AISafetyManager.sanitizeUserInput(careerProfileMarkdown)

        val prompt = """
            请基于以下用户的累计工作经历与职业技能文档，生成一份排版优雅、结构严谨的专业个人简历。

            【用户职业履历文档】:
            <user_raw_content>
            $cleanProfile
            </user_raw_content>

            模板风格需求：$templateStyle 风格（如 Modern: 现代科技感，注重成果量化与技术栈；Classic: 传统稳重；Minimal: 极简流线型）。

            简历包含的标准模块：
            1. 个人基本信息（姓名、联系方式等严格使用占位符，详见系统安全指令）
            2. 个人优势 / 核心技能
            3. 工作经历与主要项目（突出职责、技术点与核心产出）
            4. 教育背景与职业技能总结
        """.trimIndent()

        val systemInst = """
            ${AISafetyManager.SYSTEM_GUARDRAIL_PROMPT}
            你是资深HR与简历专家，负责打造高质量Markdown简历。严禁虚构任何未经记录的公司名称或工作成果，所有个人隐私一律使用占位符！
        """.trimIndent()

        return generateContent(prompt, systemInst, settings)
    }

    /**
     * AI Feature 5: Generate AI Work Experiences summary.
     */
    suspend fun generateWorkExperiences(
        careerProfileMarkdown: String,
        settings: UserSettingsEntity? = null
    ): Result<String> {
        val cleanProfile = AISafetyManager.sanitizeUserInput(careerProfileMarkdown)

        val prompt = """
            请根据以下用户的职业履历与日常工作总结，全面且结构化地提炼出【工作经历 (Work Experiences)】报告。

            【用户履历与日志数据】:
            <user_raw_content>
            $cleanProfile
            </user_raw_content>

            输出格式要求 (Markdown)：
            # 💼 工作经历总结

            ## 1. 核心岗位与职责概览
            - 描述主要担当的角色、核心工作职责与业务领域。

            ## 2. 关键工作产出与量化成果
            - 使用无序列表罗列核心成果，突出业务价值与效率提升。

            ## 3. 技术能力与工作胜任度
            - 归纳在日常工作经历中展现出的专业技能与协同能力。
        """.trimIndent()

        val systemInst = """
            ${AISafetyManager.SYSTEM_GUARDRAIL_PROMPT}
            你是资深职业顾问，负责从用户的工作记录中严谨提炼结构化【工作经历】。
        """.trimIndent()

        return generateContent(prompt, systemInst, settings)
    }

    /**
     * AI Feature 6: Generate AI Project Experiences summary.
     */
    suspend fun generateProjectExperiences(
        careerProfileMarkdown: String,
        settings: UserSettingsEntity? = null
    ): Result<String> {
        val cleanProfile = AISafetyManager.sanitizeUserInput(careerProfileMarkdown)

        val prompt = """
            请根据以下用户的职业履历与日常工作总结，全面且结构化地提炼出【项目经历 (Project Experiences)】报告。

            【用户履历与日志数据】:
            <user_raw_content>
            $cleanProfile
            </user_raw_content>

            输出格式要求 (Markdown)：
            # 🚀 项目经历总结

            ## 项目 1: [重点项目名称]
            - **项目背景与目标**: 简述项目解决的痛点
            - **技术栈与架构**: 涉及的核心技术与工具
            - **个人核心贡献**: 具体负责的模块与关键算法/功能开发
            - **项目成果与效果**: 交付成果、数据指标与上线收益

            （如有多个项目，按同样结构罗列）
        """.trimIndent()

        val systemInst = """
            ${AISafetyManager.SYSTEM_GUARDRAIL_PROMPT}
            你是资深技术专家，负责从用户的工作记录中提取清晰、具有代表性的【项目经历】。
        """.trimIndent()

        return generateContent(prompt, systemInst, settings)
    }

    /**
     * AI Feature 7: Semantic Search over historical daily work logs.
     */
    suspend fun semanticSearchLogs(
        query: String,
        logs: List<com.example.data.local.DailyWorkLogEntity>,
        settings: UserSettingsEntity? = null
    ): Result<List<SemanticSearchResult>> {
        if (logs.isEmpty() || query.isBlank()) {
            return Result.success(emptyList())
        }

        val logsPrepared = logs
            .filter { it.summaryMarkdown.isNotBlank() || it.rawNotes.isNotBlank() }
            .take(60)
            .map { log ->
                val content = if (log.summaryMarkdown.isNotBlank()) log.summaryMarkdown else log.rawNotes
                val preview = content.take(300).replace("\n", " ")
                "【日期: ${log.date}】: $preview"
            }.joinToString("\n\n")

        val cleanQuery = AISafetyManager.sanitizeUserInput(query)

        val prompt = """
            用户正在对过去的工作日记/日志库进行语义搜索。
            【搜索查询词/意图】: "$cleanQuery"

            【候选工作记录列表】:
            $logsPrepared

            请进行【语义检索与关联度评估】。分析每个日期记录与查询词在语义上的相关性（不仅限于字面精确匹配，要识别概念相关、技术关联、同义词、项目语境等）。

            请只返回相关度得分 > 40 的条目，按相关度得分从高到低排序。
            请严格按 JSON 数组格式返回（不要包含任何Markdown标签或额外文字）：
            [
              {
                "date": "YYYY-MM-DD",
                "relevanceScore": 85,
                "matchReason": "命中分析理由（如：讨论了数据库主从同步与索引优化的技术点）",
                "snippet": "该日期记录中关联度最高的关键摘要片段（50字左右）"
              }
            ]
            如果没有语义匹配的条目，请返回空数组 `[]`。
        """.trimIndent()

        val systemInst = """
            ${AISafetyManager.SYSTEM_GUARDRAIL_PROMPT}
            你是一个精通语义检索的AI系统，负责准确识别用户工作日志中与查询意图高度相关的历史条目。
        """.trimIndent()

        val res = generateContent(prompt, systemInst, settings)
        return res.map { jsonStr ->
            parseSearchResultsFromJson(jsonStr)
        }
    }

    // --- Helper JSON Parsers ---

    data class SemanticSearchResult(
        val date: String,
        val relevanceScore: Int,
        val matchReason: String,
        val snippet: String
    )

    data class ExtractedTodo(
        val title: String,
        val description: String,
        val priority: String,
        val category: String
    )

    data class CareerProfileUpdateResult(
        val shouldUpdate: Boolean,
        val reason: String,
        val updatedProfileMarkdown: String
    )

    private fun parseSearchResultsFromJson(jsonStr: String): List<SemanticSearchResult> {
        val list = mutableListOf<SemanticSearchResult>()
        val dateRegex = Regex("""^\d{4}-\d{2}-\d{2}$""")
        try {
            val cleanJson = extractJsonSubstring(jsonStr)
            val jsonArray = JSONArray(cleanJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val date = obj.optString("date", "")
                val score = obj.optInt("relevanceScore", 50)
                val reason = obj.optString("matchReason", "")
                val snippet = obj.optString("snippet", "")
                if (date.isNotBlank() && dateRegex.matches(date)) {
                    list.add(SemanticSearchResult(date, score, reason, snippet))
                }
            }
        } catch (e: Exception) {
            // Ignore parse error
        }
        return list.sortedByDescending { it.relevanceScore }
    }

    private fun parseTodosFromJson(jsonStr: String): List<ExtractedTodo> {
        val list = mutableListOf<ExtractedTodo>()
        try {
            val cleanJson = extractJsonSubstring(jsonStr)
            val jsonArray = JSONArray(cleanJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val title = obj.optString("title", "未命名任务")
                val desc = obj.optString("description", "")
                val priority = obj.optString("priority", "MEDIUM").uppercase()
                val category = obj.optString("category", "工作")
                list.add(ExtractedTodo(title, desc, priority, category))
            }
        } catch (e: Exception) {
            // Ignore parse errors, return empty list
        }
        return list
    }

    private fun parseCareerUpdateFromJson(jsonStr: String, fallbackProfile: String): CareerProfileUpdateResult {
        try {
            val cleanJson = extractJsonSubstring(jsonStr)
            val obj = JSONObject(cleanJson)
            val shouldUpdate = obj.optBoolean("shouldUpdate", false)
            val reason = obj.optString("reason", "")
            val updatedMd = obj.optString("updatedProfileMarkdown", "")
            
            return CareerProfileUpdateResult(
                shouldUpdate = shouldUpdate,
                reason = reason,
                updatedProfileMarkdown = if (shouldUpdate && updatedMd.isNotBlank()) updatedMd else fallbackProfile
            )
        } catch (e: Exception) {
            return CareerProfileUpdateResult(false, "解析响应失败: ${e.message}", fallbackProfile)
        }
    }

    private fun extractJsonSubstring(text: String): String {
        var clean = text.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json").removeSuffix("```").trim()
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```").removeSuffix("```").trim()
        }

        var start = -1
        var startChar = ' '
        for (i in clean.indices) {
            val ch = clean[i]
            if (ch == '{' || ch == '[') {
                start = i
                startChar = ch
                break
            }
        }
        if (start == -1) return clean

        val endChar = if (startChar == '{') '}' else ']'
        var depth = 0
        var inString = false
        var escape = false
        var end = -1

        for (i in start until clean.length) {
            val ch = clean[i]
            if (escape) {
                escape = false
                continue
            }
            if (ch == '\\' && inString) {
                escape = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (ch == startChar) depth++
                else if (ch == endChar) {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
        }

        return if (end != -1) clean.substring(start, end + 1) else clean
    }

    private fun parseErrorMessage(errorBody: String, statusCode: Int): String {
        try {
            val json = JSONObject(errorBody)
            val errorObj = json.optJSONObject("error")
            if (errorObj != null) {
                val msg = errorObj.optString("message", "")
                if (msg.isNotEmpty()) return "AI API 错误 ($statusCode): $msg"
            }
        } catch (e: Exception) {
            // Fallthrough
        }
        return "请求 AI API 失败 (HTTP $statusCode)"
    }
}
