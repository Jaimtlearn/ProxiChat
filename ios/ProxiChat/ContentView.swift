import SwiftUI

struct ContentView: View {
    @EnvironmentObject var bluetooth: BluetoothController
    @EnvironmentObject var settings: UserSettings
    let messageStore: MessageStore

    var body: some View {
        if settings.isOnboardingComplete {
            MainDiscoveryView(messageStore: messageStore)
        } else {
            OnboardingView(viewModel: OnboardingViewModel(settings: settings))
        }
    }
}

/// Wrapper that owns the DiscoveryViewModel via @StateObject (created once, not on every redraw)
struct MainDiscoveryView: View {
    @EnvironmentObject var bluetooth: BluetoothController
    @EnvironmentObject var settings: UserSettings
    let messageStore: MessageStore

    @StateObject private var viewModel = DeferredDiscoveryViewModel()

    var body: some View {
        DiscoveryView(
            viewModel: viewModel.resolve(bluetooth: bluetooth, settings: settings),
            messageStore: messageStore
        )
    }
}

/// Deferred wrapper so @StateObject can be used without constructor parameters
class DeferredDiscoveryViewModel: ObservableObject {
    private var inner: DiscoveryViewModel?

    func resolve(bluetooth: BluetoothController, settings: UserSettings) -> DiscoveryViewModel {
        if let existing = inner { return existing }
        let vm = DiscoveryViewModel(bluetooth: bluetooth, settings: settings)
        inner = vm
        return vm
    }
}
