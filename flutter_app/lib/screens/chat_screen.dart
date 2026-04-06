import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../bluetooth/ble_controller.dart';
import '../data/message_store.dart';
import '../models/chat_device.dart';
import '../models/chat_message.dart';
import '../widgets/chat_bubble.dart';
import '../widgets/empty_state.dart';
import '../widgets/message_input.dart';

class ChatScreen extends StatefulWidget {
  final String deviceId;
  const ChatScreen({super.key, required this.deviceId});
  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final _inputController = TextEditingController();
  final _scrollController = ScrollController();
  List<ChatMessage> _messages = [];
  late final StreamSubscription _msgSub;
  late final StreamSubscription _ackSub;

  @override
  void initState() {
    super.initState();
    _loadMessages();

    final ble = context.read<BleController>();
    final store = context.read<MessageStore>();

    _msgSub = ble.incomingMessages.where((m) => m.deviceId == widget.deviceId).listen((msg) async {
      await store.addMessage(msg);
      _loadMessages();
    });

    _ackSub = ble.incomingAcks.listen((ack) async {
      final status = MessageStatus.values.firstWhere(
          (e) => e.name == ack['status']!.toLowerCase(),
          orElse: () => MessageStatus.delivered);
      await store.updateStatus(widget.deviceId, ack['messageId']!, status);
      _loadMessages();
    });
  }

  Future<void> _loadMessages() async {
    final store = context.read<MessageStore>();
    final msgs = await store.getMessages(widget.deviceId);
    if (mounted) {
      setState(() => _messages = msgs);
      _scrollToBottom();
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeOut,
        );
      }
    });
  }

  Future<void> _send() async {
    final text = _inputController.text.trim();
    if (text.isEmpty) return;
    _inputController.clear();

    final msg = ChatMessage(deviceId: widget.deviceId, text: text, isOutgoing: true);
    final store = context.read<MessageStore>();
    final ble = context.read<BleController>();

    await store.addMessage(msg);
    _loadMessages();

    final sent = await ble.sendTextMessage(widget.deviceId, text, msg.id);
    await store.updateStatus(widget.deviceId, msg.id, sent ? MessageStatus.sent : MessageStatus.failed);
    _loadMessages();
  }

  @override
  void dispose() {
    _msgSub.cancel();
    _ackSub.cancel();
    _inputController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final ble = context.watch<BleController>();
    final device = ble.getDevice(widget.deviceId);
    final isConnected = device?.connectionState == ConnectionState_.connected;
    final isTyping = ble.typingStates[widget.deviceId] == true;

    return Scaffold(
      appBar: AppBar(
        title: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(device?.displayName ?? 'Chat', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
          Text(
            isTyping ? 'typing...' : isConnected ? 'Connected' : 'Disconnected',
            style: TextStyle(
              fontSize: 12,
              color: isTyping ? Theme.of(context).colorScheme.primary : Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
        ]),
      ),
      body: Column(children: [
        // Connection lost banner
        if (!isConnected)
          Container(
            color: Theme.of(context).colorScheme.errorContainer,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(children: [
              Text('Connection lost', style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onErrorContainer)),
              const Spacer(),
              TextButton.icon(
                icon: const Icon(Icons.refresh, size: 14),
                label: const Text('Reconnect', style: TextStyle(fontSize: 12)),
                onPressed: () => ble.connectToDevice(widget.deviceId),
              ),
            ]),
          ),

        // Messages
        Expanded(
          child: _messages.isEmpty
              ? const EmptyState(icon: Icons.chat_bubble_outline, title: 'No Messages Yet', subtitle: 'Send a message to start chatting.')
              : ListView.builder(
                  controller: _scrollController,
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  itemCount: _messages.length + (isTyping ? 1 : 0),
                  itemBuilder: (_, i) {
                    if (i == _messages.length && isTyping) {
                      return const _TypingIndicator();
                    }
                    return ChatBubble(message: _messages[i], onRetry: () => _retryMessage(_messages[i]));
                  },
                ),
        ),

        // Input
        MessageInput(controller: _inputController, enabled: isConnected, onSend: _send),
      ]),
    );
  }

  Future<void> _retryMessage(ChatMessage msg) async {
    if (msg.status != MessageStatus.failed) return;
    final store = context.read<MessageStore>();
    final ble = context.read<BleController>();
    await store.updateStatus(widget.deviceId, msg.id, MessageStatus.sending);
    _loadMessages();
    final sent = await ble.sendTextMessage(widget.deviceId, msg.text, msg.id);
    await store.updateStatus(widget.deviceId, msg.id, sent ? MessageStatus.sent : MessageStatus.failed);
    _loadMessages();
  }
}

class _TypingIndicator extends StatelessWidget {
  const _TypingIndicator();
  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.centerLeft,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surfaceContainerHigh,
          borderRadius: const BorderRadius.only(
            topLeft: Radius.circular(20), topRight: Radius.circular(20),
            bottomLeft: Radius.circular(6), bottomRight: Radius.circular(20),
          ),
        ),
        child: Row(mainAxisSize: MainAxisSize.min, children: List.generate(3, (i) =>
          Padding(padding: const EdgeInsets.symmetric(horizontal: 2),
            child: _Dot(delay: i * 200)),
        )),
      ),
    );
  }
}

class _Dot extends StatefulWidget {
  final int delay;
  const _Dot({required this.delay});
  @override
  State<_Dot> createState() => _DotState();
}

class _DotState extends State<_Dot> with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;
  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(vsync: this, duration: const Duration(milliseconds: 600))
      ..repeat(reverse: true);
    Future.delayed(Duration(milliseconds: widget.delay), () { if (mounted) _ctrl.forward(); });
  }
  @override
  void dispose() { _ctrl.dispose(); super.dispose(); }
  @override
  Widget build(BuildContext context) {
    return FadeTransition(
      opacity: Tween(begin: 0.3, end: 1.0).animate(_ctrl),
      child: Container(width: 8, height: 8, decoration: BoxDecoration(shape: BoxShape.circle, color: Theme.of(context).colorScheme.onSurfaceVariant)),
    );
  }
}
