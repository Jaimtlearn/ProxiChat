import SwiftUI

struct DeviceCardView: View {
    let device: ChatDevice
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 16) {
                // Avatar
                AvatarView(
                    name: device.displayName,
                    colorIndex: device.avatarColorIndex,
                    isConnected: device.isConnected,
                    size: 48
                )

                // Info
                VStack(alignment: .leading, spacing: 2) {
                    Text(device.displayName)
                        .font(.headline)
                        .foregroundColor(.primary)
                        .lineLimit(1)

                    HStack(spacing: 6) {
                        ConnectionDot(state: device.connectionState, size: 8)

                        Text(statusLabel)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }

                    if let msg = device.lastMessage {
                        Text(msg)
                            .font(.caption)
                            .foregroundColor(.secondary.opacity(0.7))
                            .lineLimit(1)
                    }
                }

                Spacer()

                // Right side
                VStack(alignment: .trailing, spacing: 8) {
                    SignalStrengthView(strength: device.signalStrength)

                    if device.connectionState == .connecting {
                        ProgressView()
                            .scaleEffect(0.7)
                    }

                    if device.unreadCount > 0 {
                        Text("\(min(device.unreadCount, 99))")
                            .font(.caption2).bold()
                            .foregroundColor(.white)
                            .frame(width: 22, height: 22)
                            .background(AppTheme.primary)
                            .clipShape(Circle())
                    }
                }
            }
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(device.isConnected
                          ? AppTheme.primaryContainer.opacity(0.3)
                          : AppTheme.surfaceContainer)
            )
        }
        .buttonStyle(.plain)
    }

    private var statusLabel: String {
        switch device.connectionState {
        case .connected: return "Connected"
        case .connecting: return "Connecting..."
        case .disconnecting: return "Disconnecting..."
        case .failed: return "Connection failed"
        case .disconnected: return device.signalStrength.label
        }
    }
}

struct AvatarView: View {
    let name: String
    let colorIndex: Int
    let isConnected: Bool
    var size: CGFloat = 48

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Circle()
                .fill(AppTheme.avatarColor(for: colorIndex))
                .frame(width: size, height: size)
                .overlay(
                    Text(String(name.prefix(1)).uppercased())
                        .font(size > 40 ? .title2 : .caption)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                )

            if isConnected {
                Circle()
                    .fill(Color.white)
                    .frame(width: 14, height: 14)
                    .overlay(
                        Circle()
                            .fill(Color.green)
                            .frame(width: 10, height: 10)
                    )
            }
        }
    }
}

struct ConnectionDot: View {
    let state: ConnectionState
    let size: CGFloat

    var body: some View {
        Circle()
            .fill(dotColor)
            .frame(width: size, height: size)
            .opacity(state == .connecting ? 0.6 : 1.0)
            .animation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true),
                       value: state == .connecting)
    }

    private var dotColor: Color {
        switch state {
        case .connected: return .green
        case .connecting, .disconnecting: return .yellow
        case .failed: return .red
        case .disconnected: return .gray.opacity(0.4)
        }
    }
}
