package com.example.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 敏感数据安全加解密管理器。
 * 采用 Android Keystore + AES-256-GCM 硬件/系统级安全加密存储，
 * 并向前兼容历史 v1 (Base64) 与纯文本配置。
 */
object CryptoManager {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "PersonalSecretaryKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    private const val PREFIX_V2 = "v2:"
    private const val PREFIX_V1 = "v1:"

    private fun base64Encode(bytes: ByteArray): String {
        return try {
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (e: Throwable) {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }

    private fun base64Decode(str: String): ByteArray {
        return try {
            java.util.Base64.getDecoder().decode(str)
        } catch (e: Throwable) {
            Base64.decode(str, Base64.NO_WRAP)
        }
    }

    private fun logWarn(tag: String, msg: String) {
        try {
            android.util.Log.w(tag, msg)
        } catch (e: Throwable) {
            // JVM environment without Android Log
        }
    }

    private fun logError(tag: String, msg: String) {
        try {
            android.util.Log.e(tag, msg)
        } catch (e: Throwable) {
            // JVM environment without Android Log
        }
    }

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            // 在不支持或缺少 AndroidKeyStore 的测试环境下兜底返回 null
            null
        }
    }

    fun encode(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val secretKey = getOrCreateSecretKey()
        if (secretKey != null) {
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                val combined = ByteArray(iv.size + encryptedBytes.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
                return "$PREFIX_V2${base64Encode(combined)}"
            } catch (e: Exception) {
                logWarn("CryptoManager", "Keystore AES-GCM 加密失败，降级至 v1: ${e.message}")
            }
        } else {
            logWarn("CryptoManager", "未获取到 Keystore 密钥，降级至 v1")
        }

        // 降级兜底方案
        val encoded = base64Encode(plainText.toByteArray(Charsets.UTF_8))
        return "$PREFIX_V1$encoded"
    }

    fun decode(encodedText: String): String {
        if (encodedText.isEmpty()) return ""

        if (encodedText.startsWith(PREFIX_V2)) {
            val secretKey = getOrCreateSecretKey()
            if (secretKey != null) {
                try {
                    val rawCombined = base64Decode(encodedText.removePrefix(PREFIX_V2))
                    if (rawCombined.size > GCM_IV_LENGTH) {
                        val iv = ByteArray(GCM_IV_LENGTH)
                        val cipherText = ByteArray(rawCombined.size - GCM_IV_LENGTH)
                        System.arraycopy(rawCombined, 0, iv, 0, GCM_IV_LENGTH)
                        System.arraycopy(rawCombined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

                        val cipher = Cipher.getInstance(TRANSFORMATION)
                        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                        val decrypted = cipher.doFinal(cipherText)
                        return String(decrypted, Charsets.UTF_8)
                    }
                } catch (e: Exception) {
                    logError("CryptoManager", "Keystore AES-GCM 解密失败: ${e.message}")
                }
            }
            // 解密失败返回空字符串，避免将密文乱码直接当作明文 API Key 发出或展示
            return ""
        }

        if (encodedText.startsWith(PREFIX_V1)) {
            val actualEncodedText = encodedText.removePrefix(PREFIX_V1)
            return try {
                String(base64Decode(actualEncodedText), Charsets.UTF_8)
            } catch (e: Exception) {
                logError("CryptoManager", "Base64 解密失败: ${e.message}")
                ""
            }
        }

        // 无前缀纯文本直接返回兼容
        return encodedText
    }
}
