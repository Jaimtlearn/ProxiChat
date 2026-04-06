import 'dart:convert';
import 'dart:io';
import 'package:path_provider/path_provider.dart';
import '../models/chat_message.dart';

class MessageStore {
  final Map<String, List<ChatMessage>> _cache = {};
  Directory? _dir;

  Future<Directory> get _baseDir async {
    if (_dir != null) return _dir!;
    final appDir = await getApplicationDocumentsDirectory();
    _dir = Directory('${appDir.path}/messages');
    if (!_dir!.existsSync()) _dir!.createSync(recursive: true);
    return _dir!;
  }

  Future<List<ChatMessage>> getMessages(String deviceId) async {
    if (_cache.containsKey(deviceId)) return _cache[deviceId]!;
    final messages = await _loadFromDisk(deviceId);
    _cache[deviceId] = messages;
    return messages;
  }

  Future<void> addMessage(ChatMessage msg) async {
    final msgs = await getMessages(msg.deviceId);
    msgs.add(msg);
    await _saveToDisk(msg.deviceId, msgs);
  }

  Future<void> updateStatus(String deviceId, String messageId, MessageStatus status) async {
    final msgs = await getMessages(deviceId);
    final idx = msgs.indexWhere((m) => m.id == messageId);
    if (idx >= 0) {
      msgs[idx].status = status;
      await _saveToDisk(deviceId, msgs);
    }
  }

  Future<void> deleteMessages(String deviceId) async {
    _cache.remove(deviceId);
    final dir = await _baseDir;
    final file = File('${dir.path}/${_safeName(deviceId)}.json');
    if (file.existsSync()) file.deleteSync();
  }

  Future<void> deleteAll() async {
    _cache.clear();
    final dir = await _baseDir;
    if (dir.existsSync()) {
      dir.deleteSync(recursive: true);
      dir.createSync(recursive: true);
    }
  }

  Future<List<ChatMessage>> _loadFromDisk(String deviceId) async {
    final dir = await _baseDir;
    final file = File('${dir.path}/${_safeName(deviceId)}.json');
    if (!file.existsSync()) return [];
    try {
      final json = jsonDecode(file.readAsStringSync()) as List;
      return json.map((e) => ChatMessage.fromJson(e)).toList();
    } catch (_) {
      return [];
    }
  }

  Future<void> _saveToDisk(String deviceId, List<ChatMessage> msgs) async {
    final dir = await _baseDir;
    final file = File('${dir.path}/${_safeName(deviceId)}.json');
    file.writeAsStringSync(jsonEncode(msgs.map((m) => m.toJson()).toList()));
  }

  String _safeName(String id) => id.replaceAll(RegExp(r'[^a-zA-Z0-9]'), '_');
}
