import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:ble_peripheral/ble_peripheral.dart' as peripheral;
import 'package:flutter/foundation.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';
import 'package:uuid/uuid.dart';

import 'constants.dart';
import '../models/chat_device.dart';
import '../models/chat_message.dart';

/// Unified BLE controller: central (flutter_blue_plus) + peripheral (ble_peripheral).
class BleController extends ChangeNotifier {
  final Map<String, ChatDevice> _devices = {};
  final Map<String, BluetoothCharacteristic> _writeChars = {};
  final Map<String, bool> typingStates = {};
  bool _isScanning = false;
  bool _isAdvertising = false;
  bool _initialized = false;
  String _displayName = 'User';
  Timer? _pruneTimer;
  StreamSubscription? _scanSub;

  final _messageController = StreamController<ChatMessage>.broadcast();
  final _ackController = StreamController<Map<String, String>>.broadcast();

  Stream<ChatMessage> get incomingMessages => _messageController.stream;
  Stream<Map<String, String>> get incomingAcks => _ackController.stream;
  List<ChatDevice> get devices =>
      _devices.values.toList()..sort((a, b) => b.rssi.compareTo(a.rssi));
  bool get isScanning => _isScanning;
  bool get isAdvertising => _isAdvertising;

  // ==================== INIT ====================

  Future<void> initialize(String displayName) async {
    if (_initialized) {
      _displayName = displayName;
      return;
    }
    _initialized = true;
    _displayName = displayName;

    try {
      await peripheral.BlePeripheral.initialize();

      // Listen for incoming write requests (messages from other devices)
      peripheral.BlePeripheral.setWriteRequestCallback(
        (String deviceId, String charId, int offset, Uint8List? value) {
          if (value != null &&
              charId.toLowerCase() == BleConstants.writeCharUuidStr.toLowerCase()) {
            _handleIncomingData(deviceId, value);
          }
          return peripheral.WriteRequestResult(status: 0);
        },
      );

      // Listen for read requests on profile characteristic
      peripheral.BlePeripheral.setReadRequestCallback(
        (String deviceId, String charId, int offset, Uint8List? value) {
          if (charId.toLowerCase() == BleConstants.profileCharUuidStr.toLowerCase()) {
            return peripheral.ReadRequestResult(
              value: Uint8List.fromList(utf8.encode(_displayName)),
              offset: 0,
            );
          }
          return peripheral.ReadRequestResult(value: Uint8List(0), offset: 0);
        },
      );

      // Add our GATT service
      await peripheral.BlePeripheral.addService(
        peripheral.BleService(
          uuid: BleConstants.serviceUuidStr,
          primary: true,
          characteristics: [
            peripheral.BleCharacteristic(
              uuid: BleConstants.writeCharUuidStr,
              properties: [
                peripheral.CharacteristicProperties.write.index,
                peripheral.CharacteristicProperties.writeWithoutResponse.index,
              ],
              value: null,
              permissions: [peripheral.AttributePermissions.writeable.index],
            ),
            peripheral.BleCharacteristic(
              uuid: BleConstants.notifyCharUuidStr,
              properties: [
                peripheral.CharacteristicProperties.notify.index,
                peripheral.CharacteristicProperties.read.index,
              ],
              value: null,
              permissions: [peripheral.AttributePermissions.readable.index],
            ),
            peripheral.BleCharacteristic(
              uuid: BleConstants.profileCharUuidStr,
              properties: [peripheral.CharacteristicProperties.read.index],
              value: Uint8List.fromList(utf8.encode(_displayName)),
              permissions: [peripheral.AttributePermissions.readable.index],
            ),
          ],
        ),
      );

      debugPrint('[BLE] GATT service added OK');
    } catch (e) {
      debugPrint('[BLE] Peripheral init error: $e');
    }

    _pruneTimer =
        Timer.periodic(const Duration(seconds: 5), (_) => _pruneStaleDevices());
  }

  // ==================== ADVERTISING ====================

  Future<void> startAdvertising() async {
    if (_isAdvertising) return;
    try {
      await peripheral.BlePeripheral.startAdvertising(
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
      await peripheral.BlePeripheral.stopAdvertising();
    } catch (_) {}
    _isAdvertising = false;
    notifyListeners();
  }

  // ==================== SCANNING ====================

  Future<void> startScanning() async {
    if (_isScanning) return;
    _isScanning = true;
    notifyListeners();

    try {
      _scanSub?.cancel();
      _scanSub = FlutterBluePlus.onScanResults.listen((results) {
        for (final r in results) {
          // Filter: only show devices advertising our service UUID
          final hasOurUuid = r.advertisementData.serviceUuids
              .any((u) => u.toString().toLowerCase() == BleConstants.serviceUuidStr.toLowerCase());
          if (hasOurUuid) {
            _processScanResult(r);
          }
        }
      });

      // On Android: scan WITHOUT UUID filter (hardware filters are broken on many devices)
      // On iOS: use UUID filter (CoreBluetooth handles it correctly)
      final isAndroid = defaultTargetPlatform == TargetPlatform.android;
      await FlutterBluePlus.startScan(
        withServices: isAndroid ? [] : [BleConstants.serviceUuid],
        androidUsesFineLocation: true,
        continuousUpdates: true,
        removeIfGone: const Duration(seconds: 30),
      );
      debugPrint('[BLE] Scan STARTED (android filter bypass: $isAndroid)');
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
    _scanSub?.cancel();
    _isScanning = false;
    notifyListeners();
  }

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
    final rssi = existing != null
        ? (BleConstants.rssiSmoothing * result.rssi +
                (1 - BleConstants.rssiSmoothing) * existing.rssi)
            .toInt()
        : result.rssi;

    _devices[id] = ChatDevice(
      id: id,
      name: name,
      displayName: existing?.displayName ?? name,
      rssi: rssi,
      connectionState: existing?.connectionState ?? ConnectionState_.disconnected,
      lastSeen: DateTime.now(),
      avatarColorIndex: id.hashCode.abs() % 8,
      bleDevice: result.device,
      unreadCount: existing?.unreadCount ?? 0,
    );
    notifyListeners();
  }

  // ==================== CONNECTION ====================

  Future<bool> connectToDevice(String deviceId) async {
    final device = _devices[deviceId];
    if (device?.bleDevice == null) return false;

    _updateState(deviceId, ConnectionState_.connecting);
    try {
      await device!.bleDevice!.connect(
        timeout: const Duration(seconds: 10),
        autoConnect: false,
      );

      final services = await device.bleDevice!.discoverServices();
      final svc = services.firstWhere(
        (s) => s.uuid == BleConstants.serviceUuid,
        orElse: () => throw Exception('Service not found'),
      );

      for (final c in svc.characteristics) {
        if (c.uuid == BleConstants.writeCharUuid) {
          _writeChars[deviceId] = c;
        }
        if (c.uuid == BleConstants.notifyCharUuid) {
          await c.setNotifyValue(true);
          c.onValueReceived.listen((value) {
            _handleIncomingData(deviceId, Uint8List.fromList(value));
          });
        }
      }

      try { await device.bleDevice!.requestMtu(512); } catch (_) {}

      _updateState(deviceId, ConnectionState_.connected);
      debugPrint('[BLE] Connected to $deviceId');

      // Watch for disconnection
      device.bleDevice!.connectionState.listen((state) {
        if (state == BluetoothConnectionState.disconnected) {
          _writeChars.remove(deviceId);
          _updateState(deviceId, ConnectionState_.disconnected);
        }
      });

      return true;
    } catch (e) {
      debugPrint('[BLE] Connect failed: $e');
      _updateState(deviceId, ConnectionState_.failed);
      return false;
    }
  }

  Future<void> disconnectFromDevice(String deviceId) async {
    try {
      await _devices[deviceId]?.bleDevice?.disconnect();
    } catch (_) {}
    _writeChars.remove(deviceId);
    _updateState(deviceId, ConnectionState_.disconnected);
  }

  // ==================== MESSAGING ====================

  Future<bool> sendTextMessage(String deviceId, String text, String msgId) async {
    final json = jsonEncode({
      't': 'MSG', 'i': msgId, 's': 'local',
      'ts': DateTime.now().millisecondsSinceEpoch,
      'p': {'text': text}, 'e': false,
    });
    return _sendToDevice(deviceId, utf8.encode(json));
  }

  void sendAck(String deviceId, String msgId, String status) {
    final json = jsonEncode({
      't': 'ACK', 'i': const Uuid().v4(), 's': 'local',
      'ts': DateTime.now().millisecondsSinceEpoch,
      'p': {'messageId': msgId, 'status': status}, 'e': false,
    });
    _sendToDevice(deviceId, utf8.encode(json));
  }

  void sendTypingIndicator(String deviceId, bool isTyping) {
    final json = jsonEncode({
      't': 'TYPING', 'i': const Uuid().v4(), 's': 'local',
      'ts': DateTime.now().millisecondsSinceEpoch,
      'p': {'isTyping': isTyping}, 'e': false,
    });
    _sendToDevice(deviceId, utf8.encode(json));
  }

  Future<bool> _sendToDevice(String deviceId, List<int> data) async {
    // Try GATT client path first
    final writeChar = _writeChars[deviceId];
    if (writeChar != null) {
      try {
        await writeChar.write(data, withoutResponse: false);
        return true;
      } catch (e) {
        debugPrint('[BLE] Client write failed: $e');
      }
    }

    // Try GATT server path (notify subscribed device)
    try {
      await peripheral.BlePeripheral.updateCharacteristic(
        characteristicId: BleConstants.notifyCharUuidStr,
        value: Uint8List.fromList(data),
        deviceId: deviceId,
      );
      return true;
    } catch (e) {
      debugPrint('[BLE] Server notify failed: $e');
    }

    return false;
  }

  // ==================== INCOMING DATA ====================

  void _handleIncomingData(String senderId, Uint8List data) {
    try {
      final json = jsonDecode(utf8.decode(data)) as Map<String, dynamic>;
      final type = json['t'] as String;
      final payload = json['p'] as Map<String, dynamic>? ?? {};

      switch (type) {
        case 'MSG':
          _messageController.add(ChatMessage(
            id: json['i'], deviceId: senderId,
            text: payload['text'] as String,
            timestamp: DateTime.fromMillisecondsSinceEpoch(json['ts']),
            isOutgoing: false, status: MessageStatus.delivered,
          ));
          sendAck(senderId, json['i'], 'DELIVERED');

        case 'ACK':
          _ackController.add({
            'messageId': payload['messageId'] as String,
            'status': payload['status'] as String,
          });

        case 'TYPING':
          typingStates[senderId] = payload['isTyping'] == true;
          notifyListeners();

        case 'PROFILE':
          if (_devices.containsKey(senderId)) {
            _devices[senderId]!.displayName = payload['displayName'] as String;
            notifyListeners();
          }
      }
    } catch (e) {
      debugPrint('[BLE] Parse error: $e');
    }
  }

  // ==================== HELPERS ====================

  void _updateState(String id, ConnectionState_ state) {
    if (_devices.containsKey(id)) {
      _devices[id]!.connectionState = state;
      notifyListeners();
    }
  }

  void _pruneStaleDevices() {
    final cutoff = DateTime.now().subtract(
        const Duration(seconds: BleConstants.staleDeviceTimeoutSec));
    _devices.removeWhere(
        (_, d) => !d.isConnected && d.lastSeen.isBefore(cutoff));
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
    _scanSub?.cancel();
    await stopScanning();
    await stopAdvertising();
    _writeChars.clear();
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
