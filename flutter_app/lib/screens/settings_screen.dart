import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../bluetooth/ble_controller.dart';
import '../data/message_store.dart';
import '../data/preferences.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final prefs = context.watch<UserPreferences>();
    final ble = context.read<BleController>();
    final store = context.read<MessageStore>();

    return Scaffold(
      appBar: AppBar(title: const Text('Settings', style: TextStyle(fontWeight: FontWeight.bold))),
      body: ListView(children: [
        _Section('Profile'),
        ListTile(
          title: const Text('Display Name'),
          subtitle: Text(prefs.displayName),
          onTap: () => _editName(context, prefs, ble),
        ),

        const Divider(indent: 16, endIndent: 16),
        _Section('Appearance'),
        ListTile(
          title: const Text('Dark Mode'),
          subtitle: Text(switch (prefs.darkMode) { 'on' => 'Always on', 'off' => 'Always off', _ => 'Follow system' }),
          onTap: () => _pickDarkMode(context, prefs),
        ),

        const Divider(indent: 16, endIndent: 16),
        _Section('Bluetooth'),
        SwitchListTile(
          title: const Text('Discoverable'),
          subtitle: const Text('Allow nearby devices to find you'),
          value: prefs.discoverable,
          onChanged: (v) {
            prefs.setDiscoverable(v);
            if (v) ble.startAdvertising(); else ble.stopAdvertising();
          },
        ),

        const Divider(indent: 16, endIndent: 16),
        _Section('Data'),
        ListTile(
          title: Text('Clear Chat History', style: TextStyle(color: Theme.of(context).colorScheme.error)),
          onTap: () => showDialog(
            context: context,
            builder: (_) => AlertDialog(
              title: const Text('Clear Chat History'),
              content: const Text('Delete all messages? This cannot be undone.'),
              actions: [
                TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
                TextButton(
                  onPressed: () { store.deleteAll(); Navigator.pop(context); },
                  child: Text('Delete', style: TextStyle(color: Theme.of(context).colorScheme.error)),
                ),
              ],
            ),
          ),
        ),

        const Divider(indent: 16, endIndent: 16),
        _Section('About'),
        const ListTile(title: Text('Version'), subtitle: Text('1.0.0 (Flutter)')),
      ]),
    );
  }

  void _editName(BuildContext context, UserPreferences prefs, BleController ble) {
    final ctrl = TextEditingController(text: prefs.displayName);
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Display Name'),
        content: TextField(controller: ctrl, maxLength: 20, autofocus: true),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          TextButton(
            onPressed: () {
              final name = ctrl.text.trim();
              if (name.isNotEmpty) {
                prefs.setDisplayName(name);
                ble.updateDisplayName(name);
              }
              Navigator.pop(context);
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }

  void _pickDarkMode(BuildContext context, UserPreferences prefs) {
    showDialog(
      context: context,
      builder: (_) => SimpleDialog(
        title: const Text('Dark Mode'),
        children: [
          for (final entry in {'system': 'Follow System', 'on': 'Always On', 'off': 'Always Off'}.entries)
            RadioListTile<String>(
              title: Text(entry.value),
              value: entry.key,
              groupValue: prefs.darkMode,
              onChanged: (v) { prefs.setDarkMode(v!); Navigator.pop(context); },
            ),
        ],
      ),
    );
  }
}

class _Section extends StatelessWidget {
  final String title;
  const _Section(this.title);
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 24, 16, 8),
      child: Text(title, style: Theme.of(context).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600, color: Theme.of(context).colorScheme.primary)),
    );
  }
}
