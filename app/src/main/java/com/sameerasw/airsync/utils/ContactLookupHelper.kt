package com.sameerasw.airsync.utils

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Helper class for phone number normalization and contact lookup with caching.
 * Uses libphonenumber for robust number formatting and PhoneLookup for contact matching.
 */
class ContactLookupHelper(private val context: Context) {

    private val phoneNumberUtil = PhoneNumberUtil.getInstance()
    private val contactCache = ConcurrentHashMap<String, CachedContact>()
    private val negativeCache = ConcurrentHashMap<String, Long>()

    private data class CachedContact(
        val displayName: String?,
        val cachedAtMillis: Long
    )

    /**
     * Normalize a phone number to E.164 format for consistent lookups.
     * Intelligently detects country code from number or uses device locale.
     * Falls back to the original number if normalization fails.
     */
    fun normalizeNumber(number: String, defaultRegion: String? = null): String {
        return try {
            val region = defaultRegion ?: detectCountryCode(number) ?: getDeviceCountryCode()
            val parsed = phoneNumberUtil.parse(number, region)
            phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        } catch (e: Exception) {
            // If parsing fails, return the original number
            number
        }
    }

    /**
     * Detect country code from phone number format
     * Returns null if cannot detect (will fallback to device locale)
     */
    private fun detectCountryCode(number: String): String? {
        return try {
            when {
                // If number starts with +, try to parse without region hint
                number.startsWith("+") -> {
                    val parsed = phoneNumberUtil.parse(number, "")
                    phoneNumberUtil.getRegionCodeForNumber(parsed)
                }
                // If number starts with 0, it's likely national format - need device locale
                number.startsWith("0") -> null
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get device's country code from locale
     */
    private fun getDeviceCountryCode(): String {
        return try {
            context.resources.configuration.locale.country.takeIf { it.isNotEmpty() } ?: "US"
        } catch (e: Exception) {
            "US"  // Fallback
        }
    }

    /**
     * Find contact display name for a given phone number.
     * Caches results to avoid repeated expensive lookups.
     * Uses negative caching to avoid repeated lookups for non-existent contacts.
     */
    suspend fun findContactName(number: String): String? = withContext(Dispatchers.IO) {
        if (number.isBlank()) return@withContext null

        val normalizedNumber = normalizeNumber(number)

        // Check negative cache (contact doesn't exist)
        val negativeTimeMillis = negativeCache[normalizedNumber]
        if (negativeTimeMillis != null && System.currentTimeMillis() - negativeTimeMillis < NEGATIVE_CACHE_TTL) {
            return@withContext null
        }

        // Check positive cache
        val cached = contactCache[normalizedNumber]
        if (cached != null && System.currentTimeMillis() - cached.cachedAtMillis < CONTACT_CACHE_TTL) {
            return@withContext cached.displayName
        }

        // Perform actual lookup
        val displayName = performPhoneLookup(normalizedNumber) ?: performPhoneLookup(number)

        return@withContext if (displayName != null) {
            // Cache the result
            contactCache[normalizedNumber] = CachedContact(displayName, System.currentTimeMillis())
            negativeCache.remove(normalizedNumber) // Remove from negative cache if found
            displayName
        } else {
            // Add to negative cache
            negativeCache[normalizedNumber] = System.currentTimeMillis()
            null
        }
    }

    /**
     * Perform actual contact lookup using ContactsContract.PhoneLookup
     */
    private fun performPhoneLookup(number: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return@use cursor.getString(0)
                }
                return@use null
            }
        } catch (e: SecurityException) {
            // READ_CONTACTS permission not granted
            null
        } catch (e: Exception) {
            // Other exceptions (query errors, etc.)
            null
        }
    }

    /**
     * Clear all caches - useful when permissions are revoked
     */
    fun clearCache() {
        contactCache.clear()
        negativeCache.clear()
    }

    companion object {
        // 24 hours TTL for positive contact cache
        private const val CONTACT_CACHE_TTL = 24 * 60 * 60 * 1000L

        // 2 hours TTL for negative cache (non-existent contacts)
        private const val NEGATIVE_CACHE_TTL = 2 * 60 * 60 * 1000L
    }
}
