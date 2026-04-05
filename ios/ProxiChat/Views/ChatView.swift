import SwiftUI

struct ChatView: View {
    @StateObject var viewModel: ChatViewModel

    var body: some View {
        VStack(spacing: 0) {
            // Connection banner
            if viewModel.connectionState == .disconnected || viewModel.connectionState == .failed {
                connectionBanner
            }

            // Messages
            if viewModel.messages.isEmpty {
                Spacer()
                EmptyStateView(
                    icon: "bubble.left.and.bubble.right",
                    title: "No Messages Yet",
                    subtitle: "Send a message to start the conversation."
                )
                Spacer()
            } else {
                messageList
            }

            // Input
            MessageInputView(
                text: $viewModel.inputText,
                isEnabled: viewModel.connectionState == .connected,
                onSend: { viewModel.sendMessage() },
                onTypingChange: { viewModel.sendTypingIndicator($0) }
            )
        }
        .navigationTitle(viewModel.device?.displayName ?? "Chat")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                VStack(spacing: 2) {
                    Text(viewModel.device?.displayName ?? "Chat")
                        .font(.headline)

                    HStack(spacing: 4) {
                        ConnectionDot(state: viewModel.connectionState, size: 6)
                        Text(subtitleText)
                            .font(.caption)
                            .foregroundColor(viewModel.isRemoteTyping ? AppTheme.primary : .secondary)
                    }
                }
            }
        }
    }

    private var subtitleText: String {
        if viewModel.isRemoteTyping { return "typing..." }
        switch viewModel.connectionState {
        case .connected: return "Connected"
        case .connecting: return "Connecting..."
        default: return "Disconnected"
        }
    }

    private var connectionBanner: some View {
        HStack {
            Text("Connection lost")
                .font(.caption)

            Spacer()

            Button(action: { viewModel.reconnect() }) {
                HStack(spacing: 4) {
                    Image(systemName: "arrow.clockwise")
                        .font(.caption)
                    Text("Reconnect")
                        .font(.caption)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(AppTheme.errorContainer)
        .transition(.move(edge: .top).combined(with: .opacity))
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 4) {
                    let grouped = groupByDate(viewModel.messages)

                    ForEach(Array(grouped.keys.sorted()), id: \.self) { dateKey in
                        if let msgs = grouped[dateKey] {
                            DateSeparatorView(date: msgs[0].timestamp)

                            ForEach(msgs) { message in
                                ChatBubbleView(
                                    message: message,
                                    onRetry: { viewModel.retryMessage(message.id) }
                                )
                                .id(message.id)
                            }
                        }
                    }

                    if viewModel.isRemoteTyping {
                        HStack {
                            TypingIndicatorView()
                            Spacer()
                        }
                        .id("typing")
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
            }
            .onChange(of: viewModel.messages.count) { _ in
                if let last = viewModel.messages.last {
                    withAnimation(.easeOut(duration: 0.2)) {
                        proxy.scrollTo(last.id, anchor: .bottom)
                    }
                }
            }
        }
    }

    private func groupByDate(_ messages: [ChatMessage]) -> [String: [ChatMessage]] {
        let cal = Calendar.current
        return Dictionary(grouping: messages) { msg in
            let comps = cal.dateComponents([.year, .month, .day], from: msg.timestamp)
            return "\(comps.year ?? 0)-\(comps.month ?? 0)-\(comps.day ?? 0)"
        }
    }
}
