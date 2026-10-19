package com.example

import com.example.data.ai.AiRepository
import com.example.data.local.DailyWorkLogEntity
import com.example.data.local.UserSettingsEntity
import com.example.data.utils.DiffType
import com.example.data.utils.DiffUtils
import com.example.data.utils.WorkLogFilterUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AiRepositoryValidationTest {

    private val repository = AiRepository()

    @Test
    fun testGenerateContent_failsWhenSettingsNull() = runBlocking {
        val result = repository.generateContent(
            prompt = "Hello",
            settings = null
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Base URL") == true)
    }

    @Test
    fun testGenerateContent_failsWhenBaseUrlEmpty() = runBlocking {
        val settings = UserSettingsEntity(
            baseUrl = "",
            apiKey = "sk-12345",
            selectedModel = "gpt-4o"
        )
        val result = repository.generateContent(
            prompt = "Hello",
            settings = settings
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Base URL") == true)
    }

    @Test
    fun testGenerateContent_failsWhenApiKeyEmpty() = runBlocking {
        val settings = UserSettingsEntity(
            baseUrl = "https://api.openai.com/v1/",
            apiKey = "",
            selectedModel = "gpt-4o"
        )
        val result = repository.generateContent(
            prompt = "Hello",
            settings = settings
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("API Key") == true)
    }

    @Test
    fun testGenerateContent_failsWhenModelEmpty() = runBlocking {
        val settings = UserSettingsEntity(
            baseUrl = "https://api.openai.com/v1/",
            apiKey = "sk-12345",
            selectedModel = ""
        )
        val result = repository.generateContent(
            prompt = "Hello",
            settings = settings
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("模型名称") == true)
    }

    @Test
    fun testTestConnection_failsWhenBaseUrlEmpty() = runBlocking {
        val result = repository.testConnection(
            baseUrl = "",
            apiKey = "sk-12345",
            model = "gpt-4o"
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Base URL") == true)
    }

    @Test
    fun testTestConnection_failsWhenBaseUrlInvalidScheme() = runBlocking {
        val result = repository.testConnection(
            baseUrl = "ftp://invalid-url.com",
            apiKey = "sk-12345",
            model = "gpt-4o"
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("http://") == true)
    }

    @Test
    fun testTestConnection_failsWhenApiKeyEmpty() = runBlocking {
        val result = repository.testConnection(
            baseUrl = "https://api.openai.com/v1/",
            apiKey = "",
            model = "gpt-4o"
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("API Key") == true)
    }

    @Test
    fun testTestConnection_failsWhenModelEmpty() = runBlocking {
        val result = repository.testConnection(
            baseUrl = "https://api.openai.com/v1/",
            apiKey = "sk-12345",
            model = ""
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("模型名称") == true)
    }

    @Test
    fun testValidateEndpointConfig() {
        assertTrue(AiRepository.validateEndpointConfig("", "key", "model").isFailure)
        assertTrue(AiRepository.validateEndpointConfig("ftp://example.com", "key", "model").isFailure)
        assertTrue(AiRepository.validateEndpointConfig("https://example.com", "", "model").isFailure)
        assertTrue(AiRepository.validateEndpointConfig("https://example.com", "key", "").isFailure)
        assertTrue(AiRepository.validateEndpointConfig("https://api.openai.com/v1", "sk-xxx", "gpt-4o").isSuccess)
    }

    @Test
    fun testNormalizeEndpoint() {
        assertEquals("https://api.openai.com/v1/chat/completions", AiRepository.normalizeEndpoint("https://api.openai.com/v1"))
        assertEquals("https://api.openai.com/v1/chat/completions", AiRepository.normalizeEndpoint("https://api.openai.com/v1/"))
        assertEquals("https://api.openai.com/v1/chat/completions", AiRepository.normalizeEndpoint("https://api.openai.com/v1/chat/completions"))
        assertEquals("http://192.168.1.100:11434/v1/chat/completions", AiRepository.normalizeEndpoint("http://192.168.1.100:11434/v1/"))
        assertEquals("http://localhost:8000/v1/chat/completions", AiRepository.normalizeEndpoint("http://localhost:8000/v1"))
        assertEquals("http://localhost:8000/chat/completions", AiRepository.normalizeEndpoint("http://localhost:8000"))
    }

    @Test
    fun testWorkLogFilterUtils_filterUnsummarizedLogs() {
        val logs = listOf(
            DailyWorkLogEntity(date = "2026-09-13", rawNotes = "未整理记录", isSummarized = false),
            DailyWorkLogEntity(date = "2026-09-12", rawNotes = "   ", isSummarized = false),
            DailyWorkLogEntity(date = "2026-09-11", rawNotes = "已整理记录", isSummarized = true)
        )
        val unsummarized = WorkLogFilterUtils.filterUnsummarizedLogs(logs)
        assertEquals(1, unsummarized.size)
        assertEquals("2026-09-13", unsummarized.first().date)
    }

    @Test
    fun testWorkLogFilterUtils_clampDateNotAfterToday() {
        val today = "2026-09-14"
        assertEquals(today, WorkLogFilterUtils.clampDateNotAfterToday("2026-09-20", today))
        assertEquals("2026-09-10", WorkLogFilterUtils.clampDateNotAfterToday("2026-09-10", today))
        assertEquals(today, WorkLogFilterUtils.clampDateNotAfterToday(today, today))
    }

    @Test
    fun testDiffUtils_basicAndPrefixSuffix() {
        val oldDoc = "Line1\nLine2\nLine3"
        val newDoc = "Line1\nLine2 Modified\nLine3"
        val diff = DiffUtils.computeDiff(oldDoc, newDoc)

        assertEquals(4, diff.size)
        assertEquals(DiffType.UNCHANGED, diff[0].type)
        assertEquals("Line1", diff[0].text)
        assertEquals(DiffType.REMOVED, diff[1].type)
        assertEquals("Line2", diff[1].text)
        assertEquals(DiffType.ADDED, diff[2].type)
        assertEquals("Line2 Modified", diff[2].text)
        assertEquals(DiffType.UNCHANGED, diff[3].type)
        assertEquals("Line3", diff[3].text)
    }

    @Test
    fun testN40_reasoningOnlyResponseIsRejectedAsFailure() {
        val jsonStr = """
            {
                "choices": [
                    {
                        "finish_reason": "stop",
                        "message": {
                            "role": "assistant",
                            "content": "",
                            "reasoning_content": "INTERNAL_DRAFT_NOT_FINAL"
                        }
                    }
                ]
            }
        """.trimIndent()
        val result = AiRepository.parseOpenAiChatResponse(jsonStr)
        assertTrue("Reasoning-only response must be treated as failure", result.isFailure)
        assertTrue(result.exceptionOrNull() is com.example.data.ai.AiReasoningOnlyException)
        assertTrue(result.exceptionOrNull()?.message?.contains("思考推理") == true)
    }

    @Test
    fun testN40_truncatedResponseIsRejectedAsFailure() {
        val jsonStr = """
            {
                "choices": [
                    {
                        "finish_reason": "length",
                        "message": {
                            "role": "assistant",
                            "content": "这是一份未生成完整的被截断报告..."
                        }
                    }
                ]
            }
        """.trimIndent()
        val result = AiRepository.parseOpenAiChatResponse(jsonStr)
        assertTrue("Truncated length response must be treated as failure", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is com.example.data.ai.AiTruncatedException)
        assertEquals("这是一份未生成完整的被截断报告...", (ex as com.example.data.ai.AiTruncatedException).partialContent)
    }

    @Test
    fun testN40_validFinalResponseIsAccepted() {
        val jsonStr = """
            {
                "choices": [
                    {
                        "finish_reason": "stop",
                        "message": {
                            "role": "assistant",
                            "content": "# 工作日报\n今天完成了核心模块构建与测试。"
                        }
                    }
                ]
            }
        """.trimIndent()
        val result = AiRepository.parseOpenAiChatResponse(jsonStr)
        assertTrue("Valid response must be accepted as success", result.isSuccess)
        assertEquals("# 工作日报\n今天完成了核心模块构建与测试。", result.getOrNull())
    }

    @Test
    fun testN40_realAiRepositoryWithLocalServer_reasoningOnlyFails() = runBlocking {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        try {
            server.createContext("/chat/completions") { exchange ->
                val json = """
                    {
                        "choices": [
                            {
                                "finish_reason": "stop",
                                "message": {
                                    "role": "assistant",
                                    "content": "",
                                    "reasoning_content": "INTERNAL_DRAFT_NOT_FINAL"
                                }
                            }
                        ]
                    }
                """.trimIndent().toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, json.size.toLong())
                exchange.responseBody.use { it.write(json) }
            }
            server.start()

            val port = server.address.port
            val settings = UserSettingsEntity(
                baseUrl = "http://127.0.0.1:$port",
                apiKey = "test-key-fake",
                selectedModel = "test-model"
            )

            val res = repository.generateContent(prompt = "test prompt", settings = settings)
            assertTrue("Reasoning-only response over real HTTP socket must be failure", res.isFailure)
            assertTrue(res.exceptionOrNull() is com.example.data.ai.AiReasoningOnlyException)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun testN40_realAiRepositoryWithLocalServer_truncatedFails() = runBlocking {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        try {
            server.createContext("/chat/completions") { exchange ->
                val json = """
                    {
                        "choices": [
                            {
                                "finish_reason": "length",
                                "message": {
                                    "role": "assistant",
                                    "content": "半截文本..."
                                }
                            }
                        ]
                    }
                """.trimIndent().toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, json.size.toLong())
                exchange.responseBody.use { it.write(json) }
            }
            server.start()

            val port = server.address.port
            val settings = UserSettingsEntity(
                baseUrl = "http://127.0.0.1:$port",
                apiKey = "test-key-fake",
                selectedModel = "test-model"
            )

            val res = repository.generateContent(prompt = "test prompt", settings = settings)
            assertTrue("Truncated length response over real HTTP socket must be failure", res.isFailure)
            val ex = res.exceptionOrNull()
            assertTrue(ex is com.example.data.ai.AiTruncatedException)
            assertEquals("半截文本...", (ex as com.example.data.ai.AiTruncatedException).partialContent)
        } finally {
            server.stop(0)
        }
    }
}
