import 'package:flutter_blue_plus/flutter_blue_plus.dart';

enum ConnectionState_ { disconnected, connecting, connected, failed }
enum SignalStrength { excellent, good, fair, weak }

class ChatDevice {
  final String id;
  String name;
  String displayName;
  int rssi;
  ConnectionState_ connectionState;
  DateTime lastSeen;
  int avatarColorIndex;
  BluetoothDevice? bleDevice;
  int unreadCount;

  ChatDevice({
    required this.id,
    this.name = 'Unknown',
    String? displayName,
    this.rssi = -100,
    this.connectionState = ConnectionState_.disconnected,
    DateTime? lastSeen,
    this.avatarColorIndex = 0,
    this.bleDevice,
    this.unreadCount = 0,
  })  : displayName = displayName ?? name,
        lastSeen = lastSeen ?? DateTime.now();

  SignalStrength get signalStrength {
    if (rssi >= -50) return SignalStrength.excellent;
    if (rssi >= -65) return SignalStrength.good;
    if (rssi >= -80) return SignalStrength.fair;
    return SignalStrength.weak;
  }

  bool get isConnected => connectionState == ConnectionState_.connected;
}
