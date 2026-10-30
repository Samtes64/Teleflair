import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:teleboard/app/theme/app_theme.dart';
import 'package:teleboard/app/theme/design_tokens.dart';
import 'package:teleboard/core/storage/database_service.dart';
import 'package:teleboard/core/telemetry/install_telemetry_service.dart';
import 'package:teleboard/features/settings/presentation/settings_screen.dart';
import 'package:teleboard/features/sticker_packs/presentation/import_screen.dart';
import 'package:teleboard/features/stickers/presentation/stickers_screen.dart';

class TeleboardApp extends StatefulWidget {
  const TeleboardApp({super.key});

  @override
  State<TeleboardApp> createState() => _TeleboardAppState();
}

class _TeleboardAppState extends State<TeleboardApp> {
  static const _channel = MethodChannel('com.teleboard.teleboard/ime_control');
  bool _isDarkMode = false;
  int _currentIndex = 0;

  @override
  void initState() {
    super.initState();
    _loadThemePreference();
    _cleanupDummyPacks();
    InstallTelemetryService.reportInstallIfNeeded();
    _checkInitialTab();
    _channel.setMethodCallHandler(_handleNativeCall);
  }

  Future<void> _checkInitialTab() async {
    try {
      final initialTab = await _channel.invokeMethod<int>('getInitialTab');
      if (initialTab != null && initialTab >= 0 && initialTab < 3) {
        setState(() => _currentIndex = initialTab);
      }
    } catch (_) {}
  }

  Future<dynamic> _handleNativeCall(MethodCall call) async {
    if (call.method == 'onTabRequested') {
      final tab = call.arguments as int?;
      if (tab != null && tab >= 0 && tab < 3) {
        setState(() => _currentIndex = tab);
      }
    }
  }

  Future<void> _loadThemePreference() async {
    final prefs = await SharedPreferences.getInstance();
    final isDark = prefs.getBool('is_dark_mode') ?? false;
    setState(() => _isDarkMode = isDark);
  }

  void _toggleTheme() async {
    final prefs = await SharedPreferences.getInstance();
    final newMode = !_isDarkMode;
    await prefs.setBool('is_dark_mode', newMode);
    setState(() => _isDarkMode = newMode);
  }

  Future<void> _cleanupDummyPacks() async {
    try {
      final db = DatabaseService.instance;
      await db.cleanupDummyPacks();
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Teleflair',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: _isDarkMode ? ThemeMode.dark : ThemeMode.light,
      home: Scaffold(
        body: IndexedStack(
          index: _currentIndex,
          children: [
            const StickersScreen(),
            const ImportScreen(),
            SettingsScreen(
              onThemeToggle: _toggleTheme,
              isDarkMode: _isDarkMode,
            ),
          ],
        ),
        bottomNavigationBar: SafeArea(
          top: false,
          child: SizedBox(
            height: 72,
            child: Stack(
              clipBehavior: Clip.none,
              alignment: Alignment.bottomCenter,
              children: [
                // 1. Flat top bar container
                Positioned(
                  left: 0,
                  right: 0,
                  bottom: 0,
                  child: Container(
                    height: 58,
                    decoration: BoxDecoration(
                      color: _isDarkMode ? const Color(0xFF10121A) : Colors.white,
                      border: Border(
                        top: BorderSide(
                          color: _isDarkMode ? const Color(0xFF232634) : const Color(0xFFE4E4E7),
                          width: 0.8,
                        ),
                      ),
                    ),
                    child: Row(
                      children: [
                        // Left: Stickers Tab
                        Expanded(
                          child: InkWell(
                            onTap: () => setState(() => _currentIndex = 0),
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Icon(
                                  _currentIndex == 0
                                      ? CupertinoIcons.square_grid_2x2_fill
                                      : CupertinoIcons.square_grid_2x2,
                                  size: 22,
                                  color: _currentIndex == 0
                                      ? const Color(0xFF5931FE)
                                      : (_isDarkMode ? AppColors.darkTextMuted : AppColors.lightTextMuted),
                                ),
                                const SizedBox(height: 3),
                                Text(
                                  'Stickers',
                                  style: TextStyle(
                                    fontSize: 11,
                                    fontWeight: _currentIndex == 0 ? FontWeight.w600 : FontWeight.w500,
                                    color: _currentIndex == 0
                                        ? const Color(0xFF5931FE)
                                        : (_isDarkMode ? AppColors.darkTextMuted : AppColors.lightTextMuted),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),

                        // Center Gap reserved for elevated circle
                        const SizedBox(width: 76),

                        // Right: Settings Tab
                        Expanded(
                          child: InkWell(
                            onTap: () => setState(() => _currentIndex = 2),
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Icon(
                                  _currentIndex == 2
                                      ? CupertinoIcons.gear_alt_fill
                                      : CupertinoIcons.gear_alt,
                                  size: 22,
                                  color: _currentIndex == 2
                                      ? const Color(0xFF5931FE)
                                      : (_isDarkMode ? AppColors.darkTextMuted : AppColors.lightTextMuted),
                                ),
                                const SizedBox(height: 3),
                                Text(
                                  'Settings',
                                  style: TextStyle(
                                    fontSize: 11,
                                    fontWeight: _currentIndex == 2 ? FontWeight.w600 : FontWeight.w500,
                                    color: _currentIndex == 2
                                        ? const Color(0xFF5931FE)
                                        : (_isDarkMode ? AppColors.darkTextMuted : AppColors.lightTextMuted),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),

                // 2. Elevated Floating Center CTA Circle (Import)
                Positioned(
                  top: 0,
                  child: GestureDetector(
                    onTap: () => setState(() => _currentIndex = 1),
                    child: Container(
                      width: 56,
                      height: 56,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: const Color(0xFF5931FE),
                        boxShadow: [
                          BoxShadow(
                            color: const Color(0xFF5931FE).withValues(alpha: 0.45),
                            blurRadius: 14,
                            spreadRadius: 1,
                            offset: const Offset(0, 4),
                          ),
                        ],
                      ),
                      child: const Center(
                        child: Icon(
                          CupertinoIcons.plus,
                          color: Colors.white,
                          size: 26,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
