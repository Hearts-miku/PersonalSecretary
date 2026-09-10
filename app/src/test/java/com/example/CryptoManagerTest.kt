package com.example

import com.example.data.local.CryptoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoManagerTest {

    @Test
    fun testEncodeDecode_emptyString() {
        assertEquals("", CryptoManager.encode(""))
        assertEquals("", CryptoManager.decode(""))
    }

    @Test
    fun testEncodeDecode_plainTextRoundTrip() {
        val original = "sk-test-api-key-1234567890abcdef"
        val encoded = CryptoManager.encode(original)

        // In pure JVM environment without Android Keystore, CryptoManager degrades gracefully to v1:
        assertTrue(encoded.startsWith("v1:") || encoded.startsWith("v2:"))

        val decoded = CryptoManager.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun testDecode_v1LegacyPrefix() {
        // v1: format is Base64 encoded plaintext
        // Base64("sk-legacy-key-999") = "c2stbGVnYWN5LWtleS05OTk="
        val v1Cipher = "v1:c2stbGVnYWN5LWtleS05OTk="
        val decoded = CryptoManager.decode(v1Cipher)
        assertEquals("sk-legacy-key-999", decoded)
    }

    @Test
    fun testDecode_rawPlainTextFallback() {
        // Backward compatibility: raw plain text without prefix should be returned as-is
        val rawPlain = "raw-unencrypted-key-value"
        val decoded = CryptoManager.decode(rawPlain)
        assertEquals(rawPlain, decoded)
    }

    @Test
    fun testDecode_corruptedV1ReturnsEmpty() {
        val corruptedV1 = "v1:???not_valid_base64!!!"
        val decoded = CryptoManager.decode(corruptedV1)
        // Hardened: fail-safe returns empty string rather than ciphertext
        assertEquals("", decoded)
    }

    @Test
    fun testDecode_corruptedV2ReturnsEmpty() {
        val corruptedV2 = "v2:invalid_gcm_ciphertext_payload"
        val decoded = CryptoManager.decode(corruptedV2)
        // Hardened: fail-safe returns empty string rather than ciphertext
        assertEquals("", decoded)
    }
}
