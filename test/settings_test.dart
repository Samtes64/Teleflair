import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:teleboard/app/app.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;

  testWidgets('Settings screen renders without overflow on 360 width', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    SharedPreferences.setMockInitialValues({
      'is_dark_mode': false,
      'keyboard_font_size': 'medium',
      'keyboard_height_scale': 'standard',
      'keyboard_key_shape': 'rounded',
    });

    await tester.pumpWidget(const TeleboardApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    // Tap Settings tab
    final settingsNav = find.text('Settings');
    expect(settingsNav, findsWidgets);
    await tester.tap(settingsNav.first);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    // Verify Settings Screen is loaded
    expect(find.text('Keyboard Setup'), findsOneWidget);

    // Scroll through the whole settings screen
    await tester.drag(find.byType(ListView), const Offset(0, -300));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    expect(find.text('Keyboard Experience & Customization'), findsOneWidget);

    await tester.drag(find.byType(ListView), const Offset(0, -400));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
  });
}
