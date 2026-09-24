package com.example.data.ai

import com.example.data.local.DailyWorkLogEntity
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
 * 统一的 AI 模型调用与提示词处理仓库。
 * 基于标准 OpenAI 兼容接口协议，支持各类自定义模型端点。
 */
class AiRepository {

    companion object {
        /**
         * 规范化 OpenAI 兼容接口端点 URL（N-23）。
         * 无论输入是带/不带尾斜杠的 baseUrl，还是已包含 chat/completions，均规范化为完整且唯一的端点。
         */
        fun normalizeEndpoint(baseUrl: String): String {
            val trimmed = baseUrl.trim()
            if (trimmed.isEmpty()) return ""
            val withoutTrailingSlash = if (trimmed.endsWith("/")) trimmed.dropLast(1) else trimmed
            return if (withoutTrailingSlash.endsWith("chat/completions")) {
                withoutTrailingSlash
            } else {
                "$withoutTrailingSlash/chat/completions"
            }
        }

        /**
         * 统一校验端点配置合法性（N-28），供 ViewModel 与 AiRepository 共享调用。
         */
        fun validateEndpointConfig(baseUrl: String, apiKey: String, model: String): Result<Unit> {
            val trimmedUrl = baseUrl.trim()
            val trimmedKey = apiKey.trim()
            val trimmedModel = model.trim()

            if (trimmedUrl.isBlank()) {
                return Result.failure(IllegalArgumentException("未配置 Base URL。请输入有效的 API Base URL。"))
            }
            if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                return Result.failure(IllegalArgumentException("Base URL 必须以 http:// 或 https:// 开头。"))
            }
            if (trimmedKey.isBlank()) {
                return Result.failure(IllegalArgumentException("未配置 API Key。请输入您的 API Key。"))
            }
            if (trimmedModel.isBlank()) {
                return Result.failure(IllegalArgumentException("未配置模型名称。请输入有效的模型标识。"))
            }
            return Result.success(Unit)
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getEffectiveApiKey(settings: UserSettingsEntity?): String {
        return settings?.apiKey?.trim().orEmpty()
    }

    private fun getEffectiveBaseUrl(settings: UserSettingsEntity?): String {
        return settings?.baseUrl?.trim().orEmpty()
    }

    private fun getEffectiveModel(settings: UserSettingsEntity?): String {
        return settings?.selectedModel?.trim().orEmpty()
    }

    /**
     * 通用 AI 内容生成入口（基于用户配置的自定义 OpenAI 兼容接口）。
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

            val validation = validateEndpointConfig(baseUrl, apiKey, model)
            if (validation.isFailure) {
                return@withContext Result.failure(IllegalStateException(validation.exceptionOrNull()?.message ?: "配置无效"))
            }

            val endpoint = normalizeEndpoint(baseUrl)
            callOpenAiApi(endpoint, apiKey, model, prompt, systemInstruction)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 测试用户配置的自定义 OpenAI 兼容接口连通性。
     */
    suspend fun testConnection(
        baseUrl: String,
        apiKey: String,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val validation = validateEndpointConfig(baseUrl, apiKey, model)
            if (validation.isFailure) {
                return@withContext Result.failure(validation.exceptionOrNull()!!)
            }

            val endpoint = normalizeEndpoint(baseUrl)
            callOpenAiApi(
                endpoint = endpoint,
                apiKey = apiKey.trim(),
                model = model.trim(),
                prompt = "请回复“OK”两个字母以测试 API 连接连通性。",
                systemInstruction = "You are a connectivity test assistant. Respond briefly with 'OK'."
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun callOpenAiApi(
        endpoint: String,
        apiKey: String,
        model: String,
        prompt: String,
        systemInstruction: String?
    ): Result<String> {
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
                    return Result.failure(Exception("AI 输出因达到最大 Token 长度限制产生截断"))
                }
                val message = firstChoice.optJSONObject("message")
                val content = message?.optString("content", "") ?: ""
                if (content.isNotBlank()) {
                    return Result.success(content)
                }
            }
            return Result.failure(Exception("AI 未返回有效内容"))
        }
    }

    /**
     * AI 功能 1：将用户工作碎记整理为结构化 Markdown 总结。
     */
    suspend fun summarizeDailyNotes(
        date: String,
        rawNotes: String,
        settings: UserSettingsEntity? = null
    ): Result<String> {
        val cleanNotes = AISafetyManager.sanitizeUserInput(rawNotes)
        val prompt = """
            日期: $date
            以下是用户记录的原始工作流水或随手笔记：
            <user_raw_content>
            $cleanNotes
            </user_raw_content>

            请将上述工作记录提炼并整理为一份专业、清晰的工作日报 Markdown 文档。
            要求：
            1. 包含【今日核心进展】、【攻坚问题与解决方案】、【关键成果与产出数据】三个核心部分。
            2. 语言书面化、条理清晰，多用客观指标与事实陈述。
            3. 严格遵守客观事实，不得无中生有。
            4. 直接返回排版优雅的 Markdown 文本。
        """.trimIndent()

        val systemInst = """
            ${AISafetyManager.SYSTEM_GUARDRAIL_PROMPT}
            你是一名专业的高级技术总监兼效率助手，擅长将工程师散乱的日常随笔转化为规范严谨的工作日报总结。
        """.trimIndent()

        return generateContent(prompt, systemInst, settings)
    }

    /**
     * AI 功能 2：从原始日记中自动提取后续待办。
     * 关键要求：按识别到的【项目/业务模块】粒度进行归纳整合，避免任务切分过碎。
     */
    suspend fun extractTodos(
        date: String,
        rawNotes: String,
        settings: UserSettingsEntity? = null
    ): Result<List<ExtractedTodo>> {
        val cleanNotes = AISafetyManager.sanitizeUserInput(rawNotes)
        val prompt = """
            日期: $date
            以下是用户记录的工作笔记：
            <user_raw_content>
            $cleanNotes
            </user_raw_content>

            【任务要求：按识别到的【项目/业务模块】粒度整理待办事项】
            1. 严禁琐碎拆分：切勿将细小操作（如“发个邮件”、“改个参数”、“查下日志”、“找张三确认”）单独切分成多条微小待办。
            2. 项目粒度归纳与聚合：
               - 从笔记中识别具体的工程项目名称、系统模块或业务线（例如“支付结算中台”、“用户增长活动”、“数据看板大屏”等）。
               - 以每个识别到的项目为单位进行整合，梳理出该项目后续的核心跟进事项或里程碑任务（每个项目通常聚合为1条核心待办；若存在多项重大交付物，单项目最多不超过2条）。
               - 若笔记内容未提及具体项目名，请按核心技术领域或业务方向聚合（如“公共技术基建”、“团队协同协作”）。
            3. 格式规范：
               - title: 必须统一采用格式 `【项目名称】核心任务概括`，突出项目主体与交付目标。
               - description: 将该项目下的具体行动子项、技术要点或沟通细节整理为清晰的序号列表（例如：“1. 排查超时重试机制；2. 与产品对齐验签规范；3. 补充单测”），确保“宏观清晰、微观详尽”。
               - category: 必须填写识别出的【项目名称】或【模块名称】。
               - priority: 评估该项目待办的紧迫程度（HIGH / MEDIUM / LOW）。

            如果没有待办，请返回空数组 `[]`。
            如果有待办，请严格按如下标准 JSON 数组格式返回（不要包含 Markdown 标记）：
            [
              {
                "title": "【项目名称】核心任务概括",
                "description": "1. 具体执行子项一；2. 具体执行子项二；3. 风险与注意事项",
                "priority": "HIGH" 或 "MEDIUM" 或 "LOW",
                "category": "识别到的项目或模块名称"
              }
            ]
        """.trimIndent()

        val systemInst = """
            ${AISafetyManager.SYSTEM_GUARDRAIL_PROMPT}
            你是一名资深技术项目经理（TPM），擅长从工程师繁杂的工作流水中，按【项目/业务模块】粒度提炼出结构化、具备里程碑价值的高层级待办事项，杜绝碎片化微观任务。
        """.trimIndent()

        val res = generateContent(prompt, systemInst, settings)
        return res.map { jsonStr ->
            parseTodosFromJson(jsonStr)
        }
    }

    /**
     * AI 功能 3：基于今日工作总结评估并增量更新职业档案。
     */
    suspend fun evaluateCareerProfileUpdate(
        currentProfileMarkdown: String,
        todaySummaryMarkdown: String,
        settings: UserSettingsEntity? = null
    ): Result<CareerProfileUpdateResult> {
        val cleanProfile = AISafetyManager.sanitizeUserInput(currentProfileMarkdown, tagName = "current_profile")
        val cleanSummary = AISafetyManager.sanitizeUserInput(todaySummaryMarkdown, tagName = "today_summary")

        val prompt = """
            【用户现有的职业档案全貌】:
            <current_profile>
            $cleanProfile
            </current_profile>

            【用户今日完成的工作总结】:
            <today_summary>
            $cleanSummary
            </today_summary>

            请评估今日的工作总结是否包含对该用户的职业生涯有长期积累价值的关键技能突破、重大系统架构设计、显著业务产出或核心攻坚成果。
            如果仅为日常琐碎或重复性维护，无需更新档案。
            如果包含重要亮点，请将亮点有机增量融合进该用户的职业档案 Markdown 中，保持整体结构完整统一。

            请严格按 JSON 格式返回：
            {
              "shouldUpdate": true 或 false,
              "reason": "评估更新或不更新的具体原因",
              "updatedProfileMarkdown": "更新后的完整职业档案Markdown文本（若不需要更新，填空字符串即可）"
            }
        """.trimIndent()

        val systemInst = """
            ${AISafetyManager.SYSTEM_GUARDRAIL_PROMPT}
            你是一名资深的职业生涯规划师兼履历顾问。请审慎评估，只记录有长期职业价值的亮点，严禁虚构夸大。
        """.trimIndent()

        val res = generateContent(prompt, systemInst, settings)
        return res.map { jsonStr ->
            parseCareerUpdateFromJson(jsonStr, currentProfileMarkdown)
        }
    }

    /**
     * AI 功能 4：全量提炼工作经历。
     */
    suspend fun generateWorkExperiences(
        careerContext: String,
        settings: UserSettingsEntity? = null
    ): Result<String> {
        val cleanContext = AISafetyManager.sanitizeUserInput(careerContext)
        val prompt = """
            以下是用户长期的日常工作日志沉淀与职业全貌上下文：
            <user_raw_content>
            $cleanContext
            </user_raw_content>

            请根据上述事实，为用户系统性提炼并总结【工作经历】模块。
            要求：
            1. 按时间倒序或核心岗位/项目维度归类。
            2. 每段经历采用标准的 STAR 法则（情境、任务、行动、结果），突出技术深度与量化业务成果。
            3. 排版采用清晰优雅的 Markdown 格式。
            4. 严格基于上下文事实，严禁凭空捏造。
        """.trimIndent()

        val systemInst = """
            ${AISafetyManager.SYSTEM_GUARDRAIL_PROMPT}
            你是资深技术总监与猎头顾问，擅长将技术人员的工作沉淀提炼为极具说服力的工作经历。
        """.trimIndent()

        return generateContent(prompt, systemInst, settings)
    }

    /**
     * AI 功能 5：全量提炼项目经历。
     */
    suspend fun generateProjectExperiences(
        careerContext: String,
        settings: UserSettingsEntity? = null
    ): Result<String> {
        val cleanContext = AISafetyManager.sanitizeUserInput(careerContext)
        val prompt = """
            以下是用户长期的日常工作日志沉淀与职业全貌上下文：
            <user_raw_content>
            $cleanContext
            </user_raw_content>

            请根据上述事实，为用户系统性提取并总结【项目经历】模块。
            要求：
            1. 提取 2~5 个最具代表性和技术挑战的核心项目。
            2. 每个项目包含：【项目背景与目标】、【核心架构与个人职责】、【关键技术攻坚点】、【项目成果与量化收益】。
            3. 使用规范严谨的 Markdown 格式输出。
            4. 严格基于事实提炼，严禁凭空捏造。
        """.trimIndent()

        val systemInst = """
            ${AISafetyManager.SYSTEM_GUARDRAIL_PROMPT}
            你是资深架构师兼技术履历评委，负责将日常研发记录提炼为结构化高水平的项目经历。
        """.trimIndent()

        return generateContent(prompt, systemInst, settings)
    }

    /**
     * AI 功能 6：基于职业履历全貌一键生成求职简历。
     */
    suspend fun generateUserResume(
        careerProfileMarkdown: String,
        templateStyle: String = "Modern",
        settings: UserSettingsEntity? = null
    ): Result<String> {
        val cleanProfile = AISafetyManager.sanitizeUserInput(careerProfileMarkdown)
        val styleInstruction = when (templateStyle) {
            "Classic" -> "采用传统经典商务风格：结构稳健，用词严谨克制，强调专业度与可靠性。"
            "Minimal" -> "采用极简干练风格：精简字句，直击重点，以纯粹的数据与技术栈呈现最强干货。"
            else -> "采用现代科技互联网风格：注重影响力、敏捷迭代、架构思维与业务赋能。"
        }

        val prompt = """
            以下是该候选人的全部真实职业履历背景沉淀：
            <user_raw_content>
            $cleanProfile
            </user_raw_content>

            风格偏好：$styleInstruction

            请生成一份完整的求职简历 Markdown 文档。
            必须遵守以下严格要求：
            1. 【隐私保护规范】：所有候选人敏感个人信息必须全部使用通用占位符（如 `[姓名]`、`[联系电话]`、`[电子邮箱]`、`[居住城市]`、`[求职意向]`、`[毕业院校]`）。
            2. 【结构完整】：包含个人信息、教育背景、专业技能概览、工作经历、核心项目经历、自我评价等模块。
            3. 【STAR法则】：经历与项目严格遵循 STAR 法则，突出技术攻坚与量化指标。
            4. 【严禁虚构】：完全基于提供的背景，不得捏造不存在的公司、学历或虚假数据。
            5. 直接输出 Markdown 文本，不要附加多余的问候语。
        """.trimIndent()

        val systemInst = """
            ${AISafetyManager.SYSTEM_GUARDRAIL_PROMPT}
            你是一名世界顶级科技公司的资深招聘官与简历大师，帮助用户将其履历打造为极具竞争力的简历。
        """.trimIndent()

        return generateContent(prompt, systemInst, settings)
    }

    /**
     * AI 功能 7：历史工作日志语义检索 (P1-16: 包含隔离沙箱与字符预算保护)。
     */
    suspend fun semanticSearchLogs(
        query: String,
        logs: List<DailyWorkLogEntity>,
        settings: UserSettingsEntity? = null
    ): Result<List<SemanticSearchResult>> {
        if (logs.isEmpty() || query.isBlank()) {
            return Result.success(emptyList())
        }

        val cleanQuery = AISafetyManager.sanitizeUserInput(query, maxLength = 1000)

        val sb = StringBuilder()
        var accumulatedLength = 0
        val maxBudget = 18000

        for (log in logs.filter { it.summaryMarkdown.isNotBlank() || it.rawNotes.isNotBlank() }) {
            val content = if (log.summaryMarkdown.isNotBlank()) log.summaryMarkdown else log.rawNotes
            val preview = content.take(300).replace("\n", " ")
            val sanitizedPreview = AISafetyManager.sanitizeUserInput(preview, maxLength = 300)
            val line = "【日期: ${log.date}】: $sanitizedPreview\n\n"
            if (accumulatedLength + line.length > maxBudget) break
            sb.append(line)
            accumulatedLength += line.length
        }

        val logsPrepared = sb.toString().trim()

        val prompt = """
            用户正在对过去的工作日记/日志库进行语义搜索。
            【搜索查询词/意图】:
            <user_raw_content>
            $cleanQuery
            </user_raw_content>

            【候选工作记录列表】:
            <user_raw_content>
            $logsPrepared
            </user_raw_content>

            请进行【语义检索与关联度评估】。分析每个日期记录与查询词在语义上的相关性（识别概念相关、技术关联、同义词、项目语境等）。
            请只返回相关度得分 > 40 的条目，按相关度得分从高到低排序。
            请严格按 JSON 数组格式返回（不要包含任何 Markdown 标签或额外文字）：
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

    /**
     * AI 功能 8：提炼导入的文件文本。
     */
    suspend fun refineImportedContent(
        rawContent: String,
        targetCategory: String,
        settings: UserSettingsEntity? = null
    ): Result<String> {
        val cleanContent = AISafetyManager.sanitizeUserInput(rawContent, tagName = "imported_data")
        val prompt = """
            用户导入了一份文件，希望将其格式化并提炼为标准的【$targetCategory】。
            以下是用户导入的原始内容：
            <imported_data>
            $cleanContent
            </imported_data>

            请你作为资深的技术专家与HR，对这些内容进行整理、提炼和润色：
            1. 剔除无效或冗余信息，突出核心价值。
            2. 使用专业的书面表达。
            3. 如果是工作经历或项目经历，请采用清晰的要点列举结构（如 STAR 法则）。
            4. 保持隐私脱敏（姓名、电话等使用占位符）。
            5. 请直接输出提炼后的 Markdown 内容，不要包含任何额外的问候语、解释或 json 包装。
        """.trimIndent()

        val systemInst = """
            ${AISafetyManager.SYSTEM_GUARDRAIL_PROMPT}
            你是资深技术专家，负责将用户导入的文本提炼为结构化、高质量的简历素材。
        """.trimIndent()

        return generateContent(prompt, systemInst, settings)
    }

    // --- 数据模型与 JSON 解析工具 ---

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
                val rawTitle = obj.optString("title", "未命名任务").trim()
                val desc = obj.optString("description", "").trim()
                val priority = obj.optString("priority", "MEDIUM").uppercase()
                val rawCategory = obj.optString("category", "通用项目").trim().ifBlank { "通用项目" }

                // 规范化标题：确保在有明确项目名称时突出展示【项目名称】
                val finalTitle = if (!rawTitle.startsWith("【") && rawCategory.isNotBlank() && rawCategory != "工作" && rawCategory != "Work") {
                    "【$rawCategory】$rawTitle"
                } else {
                    rawTitle
                }

                list.add(ExtractedTodo(finalTitle, desc, priority, rawCategory))
            }
        } catch (e: Exception) {
            // Ignore parse errors, return empty list
        }
        return list
    }

    private fun parseCareerUpdateFromJson(jsonStr: String, fallbackProfile: String): CareerProfileUpdateResult {
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
