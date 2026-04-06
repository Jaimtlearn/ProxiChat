import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:ble_peripheral/ble_peripheral.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';
import 'package:uuid/uuid.dart';

import 'constants.dart';
import '../models/chat_device.dart';
import '../models/chat_message.dart';

/// Unified BLE controller handling both central (scan/connect) and peripheral (advertise/serve) roles.
class BleController extends ChangeNotifier {
  // State
  final Map<String, ChatDevice> _devices = {};
  final Map<String, BluetoothCharacteristic> _writeChars = {}; // GATT client write chars
  final Map<String, BluetoothCharacteristic> _notifyChars = {};
  final Map<String, bool> typingStates = {};
  bool _isScanning = false;
  bool _isAdvertising = false;
  bool _initialized = false;
  String _displayName = 'User';
  Timer? _pruneTimer;

  // Streams for incoming messages
  final _messageController = StreamController<ChatMessage>.broadcast();
  final _ackController = StreamController<Map<String, String>>.broadcast();

  Stream<ChatMessage> get incomingMessages => _messageController.stream;
  Stream<Map<String, String>> get incomingAcks => _ackController.stream;
  List<ChatDevice> get devices => _devices.values.toList()..sort((a, b) => b.rssi.compareTo(a.rssi));
  bool get isScanning => _isScanning;
  bool get isAdvertising => _isAdvertising;

  // ========== INITIALIZATION ==========

  Future<void> initialize(String displayName) async {
    if (_initialized) {
      _displayName = displayName;
      return;
    }
    _initialized = true;
    _displayName = displayName;

    // Initialize peripheral (GATT server + advertiser)
    await _initPeripheral();

    // Start prune timer
    _pruneTimer = Timer.periodic(const Duration(seconds: 5), (_) => _pruneStaleDevices());
  }

  Future<void> _initPeripheral() async {
    try {
      await BlePeripheral.initialize();

      // Set up GATT server callbacks
      BlePeripheral.setWriteRequestCallback(_onWriteRequest);
      BlePeripheral.setReadRequestCallback(_onReadRequest);
      BlePeripheral.setCharacteristicSubscriptionChangeCallback(_onSubscriptionChange);

      // Add our GATT service
      await BlePeripheral.addService(
        BleService(
          uuid: BleConstants.serviceUuidStr,
          primary: true,
          characteristics: [
            BleCharacteristic(
              uuid: BleConstants.writeCharUuidStr,
              properties: [
                CharacteristicProperties.write.index,
                CharacteristicProperties.writeWithoutResponse.index,
              ],
              value: null,
              permissions: [AttributePermissions.writeable.index],
            ),
            BleCharacteristic(
              uuid: BleConstants.notifyCharUuidStr,
              properties: [
                CharacteristicProperties.notify.index,
                CharacteristicProperties.read.index,
              ],
              value: null,
              permissions: [AttributePermissions.readable.index],
            ),
            BleCharacteristic(
              uuid: BleConstants.profileCharUuidStr,
              properties: [CharacteristicProperties.read.index],
              value: Uint8List.fromList(utf8.encode(_displayName)),
              permissions: [AttributePermissions.readable.index],
            ),
          ],
        ),
      );

      debugPrint('[BLE] GATT service added');
    } catch (e) {
      debugPrint('[BLE] Peripheral init error: $e');
    }
  }

  // ========== ADVERTISING ==========

  Future<void> startAdvertising() async {
    if (_isAdvertising) return;
    try {
      await BlePeripheral.startAdvertising(
        services: [BleConstants.serviceUuidStr],
        localName: _displayName,
      );
      _isAdvertising = true;
      debugPrint('[BLE] Advertising STARTED as "$_displayName"');
      notifyListeners();
    } catch (e) {
      debugPrint('[BLE] Advertising error: $e');
    }
  }

  Future<void> stopAdvertising() async {
    try {
      await BlePeripheral.stopAdvertising();
    } catch (_) {}
    _isAdvertising = false;
    notifyListeners();
  }

  // ========== SCANNING ==========

  Future<void> startScanning() async {
    if (_isScanning) return;
    _isScanning = true;
    notifyListeners();

    try {
      // Listen to scan results
      FlutterBluePlus.scanResults.listen((results) {
        for (final r in results) {
          _processScanResult(r);
        }
      });

      // Start scan — withServices filter works on iOS, on Android it's best-effort
      await FlutterBluePlus.startScan(
        withServices: [BleConstants.serviceUuid],
        androidUsesFineLocation: true,
        continuousUpdates: true,
        removeIfGone: Duration(seconds: BleConstants.staleDeviceTimeoutSec),
      );

      debugPrint('[BLE] Scan STARTED');
    } catch (e) {
      debugPrint('[BLE] Scan error: $e');
      _isScanning = false;
      notifyListeners();
    }
  }

  Future<void> stopScanning() async {
    try {
      await FlutterBluePlus.stopScan();
    } catch (_) {}
    _isScanning = false;
    notifyListeners();
  }

  // Start both advertising and scanning
  Future<void> startDiscovery() async {
    await startAdvertising();
    await startScanning();
  }

  Future<void> stopDiscovery() async {
    await stopScanning();
  }

  void _processScanResult(ScanResult result) {
    final id = result.device.remoteId.str;
    final name = result.device.advName.isNotEmpty
        ? result.device.advName
        : result.device.platformName.isNotEmpty
            ? result.device.platformName
            : 'ProxiChat User';

    final existing = _devices[id];
    final smoothedRssi = existing != null
        ? (BleConstants.rssiSmoothing * result.rssi +
                (1 - BleConstants.rssiSmoothing) * existing.rssi)
            .toInt()
        : result.rssi;

    _devices[id] = ChatDevice(
      id: id,
      name: name,
      displayName: existing?.displayName ?? name,
      rssi: smoothedRssi,
      connectionState: existing?.connectionState ?? ConnectionState_.disconnected,
      lastSeen: DateTime.now(),
      avatarColorIndex: id.hashCode.abs() % 8,
      bleDevice: result.device,
      unreadCount: existing?.unreadCount ?? 0,
    );
    notifyListeners();
  }

  // ========== CONNECTION ==========

  Future<bool> connectToDevice(String deviceId) async {
    final device = _devices[deviceId];
    if (device == null || device.bleDevice == null) return false;

    _updateDeviceState(deviceId, ConnectionState_.connecting);
    try {
      await device.bleDevice!.connect(
        timeout: Duration(seconds: BleConstants.connectionTimeoutSec),
        autoConnect: false,
      );

      // Discover services
      final services = await device.bleDevice!.discoverServices();
      final svc = services.firstWhere(
        (s) => s.uuid == BleConstants.serviceUuid,
        orElse: () => throw Exception('ProxiChat service not found'),
      );

      // Find characteristics
      for (final c in svc.characteristics) {
        if (c.uuid == BleConstants.writeCharUuid) {
          _writeChars[deviceId] = c;
        }
        if (c.uuid == BleConstants.notifyCharUuid) {
          _notifyChars[deviceId] = c;
          // Subscribe to notifications (incoming messages from remote device)
          await c.setNotifyValue(true);
          c.onValueReceived.listen((value) {
            _handleIncomingData(deviceId, Uint8List.fromList(value));
          });
        }
      }

      // Request higher MTU
      try {
        await device.bleDevice!.requestMtu(512);
      } catch (_) {}

      _updateDeviceState(deviceId, ConnectionState_.connected);
      debugPrint('[BLE] Connected to $deviceId');

      // Listen for disconnect
      device.bleDevice!.connectionState.listen((state) {
        if (state == BluetoothConnectionState.disconnected) {
          _writeChars.remove(deviceId);
          _notifyChars.remove(deviceId);
          _updateDeviceState(deviceId, ConnectionState_.disconnected);
        }
      });

      return true;
    } catch (e) {
      debugPrint('[BLE] Connection failed: $e');
      _updateDeviceState(deviceId, ConnectionState_.failed);
      return false;
    }
  }

  Future<void> disconnectFromDevice(String deviceId) async {
    final device = _devices[deviceId];
    if (device?.bleDevice != null) {
      try {
        await device!.bleDevice!.disconnect();
      } catch (_) {}
    }
    _writeChars.remove(deviceId);
    _notifyChars.remove(deviceId);
    _updateDeviceState(deviceId, ConnectionState_.disconnected);
  }

  // ========== MESSAGING ==========

  Future<bool> sendTextMessage(String deviceId, String text, String messageId) async {
    final payload = jsonEncode({
      't': 'MSG',
      'i': messageId,
      's': 'local',
      'ts': DateTime.now().millisecondsSinceEpoch,
      'p': {'text': text},
      'e': false,
    });

    return _sendToDevice(deviceId, utf8.encode(payload));
  }

  void sendAck(String deviceId, String messageId, String status) {
    final payload = jsonEncode({
      't': 'ACK',
      'i': const Uuid().v4(),
      's': 'local',
      'ts': DateTime.now().millisecondsSinceEpoch,
      'p': {'messageId': messageId, 'status': status},
      'e': false,
    });
    _sendToDevice(deviceId, utf8.encode(payload));
  }

  void sendTypingIndicator(String deviceId, bool isTyping) {
    final payload = jsonEncode({
      't': 'TYPING',
      'i': const Uuid().v4(),
      's': 'local',
      'ts': DateTime.now().millisecondsSinceEpoch,
      'p': {'isTyping': isTyping},
      'e': false,
    });
    _sendToDevice(deviceId, utf8.encode(payload));
  }

  Future<bool> _sendToDevice(String deviceId, List<int> data) async {
    // Try GATT client path (we connected to them)
    final writeChar = _writeChars[deviceId];
    if (writeChar != null) {
      try {
        await writeChar.write(data, withoutResponse: false);
        return true;
      } catch (e) {
        debugPrint('[BLE] Write failed: $e');
        return false;
      }
    }

    // Try GATT server path (they connected to us)
    try {
      await BlePeripheral.updateCharacteristic(
        characteristicId: BleConstants.notifyCharUuidStr,
        value: Uint8List.fromList(data),
        deviceId: deviceId,
      );
      return true;
    } catch (e) {
      debugPrint('[BLE] Notify failed: $e');
      return false;
    }
  }

  // ========== GATT SERVER CALLBACKS ==========

  void _onWriteRequest(String deviceId, String characteristicId, int offset, Uint8List? value) {
    if (characteristicId.toLowerCase() == BleConstants.writeCharUuidStr.toLowerCase() && value != null) {
      _handleIncomingData(deviceId, value);
    }
  }

  ReadRequestResult? _onReadRequest(String deviceId, String characteristicId, int offset, Uint8List? value) {
    if (characteristicId.toLowerCase() == BleConstants.profileCharUuidStr.toLowerCase()) {
      return ReadRequestResult(
        value: Uint8List.fromList(utf8.encode(_displayName)),
        offset: offset,
      );
    }
    return null;
  }

  void _onSubscriptionChange(String deviceId, String characteristicId, bool isSubscribed) {
    debugPrint('[BLE] Device $deviceId ${isSubscribed ? "subscribed" : "unsubscribed"}');
  }

  // ========== INCOMING MESSAGE HANDLER ==========

  void _handleIncomingData(String senderId, Uint8List data) {
    try {
      final json = jsonDecode(utf8.decode(data)) as Map<String, dynamic>;
      final type = json['t'] as String;

      switch (type) {
        case 'MSG':
          final text = (json['p'] as Map<String, dynamic>)['text'] as String;
          final msg = ChatMessage(
            id: json['i'],
            deviceId: senderId,
            text: text,
            timestamp: DateTime.fromMillisecondsSinceEpoch(json['ts']),
            isOutgoing: false,
            status: MessageStatus.delivered,
          );
          _messageController.add(msg);
          sendAck(senderId, json['i'], 'DELIVERED');
          break;

        case 'ACK':
          final p = json['p'] as Map<String, dynamic>;
          _ackController.add({
            'messageId': p['messageId'] as String,
            'status': p['status'] as String,
          });
          break;

        case 'TYPING':
          final isTyping = (json['p'] as Map<String, dynamic>)['isTyping'] == true;
          typingStates[senderId] = isTyping;
          notifyListeners();
          break;

        case 'PROFILE':
          final name = (json['p'] as Map<String, dynamic>)['displayName'] as String;
          if (_devices.containsKey(senderId)) {
            _devices[senderId]!.displayName = name;
            notifyListeners();
          }
          break;
      }
    } catch (e) {
      debugPrint('[BLE] Failed to parse message: $e');
    }
  }

  // ========== HELPERS ==========

  void _updateDeviceState(String id, ConnectionState_ state) {
    if (_devices.containsKey(id)) {
      _devices[id]!.connectionState = state;
      notifyListeners();
    }
  }

  void _pruneStaleDevices() {
    final cutoff = DateTime.now().subtract(Duration(seconds: BleConstants.staleDeviceTimeoutSec));
    _devices.removeWhere((_, d) =>
        d.connectionState != ConnectionState_.connected && d.lastSeen.isBefore(cutoff));
    notifyListeners();
  }

  ChatDevice? getDevice(String id) => _devices[id];

  void updateDisplayName(String name) {
    _displayName = name;
    if (_isAdvertising) {
      stopAdvertising().then((_) => startAdvertising());
    }
  }

  Future<void> shutdown() async {
    _pruneTimer?.cancel();
    await stopScanning();
    await stopAdvertising();
    _writeChars.clear();
    _notifyChars.clear();
    _initialized = false;
  }

  @override
  void dispose() {
    shutdown();
    _messageController.close();
    _ackController.close();
    super.dispose();
  }
}
