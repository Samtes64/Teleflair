import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:teleboard/app/theme/design_tokens.dart';

class AppGuideStep {
  final String badge;
  final String title;
  final String description;
  final IconData icon;
  final Color accentColor;

  const AppGuideStep({
    required this.badge,
    required this.title,
    required this.description,
    required this.icon,
    this.accentColor = const Color(0xFF5931FE),
  });
}

class AppGuideDialog extends StatefulWidget {
  final bool isReplay;

  const AppGuideDialog({super.key, this.isReplay = false});

  static Future<void> show(BuildContext context, {bool isReplay = false}) {
    return showDialog<void>(
      context: context,
      barrierDismissible: isReplay,
      builder: (ctx) => AppGuideDialog(isReplay: isReplay),
    );
  }

  @override
  State<AppGuideDialog> createState() => _AppGuideDialogState();
}

class _AppGuideDialogState extends State<AppGuideDialog> {
  final PageController _pageController = PageController();
  int _currentPage = 0;

  static const List<AppGuideStep> _steps = [
    AppGuideStep(
      badge: 'Step 1 of 4 • Keyboard Engine',
      title: 'Your Private Sticker Keyboard',
      description:
          'Teleflair gives you an ultra-fast keyboard with zero system emojis, pure vector aesthetics, and 100% offline privacy. Type and share expressive stickers across WhatsApp, Telegram, Instagram & SMS.',
      icon: Icons.keyboard_alt_outlined,
      accentColor: Color(0xFF5931FE),
    ),
    AppGuideStep(
      badge: 'Step 2 of 4 • Stickers Library',
      title: 'Stickers & Smart Text-Art',
      description:
          'The "Stickers" tab (bottom-left) is your library. Browse imported packs, search by emoji, bookmark favorites, or copy Unicode text-art silhouettes that work in plain SMS and comment sections.',
      icon: Icons.auto_awesome_motion_outlined,
      accentColor: Color(0xFF5931FE),
    ),
    AppGuideStep(
      badge: 'Step 3 of 4 • Center Plus (+)',
      title: 'Instant Telegram Importer',
      description:
          'Tap the elevated purple plus (+) button in the center anytime to import any public Telegram sticker set, or use our 1-tap featured starter shortcuts to fill your keyboard instantly.',
      icon: CupertinoIcons.plus,
      accentColor: Color(0xFF5931FE),
    ),
    AppGuideStep(
      badge: 'Step 4 of 4 • Settings Tab',
      title: 'Activation & Customization',
      description:
          'Head to Settings to enable the keyboard in Android Settings, customize key height, adjust key text size, choose key corner shapes, and check for OTA app updates seamlessly.',
      icon: CupertinoIcons.gear_alt,
      accentColor: Color(0xFF5931FE),
    ),
  ];

  Future<void> _completeGuide() async {
    final nav = Navigator.of(context, rootNavigator: true);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('first_install_guide_completed', true);
    if (mounted && nav.canPop()) {
      nav.pop();
    }
  }

  void _nextPage() {
    if (_currentPage < _steps.length - 1) {
      _pageController.nextPage(
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeInOut,
      );
    } else {
      _completeGuide();
    }
  }

  void _previousPage() {
    if (_currentPage > 0) {
      _pageController.previousPage(
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeInOut,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textColor = isDark ? AppColors.darkText : AppColors.lightText;
    final mutedColor = isDark ? AppColors.darkTextMuted : AppColors.lightTextMuted;
    final cardBg = isDark ? const Color(0xFF18181B) : Colors.white;

    return Dialog(
      backgroundColor: cardBg,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      insetPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 400, maxHeight: 540),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Top Bar: Skip button
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  // Brand indicator
                  Row(
                    children: [
                      Container(
                        width: 10,
                        height: 10,
                        decoration: const BoxDecoration(
                          color: Color(0xFF5931FE),
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        'Teleflair Tour',
                        style: TextStyle(
                          color: mutedColor,
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                          letterSpacing: 0.5,
                        ),
                      ),
                    ],
                  ),
                  TextButton(
                    onPressed: _completeGuide,
                    style: TextButton.styleFrom(
                      foregroundColor: mutedColor,
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      minimumSize: Size.zero,
                      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                    ),
                    child: const Text(
                      'Skip Tour',
                      style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),

              // PageView Content
              Expanded(
                child: PageView.builder(
                  controller: _pageController,
                  itemCount: _steps.length,
                  onPageChanged: (index) => setState(() => _currentPage = index),
                  itemBuilder: (context, index) {
                    final step = _steps[index];
                    return Center(
                      child: SingleChildScrollView(
                        physics: const BouncingScrollPhysics(),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            // Animated Hero Icon
                            Container(
                              width: 72,
                              height: 72,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: step.accentColor.withValues(alpha: isDark ? 0.2 : 0.1),
                                border: Border.all(
                                  color: step.accentColor.withValues(alpha: isDark ? 0.4 : 0.25),
                                  width: 1.5,
                                ),
                                boxShadow: [
                                  BoxShadow(
                                    color: step.accentColor.withValues(alpha: 0.15),
                                    blurRadius: 16,
                                    spreadRadius: 2,
                                  ),
                                ],
                              ),
                              child: Center(
                                child: Icon(
                                  step.icon,
                                  size: 34,
                                  color: step.accentColor,
                                ),
                              ),
                            ),
                            const SizedBox(height: 16),

                            // Step Badge
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
                              decoration: BoxDecoration(
                                color: isDark ? const Color(0xFF27272A) : const Color(0xFFF4F4F5),
                                borderRadius: BorderRadius.circular(AppRadius.pill),
                              ),
                              child: Text(
                                step.badge,
                                style: const TextStyle(
                                  color: Color(0xFF5931FE),
                                  fontSize: 11,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                            ),
                            const SizedBox(height: 10),

                            // Title
                            Text(
                              step.title,
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                color: textColor,
                                fontSize: 17,
                                fontWeight: FontWeight.w700,
                                letterSpacing: -0.3,
                              ),
                            ),
                            const SizedBox(height: 8),

                            // Description
                            Padding(
                              padding: const EdgeInsets.symmetric(horizontal: 10.0),
                              child: Text(
                                step.description,
                                textAlign: TextAlign.center,
                                style: TextStyle(
                                  color: mutedColor,
                                  fontSize: 12.5,
                                  height: 1.4,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
              ),

              const SizedBox(height: 16),

              // Page Indicator Dots
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(
                  _steps.length,
                  (index) => AnimatedContainer(
                    duration: const Duration(milliseconds: 250),
                    margin: const EdgeInsets.symmetric(horizontal: 4),
                    width: _currentPage == index ? 22 : 7,
                    height: 7,
                    decoration: BoxDecoration(
                      color: _currentPage == index
                          ? const Color(0xFF5931FE)
                          : (isDark ? const Color(0xFF3F3F46) : const Color(0xFFD4D4D8)),
                      borderRadius: BorderRadius.circular(4),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 20),

              // Navigation Buttons Row
              Row(
                children: [
                  if (_currentPage > 0) ...[
                    OutlinedButton(
                      style: OutlinedButton.styleFrom(
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.pill)),
                        side: BorderSide(color: isDark ? AppColors.darkBorder : AppColors.lightBorder),
                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                      ),
                      onPressed: _previousPage,
                      child: Text('Back', style: TextStyle(color: textColor, fontSize: 13)),
                    ),
                    const SizedBox(width: 10),
                  ],
                  Expanded(
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF5931FE),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 13),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.pill)),
                        elevation: 0,
                      ),
                      onPressed: _nextPage,
                      child: Text(
                        _currentPage == _steps.length - 1 ? 'Start Using Teleflair' : 'Next Step →',
                        style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
