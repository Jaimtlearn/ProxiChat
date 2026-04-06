import 'package:flutter/material.dart';
import '../models/chat_message.dart';

class ChatBubble extends StatelessWidget {
  final ChatMessage message;
  final VoidCallback? onRetry;
  const ChatBubble({super.key, required this.message, this.onRetry});

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final isOut = message.isOutgoing;
    final bubbleColor = isOut ? cs.primaryContainer : cs.surfaceContainerHigh;
    final textColor = isOut ? cs.onPrimaryContainer : cs.onSurface;
    final time = '${message.timestamp.hour.toString().padLeft(2, '0')}:${message.timestamp.minute.toString().padLeft(2, '0')}';

    return Align(
      alignment: isOut ? Alignment.centerRight : Alignment.centerLeft,
      child: GestureDetector(
        onTap: message.status == MessageStatus.failed ? onRetry : null,
        child: Container(
          constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.78),
          margin: const EdgeInsets.symmetric(vertical: 2),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          decoration: BoxDecoration(
            color: bubbleColor,
            borderRadius: BorderRadius.only(
              topLeft: const Radius.circular(20), topRight: const Radius.circular(20),
              bottomLeft: Radius.circular(isOut ? 20 : 6), bottomRight: Radius.circular(isOut ? 6 : 20),
            ),
          ),
          child: Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
            Text(message.text, style: TextStyle(color: textColor, fontSize: 16)),
            const SizedBox(height: 4),
            Row(mainAxisSize: MainAxisSize.min, children: [
              Text(time, style: TextStyle(fontSize: 11, color: textColor.withOpacity(0.6))),
              if (isOut) ...[
                const SizedBox(width: 4),
                _StatusIcon(status: message.status, color: textColor),
              ],
            ]),
          ]),
        ),
      ),
    );
  }
}

class _StatusIcon extends StatelessWidget {
  final MessageStatus status;
  final Color color;
  const _StatusIcon({required this.status, required this.color});

  @override
  Widget build(BuildContext context) {
    switch (status) {
      case MessageStatus.sending:
        return SizedBox(width: 12, height: 12, child: CircularProgressIndicator(strokeWidth: 1.5, color: color.withOpacity(0.5)));
      case MessageStatus.sent:
        return Icon(Icons.check, size: 14, color: color.withOpacity(0.6));
      case MessageStatus.delivered:
        return Icon(Icons.done_all, size: 14, color: color.withOpacity(0.6));
      case MessageStatus.read:
        return Icon(Icons.done_all, size: 14, color: Theme.of(context).colorScheme.primary);
      case MessageStatus.failed:
        return Icon(Icons.error_outline, size: 14, color: Theme.of(context).colorScheme.error);
    }
  }
}
