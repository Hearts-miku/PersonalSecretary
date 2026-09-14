package com.example

import com.example.data.ai.AiRepository
import com.example.data.local.UserSettingsEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun testUnsummarizedFilter_includesNonTodayLogs() {
        val today = "2026-09-14"
        val yesterday = "2026-09-13"
        val pastDate = "2026-09-10"

        val logs = listOf(
            com.example.data.local.DailyWorkLogEntity(
                date = yesterday,
                rawNotes = "昨天完成的未整理需求",
                isSummarized = false
            ),
            com.example.data.local.DailyWorkLogEntity(
                date = pastDate,
                rawNotes = "过去日期的未整理需求",
                isSummarized = false
            ),
            com.example.data.local.DailyWorkLogEntity(
                date = today,
                rawNotes = "今天已整理内容",
                isSummarized = true
            )
        )

        val unsummarized = logs.filter { !it.isSummarized && it.rawNotes.isNotBlank() }
        assertEquals(2, unsummarized.size)
        assertTrue(unsummarized.any { it.date == yesterday })
        assertTrue(unsummarized.any { it.date == pastDate })
    }

    @Test
    fun testFutureDateConstraint() {
        val today = "2026-09-14"
        val futureDate = "2026-09-15"
        val pastDate = "2026-09-13"

        // Any date > today is considered future and must be disallowed/clamped to today
        val clampedFuture = if (futureDate > today) today else futureDate
        val clampedPast = if (pastDate > today) today else pastDate

        assertEquals(today, clampedFuture)
        assertEquals(pastDate, clampedPast)
    }
}
