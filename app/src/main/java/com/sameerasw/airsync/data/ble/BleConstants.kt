package com.sameerasw.airsync.data.ble

import java.util.UUID

object BleConstants {
    private const val UUID_BASE = "-7461-4694-8146-2162624a682c"

    // Services
    val SERVICE_SYSTEM = UUID.fromString("a1520001$UUID_BASE")
    val SERVICE_NOTIFICATIONS = UUID.fromString("a1520002$UUID_BASE")
    val SERVICE_MEDIA = UUID.fromString("a1520003$UUID_BASE")
    val SERVICE_CLIPBOARD = UUID.fromString("a1520004$UUID_BASE")

    // System Characteristics
    val CHAR_PROTOCOL_VERSION = UUID.fromString("a1520101$UUID_BASE")
    val CHAR_AUTH_TOKEN = UUID.fromString("a1520102$UUID_BASE")
    val CHAR_AUTH_RESULT = UUID.fromString("a1520103$UUID_BASE")
    val CHAR_BATTERY_LEVEL = UUID.fromString("a1520104$UUID_BASE")
    val CHAR_MAC_BATTERY = UUID.fromString("a1520105$UUID_BASE")
    val CHAR_SYSTEM_STATE = UUID.fromString("a1520106$UUID_BASE")
    val CHAR_MAC_CONTROL = UUID.fromString("a1520107$UUID_BASE")
    val CHAR_DEVICE_NAME = UUID.fromString("a1520108$UUID_BASE")

    // Notification Characteristics
    val CHAR_NOTIFICATION_DATA = UUID.fromString("a1520201$UUID_BASE")
    val CHAR_NOTIFICATION_ACTION = UUID.fromString("a1520202$UUID_BASE")
    val CHAR_NOTIFICATION_DISMISS = UUID.fromString("a1520203$UUID_BASE")
    val CHAR_NOTIFICATION_DISMISS_NOTIFY = UUID.fromString("a1520204$UUID_BASE")

    // Media Characteristics
    val CHAR_MEDIA_STATE = UUID.fromString("a1520301$UUID_BASE")
    val CHAR_MEDIA_CONTROL = UUID.fromString("a1520302$UUID_BASE")
    val CHAR_MAC_MEDIA_STATE = UUID.fromString("a1520303$UUID_BASE")

    // Clipboard Characteristics
    val CHAR_CLIPBOARD_DATA_NOTIFY = UUID.fromString("a1520401$UUID_BASE")
    val CHAR_CLIPBOARD_DATA_WRITE = UUID.fromString("a1520402$UUID_BASE")

    // Protocol Constants
    const val PROTOCOL_VERSION = 1
    const val AUTH_SUCCESS: Byte = 0x01
    const val AUTH_FAILED: Byte = 0x00

    // Chunking
    const val MAX_MTU = 512
    const val CHUNK_HEADER_SIZE = 4 // [index: UInt16][total: UInt16]

    // Delimiter for compact strings
    const val DELIMITER = "\u001F"
}
