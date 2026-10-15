package com.sameerasw.airsync.utils

import android.util.Log
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages asset caching for WebSocket data exchange.
 * Tracks content hashes to avoid redundant transmission of unchanged assets
 * (app icons, album art, contact photos, etc.).
 *
 * Protocol:
 * 1. Android computes SHA-256 hash for each asset (e.g. app icon base64)
 * 2. Android sends an "appIconManifest" with { packageName: hash } for all apps
 * 3. Mac compares with its local cache and responds with "requestIcons" listing only missing/changed packages
 * 4. Android sends "appIconsDelta" with only the requested icons
 *
 * This avoids re-sending hundreds of unchanged icons on every reconnection.
 */
object AssetCacheManager {
    private const val TAG = "AssetCacheManager"

    // Local cache: maps asset key (e.g., "icon:com.example.app") to its SHA-256 hash
    private val localHashCache = ConcurrentHashMap<String, String>()

    // Cache of raw base64 data for quick re-send without re-extraction
    private val dataCache = ConcurrentHashMap<String, String>()

    // Maximum number of cached items to prevent unbounded memory growth
    private const val MAX_CACHE_SIZE = 500

    /**
     * Compute SHA-256 hash of content, cache both hash and data.
     * Returns the hex hash string.
     */
    fun computeAndCache(key: String, data: String): String {
        val hash = sha256(data)
        localHashCache[key] = hash
        dataCache[key] = data

        // Evict oldest entries if cache exceeds limit
        if (dataCache.size > MAX_CACHE_SIZE) {
            evictOldest(dataCache.size - MAX_CACHE_SIZE)
        }

        return hash
    }

    /**
     * Get the cached hash for a given key, or null if not cached.
     */
    fun getCachedHash(key: String): String? = localHashCache[key]

    /**
     * Get the cached data for a given key, or null if not cached.
     */
    fun getCachedData(key: String): String? = dataCache[key]

    /**
     * Check if data has changed since last cache.
     * Returns true if either not cached or hash differs.
     */
    fun hasChanged(key: String, newData: String): Boolean {
        val newHash = sha256(newData)
        val oldHash = localHashCache[key]
        return oldHash != newHash
    }

    /**
     * Build a manifest of asset hashes for a given prefix.
     * E.g., buildManifest("icon") returns all "icon:xxx" -> hash entries.
     */
    fun buildManifest(prefix: String): Map<String, String> {
        return localHashCache
            .filter { it.key.startsWith("$prefix:") }
            .mapKeys { it.key.removePrefix("$prefix:") }
    }

    /**
     * Given a set of keys the remote side needs, return their cached data.
     * Keys should be the raw package names (without prefix).
     */
    fun getDataForKeys(prefix: String, keys: Set<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (key in keys) {
            val fullKey = "$prefix:$key"
            dataCache[fullKey]?.let { result[key] = it }
        }
        return result
    }

    /**
     * Clear all cached data (e.g., on disconnect).
     */
    fun clearAll() {
        localHashCache.clear()
        dataCache.clear()
        Log.d(TAG, "Asset cache cleared")
    }

    /**
     * Clear cache entries for a specific prefix.
     */
    fun clearPrefix(prefix: String) {
        val keysToRemove = localHashCache.keys.filter { it.startsWith("$prefix:") }
        keysToRemove.forEach {
            localHashCache.remove(it)
            dataCache.remove(it)
        }
        Log.d(TAG, "Cleared ${keysToRemove.size} entries with prefix '$prefix'")
    }

    /**
     * Get cache statistics for debugging.
     */
    fun getStats(): String {
        val prefixCounts = localHashCache.keys
            .groupBy { it.substringBefore(":") }
            .mapValues { it.value.size }
        return "AssetCache: ${localHashCache.size} hashes, ${dataCache.size} data entries. " +
                "By prefix: $prefixCounts"
    }

    private fun sha256(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(16) // Use first 16 hex chars (64 bits) for efficiency
    }

    private fun evictOldest(count: Int) {
        val keys = dataCache.keys().toList().take(count)
        keys.forEach {
            dataCache.remove(it)
            localHashCache.remove(it)
        }
        Log.d(TAG, "Evicted $count oldest cache entries")
    }
}
