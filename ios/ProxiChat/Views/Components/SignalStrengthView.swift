import SwiftUI

struct SignalStrengthView: View {
    let strength: SignalStrength

    var body: some View {
        HStack(alignment: .bottom, spacing: 2) {
            ForEach(0..<4, id: \.self) { index in
                RoundedRectangle(cornerRadius: 1.5)
                    .fill(index < strength.bars ? barColor : Color.gray.opacity(0.25))
                    .frame(width: 4, height: barHeight(for: index))
            }
        }
        .frame(width: 24, height: 20)
    }

    private func barHeight(for index: Int) -> CGFloat {
        CGFloat(5 + index * 5)
    }

    private var barColor: Color {
        strength.color
    }
}

struct EmptyStateView: View {
    let icon: String
    let title: String
    let subtitle: String
    var animate: Bool = false

    @State private var offset: CGFloat = 0

    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: icon)
                .font(.system(size: 60))
                .foregroundColor(.secondary.opacity(0.4))
                .offset(y: offset)
                .onAppear {
                    if animate {
                        withAnimation(.easeInOut(duration: 2).repeatForever(autoreverses: true)) {
                            offset = -8
                        }
                    }
                }

            VStack(spacing: 8) {
                Text(title)
                    .font(.title3)
                    .fontWeight(.semibold)
                    .foregroundColor(.primary.opacity(0.7))

                Text(subtitle)
                    .font(.subheadline)
                    .foregroundColor(.secondary.opacity(0.6))
                    .multilineTextAlignment(.center)
            }
        }
        .padding(48)
    }
}
