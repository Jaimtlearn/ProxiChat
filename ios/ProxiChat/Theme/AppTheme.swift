import SwiftUI

enum AppTheme {
    // Primary palette
    static let primary = Color(red: 0.40, green: 0.31, blue: 0.64) // #6750A4
    static let onPrimary = Color.white
    static let primaryContainer = Color(red: 0.92, green: 0.87, blue: 1.0) // #EADDFF
    static let onPrimaryContainer = Color(red: 0.13, green: 0.0, blue: 0.36)

    // Surface
    static let surface = Color(UIColor.systemBackground)
    static let surfaceContainer = Color(UIColor.secondarySystemBackground)
    static let surfaceContainerHigh = Color(UIColor.tertiarySystemBackground)
    static let onSurface = Color(UIColor.label)
    static let onSurfaceVariant = Color(UIColor.secondaryLabel)

    // Error
    static let error = Color(red: 0.70, green: 0.15, blue: 0.12)
    static let errorContainer = Color(red: 0.98, green: 0.87, blue: 0.86)

    // Chat bubbles
    static let sentBubble = Color(red: 0.92, green: 0.87, blue: 1.0)
    static let receivedBubble = Color(UIColor.tertiarySystemBackground)

    // Avatar colors
    static let avatarColors: [Color] = [
        Color(red: 0.40, green: 0.31, blue: 0.64), // Purple
        Color(red: 0.0, green: 0.38, blue: 0.64),   // Blue
        Color(red: 0.0, green: 0.43, blue: 0.11),   // Green
        Color(red: 0.57, green: 0.30, blue: 0.15),  // Brown
        Color(red: 0.73, green: 0.10, blue: 0.10),  // Red
        Color(red: 0.49, green: 0.32, blue: 0.38),  // Rose
        Color(red: 0.0, green: 0.42, blue: 0.38),   // Teal
        Color(red: 0.43, green: 0.33, blue: 0.0)    // Amber
    ]

    static func avatarColor(for index: Int) -> Color {
        avatarColors[abs(index) % avatarColors.count]
    }
}
