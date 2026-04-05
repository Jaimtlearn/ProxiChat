import SwiftUI

struct MessageInputView: View {
    @Binding var text: String
    let isEnabled: Bool
    let onSend: () -> Void
    let onTypingChange: (Bool) -> Void

    @State private var typingTimer: Timer?

    var body: some View {
        HStack(alignment: .bottom, spacing: 8) {
            TextField("Type a message...", text: $text)
                .textFieldStyle(.plain)
                .padding(.horizontal, 18)
                .padding(.vertical, 12)
                .background(
                    RoundedRectangle(cornerRadius: 24)
                        .fill(AppTheme.surfaceContainerHigh)
                )
                .disabled(!isEnabled)
                .onChange(of: text) { newValue in
                    handleTyping(newValue)
                }
                .onSubmit {
                    if !text.trimmingCharacters(in: .whitespaces).isEmpty {
                        onSend()
                    }
                }

            if !text.trimmingCharacters(in: .whitespaces).isEmpty {
                Button(action: onSend) {
                    Image(systemName: "arrow.up")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                        .background(
                            Circle().fill(isEnabled ? AppTheme.primary : .gray)
                        )
                }
                .disabled(!isEnabled)
                .transition(.scale.combined(with: .opacity))
                .animation(.spring(response: 0.3), value: text.isEmpty)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(AppTheme.surface)
    }

    private func handleTyping(_ value: String) {
        typingTimer?.invalidate()
        if !value.isEmpty {
            onTypingChange(true)
            typingTimer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: false) { _ in
                onTypingChange(false)
            }
        } else {
            onTypingChange(false)
        }
    }
}
