package com.sameerasw.airsync.utils

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtil {

    private const val AES_GCM_NOPADDING = "AES/GCM/NoPadding"
    private const val NONCE_SIZE_BYTES = 12
    private const val TAG_SIZE_BITS = 128

    fun decodeKey(base64Key: String): SecretKey? {
        return try {
            val sanitized = base64Key.replace(" ", "+")
            val keyBytes = Base64.decode(sanitized, Base64.DEFAULT)
            SecretKeySpec(keyBytes, "AES")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decryptMessage(base64Combined: String, key: SecretKey): String? {
        return try {
            val combined = Base64.decode(base64Combined, Base64.DEFAULT)

            if (combined.size < NONCE_SIZE_BYTES) {
                return null // Invalid message
            }

            val nonce = combined.copyOfRange(0, NONCE_SIZE_BYTES)
            val ciphertextWithTag = combined.copyOfRange(NONCE_SIZE_BYTES, combined.size)

            val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
            val spec = GCMParameterSpec(TAG_SIZE_BITS, nonce)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val plainBytes = cipher.doFinal(ciphertextWithTag)
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun encryptMessage(message: String, key: SecretKey): String? {
        return try {
            val nonce = ByteArray(NONCE_SIZE_BYTES)
            SecureRandom().nextBytes(nonce)

            val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
            val spec = GCMParameterSpec(TAG_SIZE_BITS, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)

            val ciphertext = cipher.doFinal(message.toByteArray(StandardCharsets.UTF_8))

            val combined = nonce + ciphertext
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

