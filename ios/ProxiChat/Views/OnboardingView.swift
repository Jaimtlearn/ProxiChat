import SwiftUI

struct OnboardingView: View {
    @ObservedObject var viewModel: OnboardingViewModel

    var body: some View {
        VStack(spacing: 0) {
            Spacer().frame(height: 48)

            // Step indicators
            HStack(spacing: 8) {
                ForEach(0..<3, id: \.self) { index in
                    Capsule()
                        .fill(index <= viewModel.currentStep ? AppTheme.primary : Color.secondary.opacity(0.2))
                        .frame(width: index == viewModel.currentStep ? 32 : 8, height: 8)
                        .animation(.spring(response: 0.3), value: viewModel.currentStep)
                }
            }

            Spacer()

            // Step content
            Group {
                switch viewModel.currentStep {
                case 0: welcomeStep
                case 1: permissionStep
                case 2: profileStep
                default: EmptyView()
                }
            }
            .transition(.asymmetric(
                insertion: .move(edge: .trailing).combined(with: .opacity),
                removal: .move(edge: .leading).combined(with: .opacity)
            ))
            .animation(.easeInOut(duration: 0.3), value: viewModel.currentStep)

            Spacer()

            // Action button
            Button(action: actionHandler) {
                Text(buttonTitle)
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(
                        RoundedRectangle(cornerRadius: 16)
                            .fill(buttonEnabled ? AppTheme.primary : .gray.opacity(0.3))
                    )
            }
            .disabled(!buttonEnabled)
            .padding(.horizontal, 24)
            .padding(.bottom, 32)
        }
        .padding(.horizontal, 24)
    }

    // MARK: - Steps

    private var welcomeStep: some View {
        VStack(spacing: 48) {
            PulseAnimation()
                .frame(width: 120, height: 120)

            VStack(spacing: 16) {
                Text("Welcome to ProxiChat")
                    .font(.title)
                    .fontWeight(.bold)
                    .multilineTextAlignment(.center)

                Text("Connect and chat with people nearby using Bluetooth — no internet required.")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }

            HStack(spacing: 32) {
                FeatureChip(icon: "antenna.radiowaves.left.and.right", label: "Bluetooth")
                FeatureChip(icon: "bubble.left.and.bubble.right", label: "P2P Chat")
                FeatureChip(icon: "lock.shield", label: "Encrypted")
            }
        }
    }

    private var permissionStep: some View {
        VStack(spacing: 32) {
            Image(systemName: "lock.shield")
                .font(.system(size: 60))
                .foregroundColor(AppTheme.primary.opacity(0.5))

            VStack(spacing: 16) {
                Text("Permissions Needed")
                    .font(.title2)
                    .fontWeight(.bold)
                    .multilineTextAlignment(.center)

                Text("ProxiChat needs Bluetooth access to discover and connect with nearby devices. Your data never leaves your device.")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }

            // On iOS, BLE permissions are requested on first use.
            // We just inform the user here.
            Text("Bluetooth permission will be requested when you start scanning.")
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.top, 8)
        }
    }

    private var profileStep: some View {
        VStack(spacing: 32) {
            Image(systemName: "person.circle.fill")
                .font(.system(size: 60))
                .foregroundColor(AppTheme.primary)

            VStack(spacing: 16) {
                Text("Set Up Your Profile")
                    .font(.title2)
                    .fontWeight(.bold)

                Text("Choose a display name so nearby users can recognize you.")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }

            VStack(alignment: .trailing, spacing: 4) {
                TextField("Enter your name", text: $viewModel.displayName)
                    .textFieldStyle(.roundedBorder)
                    .textInputAutocapitalization(.words)
                    .submitLabel(.done)
                    .onChange(of: viewModel.displayName) { newValue in
                        if newValue.count > 20 {
                            viewModel.displayName = String(newValue.prefix(20))
                        }
                    }

                Text("\(viewModel.displayName.count)/20")
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            .padding(.horizontal, 32)
        }
    }

    // MARK: - Button

    private var buttonTitle: String {
        switch viewModel.currentStep {
        case 0: return "Get Started"
        case 1: return "Continue"
        case 2: return "Start Chatting"
        default: return ""
        }
    }

    private var buttonEnabled: Bool {
        switch viewModel.currentStep {
        case 2: return !viewModel.displayName.trimmingCharacters(in: .whitespaces).isEmpty
        default: return true
        }
    }

    private func actionHandler() {
        if viewModel.currentStep < 2 {
            viewModel.nextStep()
        } else {
            viewModel.completeOnboarding()
        }
    }
}

// MARK: - Supporting Views

private struct FeatureChip: View {
    let icon: String
    let label: String

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundColor(AppTheme.onPrimaryContainer)
                .frame(width: 48, height: 48)
                .background(AppTheme.primaryContainer)
                .clipShape(Circle())

            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }
}

private struct PulseAnimation: View {
    @State private var animate = false

    var body: some View {
        ZStack {
            ForEach(0..<3, id: \.self) { index in
                Circle()
                    .stroke(AppTheme.primary.opacity(0.3), lineWidth: 2)
                    .scaleEffect(animate ? 1.0 + CGFloat(index) * 0.15 : 0.5)
                    .opacity(animate ? 0 : 0.4)
                    .animation(
                        .easeOut(duration: 1.8)
                        .repeatForever(autoreverses: false)
                        .delay(Double(index) * 0.6),
                        value: animate
                    )
            }

            Circle()
                .fill(AppTheme.primary)
                .frame(width: 20, height: 20)
        }
        .onAppear { animate = true }
    }
}
