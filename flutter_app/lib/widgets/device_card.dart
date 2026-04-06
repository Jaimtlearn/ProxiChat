import 'package:flutter/material.dart';
import '../models/chat_device.dart';
import '../theme.dart';

class DeviceCard extends StatelessWidget {
  final ChatDevice device;
  final VoidCallback onTap;
  const DeviceCard({super.key, required this.device, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Card(
      color: device.isConnected ? cs.primaryContainer.withOpacity(0.3) : cs.surfaceContainerLow,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(children: [
            _Avatar(name: device.displayName, colorIndex: device.avatarColorIndex, connected: device.isConnected),
            const SizedBox(width: 16),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(device.displayName, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
                const SizedBox(height: 2),
                Row(children: [
                  _StatusDot(state: device.connectionState),
                  const SizedBox(width: 6),
                  Text(_statusLabel, style: Theme.of(context).textTheme.bodySmall?.copyWith(color: cs.onSurfaceVariant)),
                ]),
              ]),
            ),
            Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
              _SignalBars(strength: device.signalStrength),
              if (device.connectionState == ConnectionState_.connecting)
                const Padding(padding: EdgeInsets.only(top: 8), child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))),
            ]),
          ]),
        ),
      ),
    );
  }

  String get _statusLabel {
    switch (device.connectionState) {
      case ConnectionState_.connected: return 'Connected';
      case ConnectionState_.connecting: return 'Connecting...';
      case ConnectionState_.failed: return 'Failed';
      case ConnectionState_.disconnected: return device.signalStrength.name[0].toUpperCase() + device.signalStrength.name.substring(1);
    }
  }
}

class _Avatar extends StatelessWidget {
  final String name;
  final int colorIndex;
  final bool connected;
  const _Avatar({required this.name, required this.colorIndex, required this.connected});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 48, height: 48,
      child: Stack(children: [
        CircleAvatar(
          radius: 24,
          backgroundColor: AppTheme.avatarColor(colorIndex),
          child: Text(name.isNotEmpty ? name[0].toUpperCase() : '?',
              style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 20)),
        ),
        if (connected)
          Positioned(bottom: 0, right: 0,
            child: Container(width: 14, height: 14,
              decoration: BoxDecoration(shape: BoxShape.circle, color: Colors.green, border: Border.all(color: Theme.of(context).colorScheme.surface, width: 2)),
            ),
          ),
      ]),
    );
  }
}

class _StatusDot extends StatelessWidget {
  final ConnectionState_ state;
  const _StatusDot({required this.state});

  @override
  Widget build(BuildContext context) {
    final color = switch (state) {
      ConnectionState_.connected => Colors.green,
      ConnectionState_.connecting => Colors.amber,
      ConnectionState_.failed => Colors.red,
      ConnectionState_.disconnected => Colors.grey.withOpacity(0.4),
    };
    return Container(width: 8, height: 8, decoration: BoxDecoration(shape: BoxShape.circle, color: color));
  }
}

class _SignalBars extends StatelessWidget {
  final SignalStrength strength;
  const _SignalBars({required this.strength});

  @override
  Widget build(BuildContext context) {
    final bars = switch (strength) { SignalStrength.excellent => 4, SignalStrength.good => 3, SignalStrength.fair => 2, SignalStrength.weak => 1 };
    final color = switch (strength) { SignalStrength.excellent => Colors.green, SignalStrength.good => Colors.lightGreen, SignalStrength.fair => Colors.amber, SignalStrength.weak => Colors.orange };
    return Row(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.end,
      children: List.generate(4, (i) => Container(
        width: 4, height: 5.0 + i * 4, margin: const EdgeInsets.symmetric(horizontal: 1),
        decoration: BoxDecoration(borderRadius: BorderRadius.circular(2), color: i < bars ? color : Colors.grey.withOpacity(0.25)),
      )),
    );
  }
}
