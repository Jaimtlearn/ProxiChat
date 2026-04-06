import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';
import 'package:provider/provider.dart';
import 'bluetooth/ble_controller.dart';
import 'data/message_store.dart';
import 'data/preferences.dart';
import 'screens/discovery_screen.dart';
import 'screens/onboarding_screen.dart';
import 'theme.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final prefs = UserPreferences();
  await prefs.load();

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider.value(value: prefs),
        ChangeNotifierProvider(create: (_) => BleController()),
        Provider(create: (_) => MessageStore()),
      ],
      child: const ProxiChatApp(),
    ),
  );
}

class ProxiChatApp extends StatelessWidget {
  const ProxiChatApp({super.key});

  @override
  Widget build(BuildContext context) {
    final prefs = context.watch<UserPreferences>();

    return MaterialApp(
      title: 'ProxiChat',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      themeMode: prefs.themeMode,
      home: prefs.onboardingComplete ? const _HomeScreen() : const OnboardingScreen(),
    );
  }
}

/// Initializes BLE after onboarding is complete and shows DiscoveryScreen
class _HomeScreen extends StatefulWidget {
  const _HomeScreen();
  @override
  State<_HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<_HomeScreen> {
  bool _initialized = false;

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    // On Android, ensure Bluetooth adapter is on and Location is enabled
    if (defaultTargetPlatform == TargetPlatform.android) {
      await FlutterBluePlus.turnOn();
    }

    final ble = context.read<BleController>();
    final prefs = context.read<UserPreferences>();
    await ble.initialize(prefs.displayName);
    await ble.startDiscovery();
    if (mounted) setState(() => _initialized = true);
  }

  @override
  Widget build(BuildContext context) {
    if (!_initialized) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    return const DiscoveryScreen();
  }
}
