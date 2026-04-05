import SwiftUI

struct ContentView: View {
    @EnvironmentObject var bluetooth: BluetoothController
    @EnvironmentObject var settings: UserSettings
    let messageStore: MessageStore

    var body: some View {
        if settings.isOnboardingComplete {
            DiscoveryView(
                viewModel: DiscoveryViewModel(bluetooth: bluetooth, settings: settings),
                messageStore: messageStore
            )
        } else {
            OnboardingView(viewModel: OnboardingViewModel(settings: settings))
                .onChange(of: settings.isOnboardingComplete) { isComplete in
                    if isComplete {
                        bluetooth.initialize(displayName: settings.displayName)
                    }
                }
        }
    }
}
