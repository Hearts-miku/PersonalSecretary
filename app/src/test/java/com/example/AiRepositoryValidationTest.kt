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
}
