package com.proxichat.app.domain.model

data class ChatDevice(
    val address: String,
    val name: String,
    val displayName: String = name,
    val rssi: Int = 0,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val lastSeen: Long = System.currentTimeMillis(),
    val avatarColorIndex: Int = 0,
    val isPaired: Boolean = false,
    val unreadCount: Int = 0,
    val lastMessage: String? = null,
    val lastMessageTime: Long? = null
) {
    val signalStrength: SignalStrength
        get() = when {
            rssi >= -50 -> SignalStrength.EXCELLENT
            rssi >= -65 -> SignalStrength.GOOD
            rssi >= -80 -> SignalStrength.FAIR
            else -> SignalStrength.WEAK
        }

    val isConnected: Boolean
        get() = connectionState == ConnectionState.CONNECTED
}

enum class SignalStrength(val label: String, val bars: Int) {
    EXCELLENT("Excellent", 4),
    GOOD("Good", 3),
    FAIR("Fair", 2),
    WEAK("Weak", 1)
}
