import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

class UserPreferences extends ChangeNotifier {
  late SharedPreferences _prefs;

  String _displayName = '';
  bool _onboardingComplete = false;
  String _darkMode = 'system'; // system, on, off
  bool _discoverable = true;

  String get displayName => _displayName;
  bool get onboardingComplete => _onboardingComplete;
  String get darkMode => _darkMode;
  bool get discoverable => _discoverable;

  ThemeMode get themeMode {
    switch (_darkMode) {
      case 'on': return ThemeMode.dark;
      case 'off': return ThemeMode.light;
      default: return ThemeMode.system;
    }
  }

  Future<void> load() async {
    _prefs = await SharedPreferences.getInstance();
    _displayName = _prefs.getString('display_name') ?? '';
    _onboardingComplete = _prefs.getBool('onboarding_complete') ?? false;
    _darkMode = _prefs.getString('dark_mode') ?? 'system';
    _discoverable = _prefs.getBool('discoverable') ?? true;
    notifyListeners();
  }

  Future<void> setDisplayName(String name) async {
    _displayName = name;
    await _prefs.setString('display_name', name);
    notifyListeners();
  }

  Future<void> setOnboardingComplete(bool v) async {
    _onboardingComplete = v;
    await _prefs.setBool('onboarding_complete', v);
    notifyListeners();
  }

  Future<void> setDarkMode(String mode) async {
    _darkMode = mode;
    await _prefs.setString('dark_mode', mode);
    notifyListeners();
  }

  Future<void> setDiscoverable(bool v) async {
    _discoverable = v;
    await _prefs.setBool('discoverable', v);
    notifyListeners();
  }
}
