import 'package:flutter/material.dart';

/// Centralized design tokens for Teleboard.
/// Ultra-minimalist, monochrome, Apple-inspired aesthetics.
class AppColors {
  // Dark palette (Default)
  static const Color darkBg = Color(0xFF09090B);
  static const Color darkCard = Color(0xFF18181B);
  static const Color darkBorder = Color(0xFF27272A);
  static const Color darkBorderSubtle = Color(0xFF1F1F23);
  static const Color darkText = Color(0xFFF4F4F5);
  static const Color darkTextMuted = Color(0xFFA1A1AA);
  static const Color darkTextDim = Color(0xFF71717A);
  static const Color darkActiveTab = Color(0xFF27272A);

  // Light palette
  static const Color lightBg = Color(0xFFF8F8FA);
  static const Color lightCard = Color(0xFFFFFFFF);
  static const Color lightBorder = Color(0xFFE4E4E7);
  static const Color lightBorderSubtle = Color(0xFFECECEE);
  static const Color lightText = Color(0xFF09090B);
  static const Color lightTextMuted = Color(0xFF71717A);
  static const Color lightTextDim = Color(0xFFA1A1AA);
  static const Color lightActiveTab = Color(0xFFE4E4E7);

  // Accent monochrome
  static const Color white = Color(0xFFFFFFFF);
  static const Color black = Color(0xFF000000);
}

class AppRadius {
  static const double sm = 8.0;
  static const double md = 12.0;
  static const double lg = 16.0;
  static const double pill = 999.0;
}

class AppSpacing {
  static const double xs = 4.0;
  static const double sm = 8.0;
  static const double md = 16.0;
  static const double lg = 24.0;
  static const double xl = 32.0;
}
