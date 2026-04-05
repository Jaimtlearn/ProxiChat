import SwiftUI

struct SettingsView: View {
    @ObservedObject var viewModel: SettingsViewModel
    @Environment(\.dismiss) var dismiss

    @State private var editedName = ""
    @State private var showClearConfirmation = false

    var body: some View {
        List {
            // Profile
            Section("Profile") {
                HStack {
                    Text("Display Name")
                    Spacer()
                    TextField("Name", text: $editedName)
                        .multilineTextAlignment(.trailing)
                        .foregroundColor(.secondary)
                        .onSubmit {
                            if !editedName.trimmingCharacters(in: .whitespaces).isEmpty {
                                viewModel.updateDisplayName(editedName.trimmingCharacters(in: .whitespaces))
                            }
                        }
                }
            }

            // Appearance
            Section("Appearance") {
                Picker("Dark Mode", selection: $viewModel.settings.darkMode) {
                    ForEach(DarkModeSetting.allCases, id: \.rawValue) { mode in
                        Text(mode.label).tag(mode.rawValue)
                    }
                }
            }

            // Bluetooth
            Section("Bluetooth") {
                Toggle("Discoverable", isOn: Binding(
                    get: { viewModel.settings.isDiscoverable },
                    set: { viewModel.setDiscoverable($0) }
                ))

                Toggle("Auto-reconnect", isOn: $viewModel.settings.autoReconnect)
            }

            // Security
            Section("Security") {
                Toggle("Message Encryption", isOn: $viewModel.settings.encryptionEnabled)

                if viewModel.settings.encryptionEnabled {
                    Text("Messages are encrypted with AES-256-GCM")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }

            // Data
            Section("Data") {
                Button(role: .destructive) {
                    showClearConfirmation = true
                } label: {
                    Text("Clear Chat History")
                }
            }

            // About
            Section("About") {
                HStack {
                    Text("Version")
                    Spacer()
                    Text("1.0.0")
                        .foregroundColor(.secondary)
                }

                HStack {
                    Text("Platform")
                    Spacer()
                    Text("iOS")
                        .foregroundColor(.secondary)
                }
            }
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Done") { dismiss() }
            }
        }
        .onAppear {
            editedName = viewModel.settings.displayName
        }
        .alert("Clear Chat History", isPresented: $showClearConfirmation) {
            Button("Delete All", role: .destructive) {
                viewModel.clearChatHistory()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This will permanently delete all messages. This action cannot be undone.")
        }
    }
}
