package com.proxichat.app.data.bluetooth

import java.util.UUID

object BluetoothConstants {
    // Custom service UUID for ProxiChat app identification
    val SERVICE_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")

    // Characteristic for writing messages (client → server)
    val MESSAGE_WRITE_CHAR_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567891")

    // Characteristic for receiving messages via notifications (server → client)
    val MESSAGE_NOTIFY_CHAR_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567892")

    // Characteristic for user profile data (read)
    val PROFILE_CHAR_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567893")

    // Client Characteristic Configuration Descriptor (standard BLE descriptor for enabling notifications)
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Manufacturer ID for advertisement data (custom)
    const val MANUFACTURER_ID = 0xFF01

    // Protocol version
    const val PROTOCOL_VERSION: Byte = 1

    // Maximum message size before chunking (after MTU negotiation)
    const val DEFAULT_MTU = 23
    const val PREFERRED_MTU = 512
    const val CHUNK_HEADER_SIZE = 5 // version(1) + sequence(2) + flags(1) + type(1)

    // Message types
    const val MSG_TYPE_TEXT: Byte = 0x01
    const val MSG_TYPE_ACK: Byte = 0x02
    const val MSG_TYPE_TYPING: Byte = 0x03
    const val MSG_TYPE_PROFILE: Byte = 0x04
    const val MSG_TYPE_KEY_EXCHANGE: Byte = 0x05
    const val MSG_TYPE_DISCONNECT: Byte = 0x06

    // Chunk flags
    const val FLAG_FIRST_CHUNK: Byte = 0x01
    const val FLAG_LAST_CHUNK: Byte = 0x02
    const val FLAG_SINGLE_CHUNK: Byte = 0x03 // FIRST | LAST

    // Timeouts
    const val SCAN_DURATION_MS = 12_000L
    const val SCAN_INTERVAL_MS = 15_000L
    const val CONNECTION_TIMEOUT_MS = 10_000L
    const val RECONNECT_DELAY_MS = 3_000L
    const val MAX_RECONNECT_ATTEMPTS = 5
    const val DEVICE_STALE_TIMEOUT_MS = 30_000L
}
