package com.example.data.local

import android.util.Base64

/**
 * 注意：此处仅提供 Base64 编码，不提供机密性保护。
 */
object CryptoManager {
    private const val PREFIX = "v1:"

    fun encode(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val encoded = Base64.encodeToString(plainText.toByteArray(), Base64.DEFAULT)
        return "$PREFIX$encoded"
    }

    fun decode(encodedText: String): String {
        if (encodedText.isEmpty()) return ""
        if (!encodedText.startsWith(PREFIX)) {
            return encodedText
        }
        val actualEncodedText = encodedText.removePrefix(PREFIX)
        return try {
            String(Base64.decode(actualEncodedText, Base64.DEFAULT))
        } catch (e: Exception) {
            encodedText
        }
    }
}
