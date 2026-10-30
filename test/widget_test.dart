import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:teleboard/app/app.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;

  testWidgets('TeleboardApp renders navigation tabs smoke test in default light theme', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});

    await tester.pumpWidget(const TeleboardApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.text('Stickers'), findsWidgets);
    expect(find.byIcon(CupertinoIcons.plus), findsOneWidget);
    expect(find.text('Settings'), findsWidgets);
    expect(find.text('Delivery'), findsNothing);
  });

  testWidgets('First time app installation displays keyboard activation onboarding banner', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});

    await tester.pumpWidget(const TeleboardApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.text('Activate Teleflair Keyboard'), findsOneWidget);
    expect(find.text('1. Enable Teleflair in Settings'), findsOneWidget);
    expect(find.text('2. Select Teleflair Keyboard'), findsOneWidget);
  });
}
