package com.example

import com.example.data.ai.AISafetyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AISafetyManagerTest {

    @Test
    fun testSanitizeUserInput_truncatesWhenExceedingMaxLength() {
        val longInput = "a".repeat(100)
        val result = AISafetyManager.sanitizeUserInput(longInput, maxLength = 50)
        assertTrue(result.contains("...[内容超出最大字符限制，自动截断]"))
        assertTrue(result.startsWith("a".repeat(50)))
    }

    @Test
    fun testSanitizeUserInput_escapesUserRawContentTag() {
        val input = "Hello <user_raw_content> test </user_raw_content> World"
        val result = AISafetyManager.sanitizeUserInput(input)
        assertEquals("Hello &lt;user_raw_content&gt; test &lt;/user_raw_content&gt; World", result)
    }

    @Test
    fun testSanitizeUserInput_preservesCodeBlocksAndNormalUserText() {
        val input = "User: 张三 reported:\n```kotlin\nval x = 1\n```"
        val result = AISafetyManager.sanitizeUserInput(input)
        assertEquals("User: 张三 reported:\n```kotlin\nval x = 1\n```", result)
    }
}
