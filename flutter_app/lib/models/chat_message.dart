import 'package:uuid/uuid.dart';

enum MessageStatus { sending, sent, delivered, read, failed }

class ChatMessage {
  final String id;
  final String deviceId;
  final String text;
  final DateTime timestamp;
  final bool isOutgoing;
  MessageStatus status;

  ChatMessage({
    String? id,
    required this.deviceId,
    required this.text,
    DateTime? timestamp,
    required this.isOutgoing,
    MessageStatus? status,
  })  : id = id ?? const Uuid().v4(),
        timestamp = timestamp ?? DateTime.now(),
        status = status ?? (isOutgoing ? MessageStatus.sending : MessageStatus.delivered);

  Map<String, dynamic> toJson() => {
        'id': id,
        'deviceId': deviceId,
        'text': text,
        'timestamp': timestamp.millisecondsSinceEpoch,
        'isOutgoing': isOutgoing,
        'status': status.name,
      };

  factory ChatMessage.fromJson(Map<String, dynamic> j) => ChatMessage(
        id: j['id'],
        deviceId: j['deviceId'],
        text: j['text'],
        timestamp: DateTime.fromMillisecondsSinceEpoch(j['timestamp']),
        isOutgoing: j['isOutgoing'],
        status: MessageStatus.values.firstWhere((e) => e.name == j['status'],
            orElse: () => MessageStatus.failed),
      );
}
