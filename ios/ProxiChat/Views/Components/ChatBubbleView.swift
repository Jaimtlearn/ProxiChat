import SwiftUI

// Custom bubble shape that works on iOS 16 (UnevenRoundedRectangle is iOS 17+)
struct BubbleShape: Shape {
    let isOutgoing: Bool

    func path(in rect: CGRect) -> Path {
        let tl: CGFloat = 20, tr: CGFloat = 20
        let bl: CGFloat = isOutgoing ? 20 : 6
        let br: CGFloat = isOutgoing ? 6 : 20

        var path = Path()
        path.move(to: CGPoint(x: rect.minX + tl, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX - tr, y: rect.minY))
        path.addArc(center: CGPoint(x: rect.maxX - tr, y: rect.minY + tr),
                    radius: tr, startAngle: .degrees(-90), endAngle: .degrees(0), clockwise: false)
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - br))
        path.addArc(center: CGPoint(x: rect.maxX - br, y: rect.maxY - br),
                    radius: br, startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false)
        path.addLine(to: CGPoint(x: rect.minX + bl, y: rect.maxY))
        path.addArc(center: CGPoint(x: rect.minX + bl, y: rect.maxY - bl),
                    radius: bl, startAngle: .degrees(90), endAngle: .degrees(180), clockwise: false)
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY + tl))
        path.addArc(center: CGPoint(x: rect.minX + tl, y: rect.minY + tl),
                    radius: tl, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
        path.closeSubpath()
        return path
    }
}

struct ChatBubbleView: View {
    let message: ChatMessage
    var onRetry: (() -> Void)? = nil

    var body: some View {
        HStack {
            if message.isOutgoing { Spacer(minLength: 60) }

            VStack(alignment: message.isOutgoing ? .trailing : .leading, spacing: 0) {
                Text(message.text)
                    .font(.body)
                    .foregroundColor(Color(UIColor.label))

                HStack(spacing: 4) {
                    if message.isEncrypted {
                        Image(systemName: "lock.fill")
                            .font(.system(size: 9))
                            .foregroundColor(.secondary.opacity(0.5))
                    }

                    Text(timeString)
                        .font(.caption2)
                        .foregroundColor(.secondary.opacity(0.6))

                    if message.isOutgoing {
                        statusIcon
                    }
                }
                .padding(.top, 4)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(
                BubbleShape(isOutgoing: message.isOutgoing)
                    .fill(message.isOutgoing ? AppTheme.sentBubble : AppTheme.receivedBubble)
            )
            .onTapGesture {
                if message.status == .failed {
                    onRetry?()
                }
            }

            if !message.isOutgoing { Spacer(minLength: 60) }
        }
    }

    @ViewBuilder
    private var statusIcon: some View {
        switch message.status {
        case .sending:
            ProgressView()
                .scaleEffect(0.5)
                .frame(width: 14, height: 14)
        case .sent:
            Image(systemName: "checkmark")
                .font(.system(size: 10))
                .foregroundColor(.secondary.opacity(0.6))
        case .delivered:
            Image(systemName: "checkmark")
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(.secondary.opacity(0.6))
                .overlay(
                    Image(systemName: "checkmark")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.secondary.opacity(0.6))
                        .offset(x: 5)
                )
        case .read:
            Image(systemName: "checkmark")
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(AppTheme.primary)
                .overlay(
                    Image(systemName: "checkmark")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(AppTheme.primary)
                        .offset(x: 5)
                )
        case .failed:
            Image(systemName: "exclamationmark.circle")
                .font(.system(size: 12))
                .foregroundColor(.red)
        }
    }

    private var timeString: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: message.timestamp)
    }
}

struct DateSeparatorView: View {
    let date: Date

    var body: some View {
        Text(dateString)
            .font(.caption)
            .fontWeight(.medium)
            .foregroundColor(.secondary.opacity(0.7))
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
            .background(
                Capsule()
                    .fill(AppTheme.surfaceContainerHigh.opacity(0.7))
            )
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
    }

    private var dateString: String {
        let cal = Calendar.current
        if cal.isDateInToday(date) { return "Today" }
        if cal.isDateInYesterday(date) { return "Yesterday" }
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d, yyyy"
        return formatter.string(from: date)
    }
}

struct TypingIndicatorView: View {
    @State private var dotOpacities: [Double] = [0.3, 0.3, 0.3]

    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<3, id: \.self) { index in
                Circle()
                    .fill(Color.secondary)
                    .frame(width: 8, height: 8)
                    .opacity(dotOpacities[index])
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(
            BubbleShape(isOutgoing: false)
                .fill(AppTheme.receivedBubble)
        )
        .onAppear { animate() }
    }

    private func animate() {
        for i in 0..<3 {
            withAnimation(.easeInOut(duration: 0.6).repeatForever().delay(Double(i) * 0.2)) {
                dotOpacities[i] = 1.0
            }
        }
    }
}
