import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:teleboard/app/theme/design_tokens.dart';
import 'package:teleboard/core/platform/ime_platform_bridge.dart';
import 'package:teleboard/core/storage/database_service.dart';
import 'package:teleboard/core/updater/app_updater_service.dart';
import 'package:teleboard/features/delivery/domain/delivery_state.dart';

class SettingsScreen extends StatefulWidget {
  final VoidCallback onThemeToggle;
  final bool isDarkMode;

  const SettingsScreen({
    super.key,
    required this.onThemeToggle,
    required this.isDarkMode,
  });

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> with WidgetsBindingObserver {
  final DatabaseService _db = DatabaseService.instance;
  final TextEditingController _botTokenController = TextEditingController();

  bool _isImeEnabled = false;
  bool _isImeSelected = false;
  bool _isAccessibilityEnabled = false;
  bool _enableTextArtFallback = true;
  String _keyboardArtStyle = 'braille';
  String _keyboardArtTheme = 'dark';
  Map<String, dynamic> _storageMetrics = {};
  List<DeliveryRecord> _recentDeliveries = [];

  String _keyboardTheme = 'midnight_slate';
  String _keyboardFontSize = 'medium';
  String _keyboardHeightScale = 'standard';
  String _keyboardKeyShape = 'rounded';
  bool _keyboardShowSubscripts = true;
  String _keyboardLongPressDelay = '450';
  bool _glideEnabled = true;
  bool _soundEnabled = false;
  String _soundStyle = 'mechanical';
  bool _hapticEnabled = true;
  bool _physicsEnabled = true;
  bool _autoAdaptSms = true;

  bool _isCheckingUpdate = false;
  AppUpdateInfo? _availableUpdate;
  double? _downloadProgress;
  String? _updateStatusMessage;
  String? _pendingApkPath;
  bool _canInstallUnknown = true;

  Future<void> _handleCheckForUpdate() async {
    setState(() {
      _isCheckingUpdate = true;
      _updateStatusMessage = null;
    });

    final update = await AppUpdaterService.checkForUpdate();

    if (mounted) {
      setState(() {
        _isCheckingUpdate = false;
        _availableUpdate = update;
        if (update == null) {
          _updateStatusMessage = 'Could not reach update server. Check your connection.';
        } else if (!update.hasUpdate) {
          _updateStatusMessage = 'You are running the latest version of Teleflair (v${AppUpdaterService.currentVersionName}).';
        }
      });
    }
  }

  Future<void> _handleDownloadAndInstall() async {
    final update = _availableUpdate;
    if (update == null || update.downloadUrl.isEmpty) return;

    // Check if we already have a cached valid APK
    final cachedApk = await AppUpdaterService.getCachedApk();
    if (cachedApk != null && _canInstallUnknown) {
      setState(() {
        _updateStatusMessage = 'Opening package installer...';
      });
      final result = await AppUpdaterService.installExistingApk(cachedApk.path);
      if (mounted) {
        setState(() {
          if (result.isSuccess) {
            _updateStatusMessage = 'Package installer opened! Please confirm the prompt.';
          } else if (result.isPermissionNeeded) {
            _pendingApkPath = cachedApk.path;
            _updateStatusMessage = 'Permission needed: Please enable "Allow from this source" in Settings.';
          } else {
            _updateStatusMessage = result.errorMessage ?? 'Installation failed.';
          }
        });
      }
      return;
    }

    setState(() {
      _downloadProgress = 0.01;
      _updateStatusMessage = 'Downloading update package...';
    });

    final result = await AppUpdaterService.downloadAndInstallApk(
      downloadUrl: update.downloadUrl,
      onProgress: (p) {
        if (mounted) {
          setState(() => _downloadProgress = p);
        }
      },
    );

    if (mounted) {
      setState(() {
        _downloadProgress = null;
        if (result.isSuccess) {
          _pendingApkPath = null;
          _updateStatusMessage = 'Package installer opened! Please confirm the prompt.';
        } else if (result.isPermissionNeeded) {
          _pendingApkPath = result.filePath;
          _updateStatusMessage = 'Permission required: Please enable "Allow from this source" in Settings, then return here to install.';
        } else if (result.status == AppInstallStatus.downloadFailed) {
          _updateStatusMessage = 'Download failed: ${result.errorMessage ?? "Check your network connection"}';
        } else {
          _updateStatusMessage = 'Installation error: ${result.errorMessage ?? "Invalid package"}';
        }
      });
    }
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _checkImeStatus();
    _checkInstallPermission();
    _loadSettings();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _botTokenController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _checkImeStatus();
      _checkInstallPermission();
    }
  }

  Future<void> _checkInstallPermission() async {
    final canInstall = await AppUpdaterService.canRequestPackageInstalls();
    if (mounted) {
      setState(() => _canInstallUnknown = canInstall);
    }
    if (canInstall && _pendingApkPath != null) {
      final path = _pendingApkPath!;
      _pendingApkPath = null;
      final result = await AppUpdaterService.installExistingApk(path);
      if (mounted) {
        setState(() {
          if (result.isSuccess) {
            _updateStatusMessage = 'Package installer opened! Please confirm the prompt.';
          } else {
            _updateStatusMessage = result.errorMessage ?? 'Installation failed.';
          }
        });
      }
    }
  }

  Future<void> _checkImeStatus() async {
    final enabled = await ImePlatformBridge.isImeEnabled();
    final selected = await ImePlatformBridge.isImeSelected();
    final a11y = await ImePlatformBridge.isAccessibilityEnabled();
    if (mounted) {
      setState(() {
        _isImeEnabled = enabled;
        _isImeSelected = selected;
        _isAccessibilityEnabled = a11y;
      });
    }
  }

  Future<void> _loadSettings() async {
    final prefs = await SharedPreferences.getInstance();
    final metrics = await _db.getStorageMetrics();
    final deliveries = await _db.getRecentDeliveries(limit: 15);
    if (mounted) {
      setState(() {
        _botTokenController.text = prefs.getString('telegram_bot_token') ?? '';
        _enableTextArtFallback = prefs.getBool('enable_text_art_fallback') ?? true;
        _keyboardArtStyle = prefs.getString('keyboard_art_style') ?? 'braille';
        _keyboardArtTheme = prefs.getString('keyboard_art_theme') ?? 'dark';
        _keyboardTheme = prefs.getString('keyboard_theme') ?? 'midnight_slate';
        _keyboardFontSize = prefs.getString('keyboard_font_size') ?? 'medium';
        _keyboardHeightScale = prefs.getString('keyboard_height_scale') ?? 'standard';
        _keyboardKeyShape = prefs.getString('keyboard_key_shape') ?? 'rounded';
        _keyboardShowSubscripts = prefs.getBool('keyboard_show_subscripts') ?? true;
        _keyboardLongPressDelay = prefs.getString('keyboard_long_press_delay') ?? '450';
        _glideEnabled = prefs.getBool('keyboard_glide_enabled') ?? true;
        _soundEnabled = prefs.getBool('keyboard_sound_enabled') ?? false;
        _soundStyle = prefs.getString('keyboard_sound_style') ?? 'mechanical';
        _hapticEnabled = prefs.getBool('keyboard_haptic_enabled') ?? true;
        _physicsEnabled = prefs.getBool('keyboard_physics_enabled') ?? true;
        _autoAdaptSms = prefs.getBool('auto_adapt_sms') ?? true;
        _storageMetrics = metrics;
        _recentDeliveries = deliveries;
      });
    }
  }

  Future<void> _updateKeySetting(VoidCallback updateFn) async {
    setState(updateFn);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('keyboard_font_size', _keyboardFontSize);
    await prefs.setString('keyboard_height_scale', _keyboardHeightScale);
    await prefs.setString('keyboard_key_shape', _keyboardKeyShape);
    await prefs.setBool('keyboard_show_subscripts', _keyboardShowSubscripts);
    await prefs.setString('keyboard_long_press_delay', _keyboardLongPressDelay);
    await prefs.setString('keyboard_theme', _keyboardTheme);
    await prefs.setBool('keyboard_glide_enabled', _glideEnabled);
    await prefs.setBool('keyboard_sound_enabled', _soundEnabled);
    await prefs.setString('keyboard_sound_style', _soundStyle);
    await prefs.setBool('keyboard_haptic_enabled', _hapticEnabled);
    await prefs.setBool('keyboard_physics_enabled', _physicsEnabled);
    await prefs.setString('keyboard_art_style', _keyboardArtStyle);
    await prefs.setString('keyboard_art_theme', _keyboardArtTheme);
  }

  Future<void> _saveSettings() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('telegram_bot_token', _botTokenController.text.trim());
    await prefs.setBool('enable_text_art_fallback', _enableTextArtFallback);
    await prefs.setString('keyboard_art_style', _keyboardArtStyle);
    await prefs.setString('keyboard_art_theme', _keyboardArtTheme);
    await prefs.setString('keyboard_theme', _keyboardTheme);
    await prefs.setString('keyboard_font_size', _keyboardFontSize);
    await prefs.setString('keyboard_height_scale', _keyboardHeightScale);
    await prefs.setString('keyboard_key_shape', _keyboardKeyShape);
    await prefs.setBool('keyboard_show_subscripts', _keyboardShowSubscripts);
    await prefs.setString('keyboard_long_press_delay', _keyboardLongPressDelay);
    await prefs.setBool('keyboard_glide_enabled', _glideEnabled);
    await prefs.setBool('keyboard_sound_enabled', _soundEnabled);
    await prefs.setString('keyboard_sound_style', _soundStyle);
    await prefs.setBool('keyboard_haptic_enabled', _hapticEnabled);
    await prefs.setBool('keyboard_physics_enabled', _physicsEnabled);
    await prefs.setBool('auto_adapt_sms', _autoAdaptSms);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Settings saved successfully')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = widget.isDarkMode;
    final textColor = isDark ? AppColors.darkText : AppColors.lightText;
    final mutedColor = isDark ? AppColors.darkTextMuted : AppColors.lightTextMuted;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Settings'),
        actions: [
          IconButton(
            icon: Icon(isDark ? Icons.light_mode_outlined : Icons.dark_mode_outlined, size: 20),
            onPressed: widget.onThemeToggle,
            tooltip: 'Toggle Theme',
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          // Keyboard Activation Status Card
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.keyboard_outlined, size: 20, color: textColor),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Keyboard Setup',
                          style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 16),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Enable and select Teleflair Keyboard to start typing and sending stickers.',
                    style: TextStyle(color: mutedColor, fontSize: 13),
                  ),
                  const SizedBox(height: 16),

                  // Step 1: Enable
                  _buildSetupStep(
                    step: 1,
                    title: 'Enable in Settings',
                    isDone: _isImeEnabled,
                    buttonLabel: 'Enable Keyboard',
                    onAction: () async {
                      await ImePlatformBridge.openImeSettings();
                      Future.delayed(const Duration(seconds: 1), _checkImeStatus);
                    },
                  ),
                  const SizedBox(height: 12),

                  // Step 2: Select
                  _buildSetupStep(
                    step: 2,
                    title: 'Select Active Keyboard',
                    isDone: _isImeSelected,
                    buttonLabel: 'Select Keyboard',
                    onAction: () async {
                      await ImePlatformBridge.showImePicker();
                      Future.delayed(const Duration(seconds: 1), _checkImeStatus);
                    },
                  ),
                  const SizedBox(height: 12),

                  // Step 3: Smart Context Detection
                  _buildSetupStep(
                    step: 3,
                    title: 'Smart Context Detection',
                    subtitle: _isAccessibilityEnabled
                        ? 'Active • Auto-adapts comments, posts & DMs'
                        : 'Optional • Auto-adapts comments, posts & DMs',
                    isDone: _isAccessibilityEnabled,
                    buttonLabel: 'Enable Detection',
                    onAction: () async {
                      await ImePlatformBridge.openAccessibilitySettings();
                      Future.delayed(const Duration(seconds: 1), _checkImeStatus);
                    },
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),

          // Keyboard Theme & Sensory Preferences
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.palette_outlined, size: 20, color: textColor),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Keyboard Experience & Customization',
                          style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 16),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Customize typography sizing, keyboard height, key shape, and input sensory feedback.',
                    style: TextStyle(color: mutedColor, fontSize: 13),
                  ),
                  const SizedBox(height: 16),

                  // Interactive Live Preview
                  _buildKeyPreview(isDark),
                  const SizedBox(height: 18),

                  // Key Typography Size
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Key Text Size (Typography)', style: TextStyle(color: textColor, fontSize: 13, fontWeight: FontWeight.w500)),
                      const SizedBox(height: 6),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        decoration: BoxDecoration(
                          color: isDark ? const Color(0xFF1E1E22) : const Color(0xFFF4F4F5),
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(
                            color: isDark ? AppColors.darkBorder : AppColors.lightBorder,
                          ),
                        ),
                        child: DropdownButtonHideUnderline(
                          child: DropdownButton<String>(
                            isExpanded: true,
                            value: _keyboardFontSize,
                            dropdownColor: isDark ? AppColors.darkCard : AppColors.lightCard,
                            items: const [
                              DropdownMenuItem(value: 'small', child: Text('Small (18.5 sp)')),
                              DropdownMenuItem(value: 'medium', child: Text('Standard (21.0 sp) - Prominent Default')),
                              DropdownMenuItem(value: 'large', child: Text('Large (23.5 sp) - Extra Legible')),
                              DropdownMenuItem(value: 'xlarge', child: Text('Extra Large (26.0 sp) - Maximum Size')),
                            ],
                            onChanged: (val) {
                              if (val != null) _updateKeySetting(() => _keyboardFontSize = val);
                            },
                          ),
                        ),
                      ),
                    ],
                  ),
                  const Divider(height: 20),

                  // Keyboard Height Scale
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Keyboard Height', style: TextStyle(color: textColor, fontSize: 13, fontWeight: FontWeight.w500)),
                      const SizedBox(height: 6),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        decoration: BoxDecoration(
                          color: isDark ? const Color(0xFF1E1E22) : const Color(0xFFF4F4F5),
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(
                            color: isDark ? AppColors.darkBorder : AppColors.lightBorder,
                          ),
                        ),
                        child: DropdownButtonHideUnderline(
                          child: DropdownButton<String>(
                            isExpanded: true,
                            value: _keyboardHeightScale,
                            dropdownColor: isDark ? AppColors.darkCard : AppColors.lightCard,
                            items: const [
                              DropdownMenuItem(value: 'compact', child: Text('Compact (88% height)')),
                              DropdownMenuItem(value: 'standard', child: Text('Standard (100% height) - Default')),
                              DropdownMenuItem(value: 'tall', child: Text('Tall (112% height)')),
                              DropdownMenuItem(value: 'extra_tall', child: Text('Extra Tall (125% height)')),
                            ],
                            onChanged: (val) {
                              if (val != null) _updateKeySetting(() => _keyboardHeightScale = val);
                            },
                          ),
                        ),
                      ),
                    ],
                  ),
                  const Divider(height: 20),

                  // Key Corner Radius / Shape
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Key Shape Style', style: TextStyle(color: textColor, fontSize: 13, fontWeight: FontWeight.w500)),
                      const SizedBox(height: 6),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        decoration: BoxDecoration(
                          color: isDark ? const Color(0xFF1E1E22) : const Color(0xFFF4F4F5),
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(
                            color: isDark ? AppColors.darkBorder : AppColors.lightBorder,
                          ),
                        ),
                        child: DropdownButtonHideUnderline(
                          child: DropdownButton<String>(
                            isExpanded: true,
                            value: _keyboardKeyShape,
                            dropdownColor: isDark ? AppColors.darkCard : AppColors.lightCard,
                            items: const [
                              DropdownMenuItem(value: 'rounded', child: Text('Rounded Apple (Default, 6.5dp)')),
                              DropdownMenuItem(value: 'pill', child: Text('Curved Pill (15dp)')),
                              DropdownMenuItem(value: 'box', child: Text('Classic Box (3.5dp)')),
                            ],
                            onChanged: (val) {
                              if (val != null) _updateKeySetting(() => _keyboardKeyShape = val);
                            },
                          ),
                        ),
                      ),
                    ],
                  ),
                  const Divider(height: 20),

                  // Show Subscript Hints
                  SwitchListTile.adaptive(
                    contentPadding: EdgeInsets.zero,
                    title: Text(
                      'Secondary Symbol Hints',
                      style: TextStyle(color: textColor, fontSize: 13, fontWeight: FontWeight.w500),
                    ),
                    subtitle: Text(
                      'Display long-press number and symbol shortcuts in the corner of letter keys.',
                      style: TextStyle(color: mutedColor, fontSize: 11),
                    ),
                    value: _keyboardShowSubscripts,
                    onChanged: (val) {
                      _updateKeySetting(() => _keyboardShowSubscripts = val);
                    },
                  ),
                  const Divider(height: 20),

                  // Long-Press Delay
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Long-Press Delay', style: TextStyle(color: textColor, fontSize: 13, fontWeight: FontWeight.w500)),
                      const SizedBox(height: 6),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        decoration: BoxDecoration(
                          color: isDark ? const Color(0xFF1E1E22) : const Color(0xFFF4F4F5),
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(
                            color: isDark ? AppColors.darkBorder : AppColors.lightBorder,
                          ),
                        ),
                        child: DropdownButtonHideUnderline(
                          child: DropdownButton<String>(
                            isExpanded: true,
                            value: _keyboardLongPressDelay,
                            dropdownColor: isDark ? AppColors.darkCard : AppColors.lightCard,
                            items: const [
                              DropdownMenuItem(value: '300', child: Text('Fast (300 ms)')),
                              DropdownMenuItem(value: '450', child: Text('Standard (450 ms) - Default')),
                              DropdownMenuItem(value: '600', child: Text('Relaxed (600 ms)')),
                            ],
                            onChanged: (val) {
                              if (val != null) _updateKeySetting(() => _keyboardLongPressDelay = val);
                            },
                          ),
                        ),
                      ),
                    ],
                  ),
                  const Divider(height: 20),

                  // Theme Selection Dropdown
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Keyboard Theme', style: TextStyle(color: textColor, fontSize: 13, fontWeight: FontWeight.w500)),
                      const SizedBox(height: 6),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        decoration: BoxDecoration(
                          color: isDark ? const Color(0xFF1E1E22) : const Color(0xFFF4F4F5),
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(
                            color: isDark ? AppColors.darkBorder : AppColors.lightBorder,
                          ),
                        ),
                        child: DropdownButtonHideUnderline(
                          child: DropdownButton<String>(
                            isExpanded: true,
                            value: _keyboardTheme,
                            dropdownColor: isDark ? AppColors.darkCard : AppColors.lightCard,
                            items: const [
                              DropdownMenuItem(value: 'midnight_slate', child: Text('Midnight Slate (Default)')),
                              DropdownMenuItem(value: 'pitch_black_oled', child: Text('Pitch Black OLED')),
                              DropdownMenuItem(value: 'slate_gray', child: Text('Slate Gray')),
                              DropdownMenuItem(value: 'paper_white', child: Text('Paper White')),
                              DropdownMenuItem(value: 'high_contrast_cyber', child: Text('Cyberpunk Neon')),
                            ],
                            onChanged: (val) {
                              if (val != null) _updateKeySetting(() => _keyboardTheme = val);
                            },
                          ),
                        ),
                      ),
                    ],
                  ),
                  const Divider(height: 20),

                  // Continuous Glide Typing
                  SwitchListTile.adaptive(
                    contentPadding: EdgeInsets.zero,
                    title: Text(
                      'Continuous Glide / Gesture Typing',
                      style: TextStyle(color: textColor, fontSize: 13, fontWeight: FontWeight.w500),
                    ),
                    subtitle: Text(
                      'Type by sliding finger across letters with Catmull-Rom spline smoothing and DTW recognition.',
                      style: TextStyle(color: mutedColor, fontSize: 11),
                    ),
                    value: _glideEnabled,
                    onChanged: (val) {
                      _updateKeySetting(() => _glideEnabled = val);
                    },
                  ),
                  const Divider(height: 20),

                  // Particle Physics
                  SwitchListTile.adaptive(
                    contentPadding: EdgeInsets.zero,
                    title: Text(
                      'Interactive Particle Physics',
                      style: TextStyle(color: textColor, fontSize: 13, fontWeight: FontWeight.w500),
                    ),
                    subtitle: Text(
                      'Accelerometer-driven key tap rebound impulses and realistic physical drift.',
                      style: TextStyle(color: mutedColor, fontSize: 11),
                    ),
                    value: _physicsEnabled,
                    onChanged: (val) {
                      _updateKeySetting(() => _physicsEnabled = val);
                    },
                  ),
                  const Divider(height: 20),

                  // Mechanical Sound Feedback
                  SwitchListTile.adaptive(
                    contentPadding: EdgeInsets.zero,
                    title: Text(
                      'Key Tap Sound Effects',
                      style: TextStyle(color: textColor, fontSize: 13, fontWeight: FontWeight.w500),
                    ),
                    subtitle: Text(
                      'Ultra-low-latency SoundPool audio feedback on key press.',
                      style: TextStyle(color: mutedColor, fontSize: 11),
                    ),
                    value: _soundEnabled,
                    onChanged: (val) {
                      _updateKeySetting(() => _soundEnabled = val);
                    },
                  ),
                  if (_soundEnabled) ...[
                    const SizedBox(height: 10),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Sound Profile', style: TextStyle(color: textColor, fontSize: 13, fontWeight: FontWeight.w500)),
                        const SizedBox(height: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          decoration: BoxDecoration(
                            color: isDark ? const Color(0xFF1E1E22) : const Color(0xFFF4F4F5),
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(
                              color: isDark ? AppColors.darkBorder : AppColors.lightBorder,
                            ),
                          ),
                          child: DropdownButtonHideUnderline(
                            child: DropdownButton<String>(
                              isExpanded: true,
                              value: _soundStyle,
                              dropdownColor: isDark ? AppColors.darkCard : AppColors.lightCard,
                              items: const [
                                DropdownMenuItem(value: 'mechanical', child: Text('Mechanical Switch')),
                                DropdownMenuItem(value: 'typewriter', child: Text('Vintage Typewriter')),
                                DropdownMenuItem(value: 'bubble', child: Text('Soft Bubble Pop')),
                              ],
                              onChanged: (val) {
                                if (val != null) _updateKeySetting(() => _soundStyle = val);
                              },
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                  const Divider(height: 20),

                  // Haptic Feedback
                  SwitchListTile.adaptive(
                    contentPadding: EdgeInsets.zero,
                    title: Text(
                      'Haptic Tap Feedback',
                      style: TextStyle(color: textColor, fontSize: 13, fontWeight: FontWeight.w500),
                    ),
                    subtitle: Text(
                      'Subtle vibration pulse on each key contact.',
                      style: TextStyle(color: mutedColor, fontSize: 11),
                    ),
                    value: _hapticEnabled,
                    onChanged: (val) {
                      _updateKeySetting(() => _hapticEnabled = val);
                    },
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),

          // Messaging Compatibility & Fallback
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.mark_chat_read_outlined, size: 20, color: textColor),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Messaging Compatibility & Fallback',
                          style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 16),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Automatically adapts stickers to recipient messaging capabilities across all apps.',
                    style: TextStyle(color: mutedColor, fontSize: 13),
                  ),
                  const SizedBox(height: 16),

                  SwitchListTile.adaptive(
                    contentPadding: EdgeInsets.zero,
                    title: Text(
                      'Universal SMS Adaptation',
                      style: TextStyle(color: textColor, fontSize: 13, fontWeight: FontWeight.w500),
                    ),
                    subtitle: Text(
                      'When texting standard SMS/MMS contacts, automatically adapts stickers to prevent carrier delivery failures.',
                      style: TextStyle(color: mutedColor, fontSize: 11),
                    ),
                    value: _autoAdaptSms,
                    onChanged: (val) {
                      setState(() => _autoAdaptSms = val);
                    },
                  ),
                  const Divider(height: 24),
                  SwitchListTile.adaptive(
                    contentPadding: EdgeInsets.zero,
                    title: Text(
                      'Context Art for Non-Image Apps',
                      style: TextStyle(color: textColor, fontSize: 13, fontWeight: FontWeight.w500),
                    ),
                    subtitle: Text(
                      'Converts stickers into platform-calibrated text art in comment fields and text-only apps.',
                      style: TextStyle(color: mutedColor, fontSize: 11),
                    ),
                    value: _enableTextArtFallback,
                    onChanged: (val) {
                      setState(() => _enableTextArtFallback = val);
                    },
                  ),
                  if (_enableTextArtFallback) ...[
                    const SizedBox(height: 12),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Art Visual Style', style: TextStyle(color: textColor, fontSize: 13, fontWeight: FontWeight.w500)),
                        const SizedBox(height: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          decoration: BoxDecoration(
                            color: isDark ? const Color(0xFF1E1E22) : const Color(0xFFF4F4F5),
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(
                              color: isDark ? AppColors.darkBorder : AppColors.lightBorder,
                            ),
                          ),
                          child: DropdownButtonHideUnderline(
                            child: DropdownButton<String>(
                              isExpanded: true,
                              value: _keyboardArtStyle,
                              dropdownColor: isDark ? AppColors.darkCard : AppColors.lightCard,
                              items: const [
                                DropdownMenuItem(value: 'braille', child: Text('High-Density Braille (Unicode 2×4, Default)')),
                                DropdownMenuItem(value: 'ascii', child: Text('Classic Monospace ASCII')),
                              ],
                              onChanged: (val) {
                                if (val != null) _updateKeySetting(() => _keyboardArtStyle = val);
                              },
                            ),
                          ),
                        ),
                        const SizedBox(height: 12),
                        Text('Art Theme Polarity', style: TextStyle(color: textColor, fontSize: 13, fontWeight: FontWeight.w500)),
                        const SizedBox(height: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          decoration: BoxDecoration(
                            color: isDark ? const Color(0xFF1E1E22) : const Color(0xFFF4F4F5),
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(
                              color: isDark ? AppColors.darkBorder : AppColors.lightBorder,
                            ),
                          ),
                          child: DropdownButtonHideUnderline(
                            child: DropdownButton<String>(
                              isExpanded: true,
                              value: _keyboardArtTheme,
                              dropdownColor: isDark ? AppColors.darkCard : AppColors.lightCard,
                              items: const [
                                DropdownMenuItem(value: 'dark', child: Text('Dark Theme Art (White dots on dark background, Default)')),
                                DropdownMenuItem(value: 'light', child: Text('Light Theme Art (Black ink dots on white background)')),
                              ],
                              onChanged: (val) {
                                if (val != null) _updateKeySetting(() => _keyboardArtTheme = val);
                              },
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),

          // Recent Delivery Activity & Logs
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.history_outlined, size: 20, color: textColor),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Recent Delivery Dispatches',
                          style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 16),
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.refresh, size: 18),
                        onPressed: () async {
                          final deliveries = await _db.getRecentDeliveries(limit: 15);
                          setState(() => _recentDeliveries = deliveries);
                        },
                        tooltip: 'Refresh Logs',
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Audit trail of recent sticker transmissions across WhatsApp, Telegram, and other apps.',
                    style: TextStyle(color: mutedColor, fontSize: 13),
                  ),
                  const SizedBox(height: 16),
                  if (_recentDeliveries.isEmpty)
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 16),
                      decoration: BoxDecoration(
                        color: isDark ? const Color(0xFF1E1E22) : const Color(0xFFF4F4F5),
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(
                          color: isDark ? AppColors.darkBorder : AppColors.lightBorder,
                        ),
                      ),
                      child: Column(
                        children: [
                          Icon(CupertinoIcons.tray, size: 28, color: mutedColor),
                          const SizedBox(height: 8),
                          Text(
                            'No Dispatches Recorded Yet',
                            style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 13),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Stickers sent via keyboard will show their delivery format and status here.',
                            textAlign: TextAlign.center,
                            style: TextStyle(color: mutedColor, fontSize: 11),
                          ),
                        ],
                      ),
                    )
                  else
                    ListView.separated(
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      itemCount: _recentDeliveries.length > 5 ? 5 : _recentDeliveries.length,
                      separatorBuilder: (_, index) => const Divider(height: 12),
                      itemBuilder: (context, index) {
                        final log = _recentDeliveries[index];
                        final timeStr = '${log.timestamp.hour.toString().padLeft(2, '0')}:${log.timestamp.minute.toString().padLeft(2, '0')}';
                        return Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                              decoration: BoxDecoration(
                                color: isDark ? const Color(0xFF262833) : const Color(0xFFE4E4E7),
                                borderRadius: BorderRadius.circular(6),
                              ),
                              child: Text(
                                log.transport.name.toUpperCase(),
                                style: TextStyle(
                                  fontSize: 10,
                                  fontWeight: FontWeight.w700,
                                  color: isDark ? const Color(0xFF818CF8) : const Color(0xFF4F46E5),
                                ),
                              ),
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    log.targetPackage ?? log.details,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: TextStyle(color: textColor, fontSize: 12, fontWeight: FontWeight.w500),
                                  ),
                                  Text(
                                    log.status.name,
                                    style: TextStyle(color: mutedColor, fontSize: 10),
                                  ),
                                ],
                              ),
                            ),
                            Text(timeStr, style: TextStyle(color: mutedColor, fontSize: 11)),
                          ],
                        );
                      },
                    ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),

          // Telegram Bot API (Optional)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.send_rounded, size: 20, color: textColor),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Telegram Bot Token (Optional)',
                          style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 16),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Teleflair uses a high-speed cloud importer by default. You can optionally provide your own Telegram bot token if you prefer direct fetching.',
                    style: TextStyle(color: mutedColor, fontSize: 13),
                  ),
                  const SizedBox(height: 16),

                  TextField(
                    controller: _botTokenController,
                    obscureText: true,
                    decoration: const InputDecoration(
                      labelText: 'Bot Token (Optional)',
                      hintText: '123456789:ABCdefGHIjklMNOpqrSTUvwxYZ',
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),

          // Save Settings Button
          SizedBox(
            width: double.infinity,
            height: 44,
            child: ElevatedButton(
              style: ElevatedButton.styleFrom(
                backgroundColor: isDark ? AppColors.white : AppColors.black,
                foregroundColor: isDark ? AppColors.black : AppColors.white,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.pill)),
              ),
              onPressed: _saveSettings,
              child: const Text('Save Configuration', style: TextStyle(fontWeight: FontWeight.w600)),
            ),
          ),
          const SizedBox(height: 24),

          // Local Storage & Footprint
          Text('Local Storage Footprint', style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 15)),
          const SizedBox(height: 8),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  Expanded(child: _buildMetricItem('${_storageMetrics['packCount'] ?? 0}', 'Packs')),
                  Expanded(child: _buildMetricItem('${_storageMetrics['stickerCount'] ?? 0}', 'Stickers')),
                  Expanded(
                    child: _buildMetricItem(
                      '${((_storageMetrics['totalBytes'] ?? 0) / (1024 * 1024)).toStringAsFixed(1)} MB',
                      'Storage',
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),

          // Privacy & Security Disclosure
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.shield_outlined, size: 20, color: Colors.greenAccent),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text('Privacy Guarantee', style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 15)),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  Text(
                    '• Zero Keylogging: Teleflair does not record, log, or send any keystrokes.\n'
                    '• Offline-First: All imported stickers and recents remain on your device.\n'
                    '• Ephemeral Links: Uploaded links automatically expire within the selected window.\n'
                    '• No Privileged SMS: Uses safe system intent handoffs without reading personal SMS.',
                    style: TextStyle(color: mutedColor, fontSize: 12, height: 1.5),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),

          // App Updates & Direct Installation
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(
                        CupertinoIcons.arrow_down_circle_fill,
                        size: 20,
                        color: isDark ? AppColors.white : AppColors.black,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'App Updates & Upgrades',
                          style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 15),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Installed Version: v${AppUpdaterService.currentVersionName} (Build ${AppUpdaterService.currentVersionCode})\n'
                    'All releases use a persistent cryptographic signature so future updates install seamlessly over existing installations without losing data.',
                    style: TextStyle(color: mutedColor, fontSize: 12, height: 1.4),
                  ),
                  const SizedBox(height: 14),

                  if (_isCheckingUpdate) ...[
                    Row(
                      children: [
                        const SizedBox(
                          width: 14,
                          height: 14,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        ),
                        const SizedBox(width: 10),
                        Text('Checking for updates...', style: TextStyle(color: mutedColor, fontSize: 12)),
                      ],
                    ),
                  ] else if (_availableUpdate != null && _availableUpdate!.hasUpdate) ...[
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: isDark ? const Color(0xFF1E1E22) : const Color(0xFFF4F4F5),
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(color: Colors.green.withValues(alpha: 0.5)),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Update Available: v${_availableUpdate!.version} (Build ${_availableUpdate!.versionCode})',
                            style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 13),
                          ),
                          if (_availableUpdate!.releaseNotes.isNotEmpty) ...[
                            const SizedBox(height: 4),
                            Text(
                              _availableUpdate!.releaseNotes,
                              style: TextStyle(color: mutedColor, fontSize: 11),
                            ),
                          ],
                          const SizedBox(height: 10),
                          if (_downloadProgress != null) ...[
                            ClipRRect(
                              borderRadius: BorderRadius.circular(4),
                              child: LinearProgressIndicator(
                                value: _downloadProgress,
                                backgroundColor: isDark ? Colors.grey[800] : Colors.grey[300],
                              ),
                            ),
                            const SizedBox(height: 6),
                            Text(
                              'Downloading: ${((_downloadProgress ?? 0) * 100).toInt()}%',
                              style: TextStyle(color: mutedColor, fontSize: 11),
                            ),
                          ] else if (_pendingApkPath != null && !_canInstallUnknown) ...[
                            ElevatedButton.icon(
                              style: ElevatedButton.styleFrom(
                                backgroundColor: const Color(0xFF5931FE),
                                foregroundColor: Colors.white,
                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.pill)),
                              ),
                              icon: const Icon(Icons.security, size: 16),
                              onPressed: () async {
                                await ImePlatformBridge.openInstallPermissionSettings();
                              },
                              label: const Text('Grant Install Permission in Settings', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 12)),
                            ),
                          ] else if (_pendingApkPath != null) ...[
                            ElevatedButton.icon(
                              style: ElevatedButton.styleFrom(
                                backgroundColor: isDark ? AppColors.white : AppColors.black,
                                foregroundColor: isDark ? AppColors.black : AppColors.white,
                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.pill)),
                              ),
                              icon: const Icon(Icons.install_mobile, size: 16),
                              onPressed: _handleDownloadAndInstall,
                              label: const Text('Install Downloaded Update', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 12)),
                            ),
                          ] else ...[
                            ElevatedButton(
                              style: ElevatedButton.styleFrom(
                                backgroundColor: isDark ? AppColors.white : AppColors.black,
                                foregroundColor: isDark ? AppColors.black : AppColors.white,
                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.pill)),
                              ),
                              onPressed: _handleDownloadAndInstall,
                              child: const Text('Download & Install Update', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 12)),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ] else ...[
                    Row(
                      children: [
                        OutlinedButton(
                          style: OutlinedButton.styleFrom(
                            side: BorderSide(color: isDark ? AppColors.darkBorder : AppColors.lightBorder),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.pill)),
                          ),
                          onPressed: _handleCheckForUpdate,
                          child: Text(
                            'Check for Updates',
                            style: TextStyle(
                              color: isDark ? AppColors.white : AppColors.black,
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],

                  if (_updateStatusMessage != null) ...[
                    const SizedBox(height: 8),
                    Text(
                      _updateStatusMessage!,
                      style: TextStyle(
                        color: _updateStatusMessage!.toLowerCase().contains('failed') || _updateStatusMessage!.toLowerCase().contains('error')
                            ? Colors.redAccent
                            : (_updateStatusMessage!.contains('opened') || _availableUpdate?.hasUpdate == true ? Colors.green : mutedColor),
                        fontSize: 11,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  Widget _buildSetupStep({
    required int step,
    required String title,
    required bool isDone,
    required String buttonLabel,
    required VoidCallback onAction,
    String? subtitle,
  }) {
    final isDark = widget.isDarkMode;
    final textColor = isDark ? AppColors.darkText : AppColors.lightText;
    final mutedColor = isDark ? AppColors.darkTextMuted : AppColors.lightTextMuted;

    return Row(
      children: [
        Container(
          width: 24,
          height: 24,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: isDone ? Colors.green : (isDark ? const Color(0xFF27272A) : const Color(0xFFE4E4E7)),
          ),
          child: isDone
              ? const Icon(Icons.check, size: 14, color: Colors.white)
              : Text('$step', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: textColor)),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: TextStyle(fontWeight: FontWeight.w500, fontSize: 13, color: textColor)),
              Text(
                subtitle ?? (isDone ? 'Active & Ready' : 'Pending Action'),
                style: TextStyle(color: isDone ? Colors.green : mutedColor, fontSize: 11),
              ),
            ],
          ),
        ),
        OutlinedButton(
          style: OutlinedButton.styleFrom(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.pill)),
          ),
          onPressed: onAction,
          child: Text(buttonLabel, style: TextStyle(fontSize: 11, color: textColor)),
        ),
      ],
    );
  }

  Widget _buildMetricItem(String value, String label) {
    final isDark = widget.isDarkMode;
    return Column(
      children: [
        Text(value, style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16, color: isDark ? AppColors.darkText : AppColors.lightText)),
        const SizedBox(height: 2),
        Text(label, style: TextStyle(fontSize: 11, color: isDark ? AppColors.darkTextMuted : AppColors.lightTextMuted)),
      ],
    );
  }

  Widget _buildKeyPreview(bool isDark) {
    Color bgColor;
    Color keyBg;
    Color textPrimary;
    Color textSub;
    Color accentColor;

    switch (_keyboardTheme) {
      case 'pitch_black_oled':
        bgColor = Colors.black;
        keyBg = const Color(0xFF141416);
        textPrimary = Colors.white;
        textSub = const Color(0xFF71717A);
        accentColor = const Color(0xFF3B82F6);
        break;
      case 'slate_gray':
        bgColor = const Color(0xFF262A33);
        keyBg = const Color(0xFF383E4C);
        textPrimary = Colors.white;
        textSub = const Color(0xFFA1A1AA);
        accentColor = const Color(0xFF60A5FA);
        break;
      case 'paper_white':
        bgColor = const Color(0xFFE5E7EB);
        keyBg = Colors.white;
        textPrimary = Colors.black;
        textSub = const Color(0xFF6B7280);
        accentColor = const Color(0xFF2563EB);
        break;
      case 'high_contrast_cyber':
        bgColor = const Color(0xFF040814);
        keyBg = const Color(0xFF0c192c);
        textPrimary = const Color(0xFF00FFCC);
        textSub = const Color(0xFFFF007F);
        accentColor = const Color(0xFF00E5FF);
        break;
      case 'midnight_slate':
      default:
        bgColor = const Color(0xFF0c1017);
        keyBg = const Color(0xFF1a2230);
        textPrimary = Colors.white;
        textSub = const Color(0xFF6c829d);
        accentColor = const Color(0xFF00c2ff);
        break;
    }

    double fontSize;
    double subFontSize;
    switch (_keyboardFontSize) {
      case 'small':
        fontSize = 14.5;
        subFontSize = 7.5;
        break;
      case 'large':
        fontSize = 18.5;
        subFontSize = 10.0;
        break;
      case 'xlarge':
        fontSize = 20.5;
        subFontSize = 11.0;
        break;
      case 'medium':
      default:
        fontSize = 16.5;
        subFontSize = 8.5;
        break;
    }

    double keyHeight;
    switch (_keyboardHeightScale) {
      case 'compact':
        keyHeight = 36.0;
        break;
      case 'tall':
        keyHeight = 46.0;
        break;
      case 'extra_tall':
        keyHeight = 50.0;
        break;
      case 'standard':
      default:
        keyHeight = 42.0;
        break;
    }

    double radius;
    switch (_keyboardKeyShape) {
      case 'pill':
        radius = 16.0;
        break;
      case 'box':
        radius = 3.5;
        break;
      case 'rounded':
      default:
        radius = 6.5;
        break;
    }

    final previewKeys = [
      ('Q', '1'),
      ('W', '2'),
      ('E', '3'),
      ('R', '4'),
      ('T', '5'),
    ];

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 12),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isDark ? const Color(0xFF27272A) : const Color(0xFFE4E4E7),
        ),
      ),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: keyBg,
                  borderRadius: BorderRadius.circular(radius),
                  border: Border.all(color: const Color(0xFF2a3b50), width: 0.8),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.content_paste_outlined, size: 10, color: textSub),
                    const SizedBox(width: 4),
                    Text('PASTE', style: TextStyle(color: textSub, fontSize: 8.5, fontWeight: FontWeight.bold, letterSpacing: 0.5)),
                  ],
                ),
              ),
              const SizedBox(width: 6),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: const Color(0xFF122336),
                  borderRadius: BorderRadius.circular(radius),
                  border: Border.all(color: accentColor, width: 0.8),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.auto_awesome, size: 10, color: accentColor),
                    const SizedBox(width: 4),
                    Text('STICKERS', style: TextStyle(color: accentColor, fontSize: 8.5, fontWeight: FontWeight.bold, letterSpacing: 0.5)),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: previewKeys.map((k) {
              return Expanded(
                child: Container(
                  height: keyHeight,
                  margin: const EdgeInsets.symmetric(horizontal: 2.5),
                  decoration: BoxDecoration(
                    color: keyBg,
                    borderRadius: BorderRadius.circular(radius),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.2),
                        blurRadius: 1,
                        offset: const Offset(0, 1),
                      ),
                    ],
                  ),
                  child: Stack(
                    children: [
                      if (_keyboardShowSubscripts)
                        Positioned(
                          top: 3,
                          right: 4,
                          child: Text(
                            k.$2,
                            style: TextStyle(
                              color: textSub,
                              fontSize: subFontSize,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                        ),
                      Center(
                        child: Text(
                          k.$1,
                          style: TextStyle(
                            color: textPrimary,
                            fontSize: fontSize,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: 6),
          Text(
            'Live Layout Preview • Size: ${_keyboardFontSize.toUpperCase()} • Height: ${_keyboardHeightScale.toUpperCase()}',
            style: TextStyle(fontSize: 10, color: textSub, fontWeight: FontWeight.w500),
          ),
        ],
      ),
    );
  }
}
