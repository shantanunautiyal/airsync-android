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
    fun createDeviceInfoJson(name: String, ipAddress: String, port: Int, version: String): String {
        return """{"type":"device","data":{"name":"$name","ipAddress":"$ipAddress","port":$port,"version":"$version"}}"""
    }

    /**
     * Creates a single-line JSON string for device info with wallpaper
     */
    fun createDeviceInfoJson(name: String, ipAddress: String, port: Int, version: String , wallpaperBase64: String?): String {
        val wallpaperJson = if (wallpaperBase64 != null) {
            ""","wallpaper":"$wallpaperBase64""""
        } else {
            ""
        }
        return """{"type":"device","data":{"name":"$name","ipAddress":"$ipAddress","port":$port,"version":"$version"$wallpaperJson}}"""
    }

    /**
     * Creates a single-line JSON string for notifications with unique ID
     */
    fun createNotificationJson(id: String, title: String, body: String, app: String, packageName: String): String {
        return """{"type":"notification","data":{"id":"$id","title":"$title","body":"$body","app":"$app","package":"$packageName"}}"""
    }

    /**
     * Creates a single-line JSON string for notifications with actions included.
     * actions: list of objects { name: String, type: "button" | "reply" }
     */
    fun createNotificationJson(
        id: String,
        title: String,
        body: String,
        app: String,
        packageName: String,
        actions: List<Pair<String, String>>
    ): String {
        val actionsJson = if (actions.isNotEmpty()) {
            val items = actions.joinToString(",") { (name, type) ->
                """{"name":"${escape(name)}","type":"${escape(type)}"}"""
            }
            ",\"actions\":[${items}]"
        } else {
            ""
        }
        return """{"type":"notification","data":{"id":"$id","title":"${escape(title)}","body":"${escape(body)}","app":"${escape(app)}","package":"${escape(packageName)}"$actionsJson}}"""
    }

    /**
     * Creates a one-line JSON for Android->Mac notification state updates (e.g., dismissals).
     * If action is not provided, defaults to "dismiss" when dismissed=true.
     */
    fun createNotificationUpdateJson(id: String, dismissed: Boolean = true, action: String? = null): String {
        val safeAction = action ?: if (dismissed) "dismiss" else null
        val actionPart = safeAction?.let { ",\"action\":\"${escape(it)}\"" } ?: ""
        return """{"type":"notificationUpdate","data":{"id":"$id","dismissed":$dismissed$actionPart}}"""
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
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
        isMuted: Boolean,
        albumArt: String?,
        likeStatus: String
    ): String {
        val albumArtJson = if (albumArt != null) ",\"albumArt\":\"$albumArt\"" else ""
        return """{"type":"status","data":{"battery":{"level":$batteryLevel,"isCharging":$isCharging},"isPaired":$isPaired,"music":{"isPlaying":$isPlaying,"title":"$title","artist":"$artist","volume":$volume,"isMuted":$isMuted$albumArtJson,"likeStatus":"$likeStatus"}}}"""
    }

    /**
     * Creates a response JSON for notification dismissal result
     */
    fun createNotificationDismissalResponse(id: String, success: Boolean, message: String = ""): String {
        return """{"type":"dismissalResponse","data":{"id":"$id","success":$success,"message":"$message"}}"""
    }

    /**
     * Creates a response JSON for notification action result
     */
    fun createNotificationActionResponse(id: String, actionName: String, success: Boolean, message: String = ""): String {
        return """{"type":"notificationActionResponse","data":{"id":"$id","action":"${escape(actionName)}","success":$success,"message":"${escape(message)}"}}"""
    }

    /**
     * Creates a response JSON for media control result
     */
    fun createMediaControlResponse(action: String, success: Boolean, message: String = ""): String {
        return """{"type":"mediaControlResponse","data":{"action":"$action","success":$success,"message":"$message"}}"""
    }

    /**
     * Creates a response JSON for volume control result
     */
    fun createVolumeControlResponse(action: String, success: Boolean, message: String = ""): String {
        return """{"type":"volumeControlResponse","data":{"action":"$action","success":$success,"message":"$message"}}"""
    }

    /**
     * Creates a JSON string for app icons
     */
    fun createAppIconsJson(apps: List<com.sameerasw.airsync.domain.model.NotificationApp>, iconMap: Map<String, String>): String {
        val appEntries = apps.joinToString(",") { app ->
            val iconData = iconMap[app.packageName] ?: ""
            """
            "${app.packageName}": {
                "name": "${app.appName}",
                "icon": "$iconData",
                "listening": ${app.isEnabled},
                "systemApp": ${app.isSystemApp}
            }
            """.trimIndent()
        }
        return """{"type":"appIcons","data":{ $appEntries }}"""
    }

    /**
     * Creates a JSON string for clipboard updates
     */
    fun createClipboardUpdateJson(text: String): String {
        // Escape quotes and newlines in the text
        val escapedText = text.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        return """{"type":"clipboardUpdate","data":{"text":"$escapedText"}}"""
    }

    /**
     * Creates a response JSON for app notification toggle result
     */
    fun createToggleAppNotificationResponse(
        packageName: String,
        success: Boolean,
        newState: Boolean,
        message: String = ""
    ): String {
        return """{"type":"toggleAppNotifResponse","data":{"package":"$packageName","success":$success,"newState":$newState,"message":"$message"}}"""
    }

    /**
     * Creates a response JSON for toggleNowPlaying command
     */
    fun createToggleNowPlayingResponse(success: Boolean, newState: Boolean?, message: String = ""): String {
        val statePart = newState?.let { ",\"state\":$it" } ?: ""
        return """{"type":"toggleNowPlayingResponse","data":{"success":$success$statePart,"message":"${escape(message)}"}}"""
    }

    /**
     * Creates a JSON for a mirror request to start or stop screen mirroring.
     */
    fun createMirrorRequestJson(
        action: String, // "start" or "stop"
        mode: String = "device",
        packageName: String = "",
        fps: Int = 30,
        quality: Float = 0.6f,
        maxWidth: Int = 1280
    ): String {
        return """{"type":"mirrorRequest","data":{"action":"$action","mode":"$mode","package":"$packageName","options":{"transport":"websocket","fps":$fps,"quality":$quality,"maxWidth":$maxWidth}}}"""
    }

    /**
     * Creates a JSON to signal that mirroring is starting with specific parameters.
     */
    fun createMirrorStartJson(fps: Int, quality: Float, width: Int, height: Int): String {
        return """{"type":"mirrorStart","data":{"fps":$fps,"quality":$quality,"width":$width,"height":$height}}"""
    }

    /**
     * Creates a JSON for a single mirror frame.
     */
    fun createMirrorFrameJson(frameBase64: String, pts: Long, isConfig: Boolean): String {
        return """{"type":"mirrorFrame","data":{"frame":"$frameBase64","pts":$pts,"isConfig":$isConfig}}"""
    }

    /**
     * Creates a JSON to stop the mirroring session.
     */
    fun createMirrorStopJson(): String {
        return """{"type":"mirrorStop","data":{}}"""
    }
}
