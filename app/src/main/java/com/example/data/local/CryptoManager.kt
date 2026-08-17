package com.example.data.local

import android.util.Base64

object CryptoManager {
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return Base64.encodeToString(plainText.toByteArray(), Base64.DEFAULT)
    }

    fun decrypt(encryptedText: String): String {
        if (encryptedText.isEmpty()) return ""
        return try {
            String(Base64.decode(encryptedText, Base64.DEFAULT))
        } catch (e: Exception) {
            encryptedText
        }
    }
}
