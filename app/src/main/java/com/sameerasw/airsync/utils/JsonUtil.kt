package com.sameerasw.airsync.utils

object JsonUtil {
    /**
     * Ensures JSON string is a single line by removing all newlines and extra whitespace
     */
    fun toSingleLine(json: String): String {
        return json.replace(Regex("\\s*\\n\\s*"), " ")
                  .replace(Regex("\\s+"), " ")
                  .trim()
    }

    /**
     * Creates a single-line JSON string for device info
     */
    fun createDeviceInfoJson(name: String, ipAddress: String, port: Int): String {
        return """{"type":"device","data":{"name":"$name","ipAddress":"$ipAddress","port":$port}}"""
    }

    /**
     * Creates a single-line JSON string for notifications with unique ID
     */
    fun createNotificationJson(id: String, title: String, body: String, app: String, packageName: String): String {
        return """{"type":"notification","data":{"id":"$id","title":"$title","body":"$body","app":"$app","packageName":"$packageName"}}"""
    }

    /**
     * Creates a single-line JSON string for device status with media playing state
     */
    fun createDeviceStatusJson(
        batteryLevel: Int,
        isCharging: Boolean,
        isPaired: Boolean,
        isPlaying: Boolean,
        title: String,
        artist: String,
        volume: Int,
        isMuted: Boolean
    ): String {
        return """{"type":"status","data":{"battery":{"level":$batteryLevel,"isCharging":$isCharging},"isPaired":$isPaired,"music":{"isPlaying":$isPlaying,"title":"$title","artist":"$artist","volume":$volume,"isMuted":$isMuted}}}"""
    }

    /**
     * Creates a response JSON for notification dismissal result
     */
    fun createNotificationDismissalResponse(id: String, success: Boolean, message: String = ""): String {
        return """{"type":"dismissalResponse","data":{"id":"$id","success":$success,"message":"$message"}}"""
    }

    /**
     * Creates a response JSON for media control result
     */
    fun createMediaControlResponse(action: String, success: Boolean, message: String = ""): String {
        return """{"type":"mediaControlResponse","data":{"action":"$action","success":$success,"message":"$message"}}"""
    }
}
