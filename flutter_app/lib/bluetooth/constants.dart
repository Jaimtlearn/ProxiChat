import 'package:flutter_blue_plus/flutter_blue_plus.dart';

class BleConstants {
  static const String serviceUuidStr = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';
  static const String writeCharUuidStr = 'a1b2c3d4-e5f6-7890-abcd-ef1234567891';
  static const String notifyCharUuidStr = 'a1b2c3d4-e5f6-7890-abcd-ef1234567892';
  static const String profileCharUuidStr = 'a1b2c3d4-e5f6-7890-abcd-ef1234567893';

  static final Guid serviceUuid = Guid(serviceUuidStr);
  static final Guid writeCharUuid = Guid(writeCharUuidStr);
  static final Guid notifyCharUuid = Guid(notifyCharUuidStr);
  static final Guid profileCharUuid = Guid(profileCharUuidStr);

  static const int scanTimeoutSec = 30;
  static const int connectionTimeoutSec = 10;
  static const int staleDeviceTimeoutSec = 30;
  static const double rssiSmoothing = 0.7; // 70% new, 30% old
}
