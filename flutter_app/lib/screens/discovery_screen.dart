import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../bluetooth/ble_controller.dart';
import '../models/chat_device.dart';
import '../widgets/device_card.dart';
import '../widgets/empty_state.dart';
import 'chat_screen.dart';
import 'settings_screen.dart';

class DiscoveryScreen extends StatelessWidget {
  const DiscoveryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final ble = context.watch<BleController>();
    final devices = ble.devices;
    final connected = devices.where((d) => d.isConnected).toList();
    final nearby = devices.where((d) => !d.isConnected).toList();

    return Scaffold(
      appBar: AppBar(
        title: Row(children: [
          const Text('ProxiChat', style: TextStyle(fontWeight: FontWeight.bold)),
          if (ble.isScanning) ...[
            const SizedBox(width: 12),
            const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
          ],
        ]),
        centerTitle: false,
        actions: [
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const SettingsScreen())),
          ),
        ],
      ),
      body: devices.isEmpty
          ? const EmptyState(
              icon: Icons.bluetooth_searching,
              title: 'No Devices Found',
              subtitle: 'Make sure Bluetooth is on and nearby devices are running ProxiChat.',
            )
          : ListView(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              children: [
                if (connected.isNotEmpty) ...[
                  _SectionHeader('Connected'),
                  ...connected.map((d) => Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: DeviceCard(device: d, onTap: () => _openChat(context, d)),
                  )),
                ],
                if (nearby.isNotEmpty) ...[
                  _SectionHeader(connected.isEmpty ? 'Nearby Devices' : 'Nearby'),
                  ...nearby.map((d) => Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: DeviceCard(
                      device: d,
                      onTap: () {
                        if (d.connectionState == ConnectionState_.disconnected ||
                            d.connectionState == ConnectionState_.failed) {
                          ble.connectToDevice(d.id);
                        } else if (d.isConnected) {
                          _openChat(context, d);
                        }
                      },
                    ),
                  )),
                ],
                const SizedBox(height: 80), // space for FAB
              ],
            ),
      floatingActionButton: FloatingActionButton(
        onPressed: () {
          if (ble.isScanning) {
            ble.stopDiscovery();
          } else {
            ble.startDiscovery();
          }
        },
        child: Icon(ble.isScanning ? Icons.bluetooth_searching : Icons.refresh),
      ),
    );
  }

  void _openChat(BuildContext context, ChatDevice device) {
    Navigator.push(context, MaterialPageRoute(builder: (_) => ChatScreen(deviceId: device.id)));
  }
}

class _SectionHeader extends StatelessWidget {
  final String title;
  const _SectionHeader(this.title);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 4),
      child: Text(title,
          style: Theme.of(context).textTheme.titleSmall?.copyWith(
                fontWeight: FontWeight.w600,
                color: Theme.of(context).colorScheme.primary,
              )),
    );
  }
}
