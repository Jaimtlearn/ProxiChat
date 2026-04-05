import SwiftUI

@main
struct ProxiChatApp: App {
    @StateObject private var bluetooth = BluetoothController()
    @StateObject private var settings = UserSettings()
    private let messageStore = MessageStore()

    var body: some Scene {
        WindowGroup {
            ContentView(messageStore: messageStore)
                .environmentObject(bluetooth)
                .environmentObject(settings)
                .preferredColorScheme(settings.colorScheme)
                .onAppear {
                    if settings.isOnboardingComplete {
                        bluetooth.initialize(displayName: settings.displayName)
                    }
                }
        }
    }
}
