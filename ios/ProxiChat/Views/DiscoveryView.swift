import SwiftUI

struct DiscoveryView: View {
    @ObservedObject var viewModel: DiscoveryViewModel
    @EnvironmentObject var bluetooth: BluetoothController
    @EnvironmentObject var settings: UserSettings
    let messageStore: MessageStore

    @State private var showSettings = false

    var body: some View {
        NavigationStack {
            ZStack {
                if viewModel.devices.isEmpty {
                    EmptyStateView(
                        icon: "antenna.radiowaves.left.and.right",
                        title: "No Devices Found",
                        subtitle: "Make sure Bluetooth is enabled on nearby devices running ProxiChat.",
                        animate: viewModel.isScanning
                    )
                } else {
                    deviceList
                }
            }
            .navigationTitle("ProxiChat")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: { showSettings = true }) {
                        Image(systemName: "gearshape")
                    }
                }

                ToolbarItem(placement: .topBarLeading) {
                    if viewModel.isScanning {
                        HStack(spacing: 6) {
                            ProgressView()
                                .scaleEffect(0.7)
                            Text("Scanning")
                                .font(.caption)
                                .foregroundColor(AppTheme.primary)
                        }
                    }
                }
            }
            .overlay(alignment: .bottomTrailing) {
                scanButton
                    .padding(16)
            }
            .onAppear {
                viewModel.startDiscovery()
            }
            .sheet(isPresented: $showSettings) {
                NavigationStack {
                    SettingsView(viewModel: SettingsViewModel(
                        settings: settings,
                        bluetooth: bluetooth,
                        messageStore: messageStore
                    ))
                }
            }
        }
    }

    private var deviceList: some View {
        ScrollView {
            LazyVStack(spacing: 8) {
                if !viewModel.connectedDevices.isEmpty {
                    sectionHeader("Connected")
                    ForEach(viewModel.connectedDevices) { device in
                        NavigationLink {
                            ChatView(viewModel: ChatViewModel(
                                deviceID: device.id,
                                bluetooth: bluetooth,
                                messageStore: messageStore
                            ))
                        } label: {
                            DeviceCardView(device: device, onTap: {})
                        }
                        .buttonStyle(.plain)
                    }
                }

                if !viewModel.nearbyDevices.isEmpty {
                    sectionHeader(viewModel.connectedDevices.isEmpty ? "Nearby Devices" : "Nearby")
                    ForEach(viewModel.nearbyDevices) { device in
                        DeviceCardView(device: device) {
                            if device.connectionState == .disconnected || device.connectionState == .failed {
                                viewModel.connect(to: device.id)
                            }
                        }
                    }
                }

                // Bottom spacer for FAB
                Color.clear.frame(height: 80)
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
        }
    }

    private var scanButton: some View {
        Button(action: {
            if viewModel.isScanning {
                viewModel.stopDiscovery()
            } else {
                viewModel.startDiscovery()
            }
        }) {
            Image(systemName: viewModel.isScanning
                  ? "antenna.radiowaves.left.and.right"
                  : "arrow.clockwise")
                .font(.title3)
                .foregroundColor(AppTheme.onPrimaryContainer)
                .frame(width: 56, height: 56)
                .background(AppTheme.primaryContainer)
                .clipShape(Circle())
                .shadow(color: .black.opacity(0.15), radius: 8, y: 4)
        }
    }

    private func sectionHeader(_ title: String) -> some View {
        HStack {
            Text(title)
                .font(.subheadline)
                .fontWeight(.semibold)
                .foregroundColor(AppTheme.primary)
            Spacer()
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 4)
    }
}
