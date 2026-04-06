import 'package:flutter/material.dart';

class AppTheme {
  static const _seed = Color(0xFF6750A4);

  static final light = ThemeData(
    colorSchemeSeed: _seed,
    brightness: Brightness.light,
    useMaterial3: true,
  );

  static final dark = ThemeData(
    colorSchemeSeed: _seed,
    brightness: Brightness.dark,
    useMaterial3: true,
  );

  static const avatarColors = [
    Color(0xFF6750A4),
    Color(0xFF0061A4),
    Color(0xFF006E1C),
    Color(0xFF924C25),
    Color(0xFFBA1A1A),
    Color(0xFF7D5260),
    Color(0xFF006A60),
    Color(0xFF6E5300),
  ];

  static Color avatarColor(int index) =>
      avatarColors[index.abs() % avatarColors.length];
}
