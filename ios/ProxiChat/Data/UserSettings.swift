import Foundation
import SwiftUI

/// UserDefaults-backed settings store.
class UserSettings: ObservableObject {

    @AppStorage("display_name") var displayName: String = ""
    @AppStorage("onboarding_complete") var isOnboardingComplete: Bool = false
    @AppStorage("dark_mode") var darkMode: String = "system"
    @AppStorage("discoverable") var isDiscoverable: Bool = true
    @AppStorage("auto_reconnect") var autoReconnect: Bool = true
    @AppStorage("encryption_enabled") var encryptionEnabled: Bool = false

    var colorScheme: ColorScheme? {
        switch darkMode {
        case "on": return .dark
        case "off": return .light
        default: return nil
        }
    }
}
