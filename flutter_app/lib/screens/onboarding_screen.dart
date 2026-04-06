import 'dart:io';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:provider/provider.dart';
import '../data/preferences.dart';

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});
  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  int _step = 0;
  final _nameController = TextEditingController();
  bool _permissionsGranted = false;

  Future<void> _requestPermissions() async {
    final statuses = await [
      Permission.bluetoothScan,
      Permission.bluetoothAdvertise,
      Permission.bluetoothConnect,
      Permission.location,
      if (Platform.isAndroid) Permission.notification,
    ].request();
    final allGranted = statuses.values.every((s) => s.isGranted || s.isLimited);
    setState(() {
      _permissionsGranted = allGranted;
      if (allGranted) _step = 2;
    });
  }

  void _finish() {
    final name = _nameController.text.trim();
    if (name.isEmpty) return;
    final prefs = context.read<UserPreferences>();
    prefs.setDisplayName(name);
    prefs.setOnboardingComplete(true);
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(children: [
            const SizedBox(height: 32),
            // Step dots
            Row(mainAxisAlignment: MainAxisAlignment.center, children: List.generate(3, (i) =>
              AnimatedContainer(
                duration: const Duration(milliseconds: 300),
                width: i == _step ? 32 : 8, height: 8,
                margin: const EdgeInsets.symmetric(horizontal: 4),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(4),
                  color: i <= _step ? cs.primary : cs.outlineVariant,
                ),
              ),
            )),
            const Spacer(),
            // Step content
            if (_step == 0) ...[
              Icon(Icons.bluetooth, size: 80, color: cs.primary),
              const SizedBox(height: 32),
              Text('Welcome to ProxiChat', style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.bold), textAlign: TextAlign.center),
              const SizedBox(height: 16),
              Text('Chat with nearby devices over Bluetooth.\nNo internet required.', style: Theme.of(context).textTheme.bodyLarge?.copyWith(color: cs.onSurfaceVariant), textAlign: TextAlign.center),
            ] else if (_step == 1) ...[
              Icon(Icons.security, size: 80, color: cs.primary.withOpacity(0.6)),
              const SizedBox(height: 32),
              Text('Permissions Needed', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold), textAlign: TextAlign.center),
              const SizedBox(height: 16),
              Text('ProxiChat needs Bluetooth and Location access to discover nearby devices.', style: Theme.of(context).textTheme.bodyLarge?.copyWith(color: cs.onSurfaceVariant), textAlign: TextAlign.center),
            ] else ...[
              Icon(Icons.person, size: 80, color: cs.primary),
              const SizedBox(height: 32),
              Text('Your Display Name', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold), textAlign: TextAlign.center),
              const SizedBox(height: 24),
              TextField(
                controller: _nameController,
                textCapitalization: TextCapitalization.words,
                maxLength: 20,
                decoration: InputDecoration(
                  hintText: 'Enter your name',
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(16)),
                ),
                onSubmitted: (_) => _finish(),
              ),
            ],
            const Spacer(),
            SizedBox(
              width: double.infinity, height: 56,
              child: FilledButton(
                onPressed: _step == 0
                    ? () => setState(() => _step = 1)
                    : _step == 1
                        ? _requestPermissions
                        : _finish,
                style: FilledButton.styleFrom(shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16))),
                child: Text(
                  _step == 0 ? 'Get Started' : _step == 1 ? 'Grant Permissions' : 'Start Chatting',
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
                ),
              ),
            ),
            const SizedBox(height: 32),
          ]),
        ),
      ),
    );
  }
}
