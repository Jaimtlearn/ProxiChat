import Foundation
import SwiftUI

class OnboardingViewModel: ObservableObject {
    @Published var currentStep = 0
    @Published var displayName = ""
    @Published var permissionsGranted = false

    private let settings: UserSettings

    init(settings: UserSettings) {
        self.settings = settings
    }

    var canContinue: Bool {
        switch currentStep {
        case 0: return true
        case 1: return permissionsGranted
        case 2: return !displayName.trimmingCharacters(in: .whitespaces).isEmpty
        default: return false
        }
    }

    func nextStep() {
        if currentStep < 2 {
            withAnimation(.easeInOut(duration: 0.3)) {
                currentStep += 1
            }
        }
    }

    func completeOnboarding() {
        let name = displayName.trimmingCharacters(in: .whitespaces)
        settings.displayName = name.isEmpty ? "User" : name
        settings.isOnboardingComplete = true
    }
}
