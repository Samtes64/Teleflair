import 'dart:convert';
import 'dart:typed_data';
import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:teleboard/features/delivery/domain/delivery_state.dart';
import 'package:teleboard/features/delivery/domain/sms_codec.dart';
import 'package:teleboard/features/text_art/application/text_art_converter.dart';

void main() {
  group('SMS Codec (TELEBOARD_V1)', () {
    test('calculateCrc32 computes deterministic CRC32 checksum', () {
      final sample = Uint8List.fromList(utf8.encode('Teleboard Test 12345'));
      final crc1 = SmsCodec.calculateCrc32(sample);
      final crc2 = SmsCodec.calculateCrc32(sample);
      expect(crc1, equals(crc2));
      expect(crc1.length, equals(8));
    });

    test('Round-trip encode and decode succeeds for arbitrary binary data', () {
      final data = Uint8List.fromList(List.generate(350, (i) => (i * 7) % 256));
      final messages = SmsCodec.encode(data, transferId: 'tx99', chunkSize: 64);

      expect(messages.length, greaterThan(1));
      for (final msg in messages) {
        expect(msg.startsWith('TB1:tx99:'), isTrue);
      }

      final decoded = SmsCodec.decode(messages);
      expect(decoded, equals(data));
    });

    test('Decode handles out-of-order received packets', () {
      final data = Uint8List.fromList(utf8.encode('Out of order message assembly verification'));
      final messages = SmsCodec.encode(data, transferId: 'txShuffle', chunkSize: 20);
      expect(messages.length, greaterThan(2));

      // Reverse message arrival order
      final shuffled = messages.reversed.toList();
      final decoded = SmsCodec.decode(shuffled);
      expect(decoded, equals(data));
    });

    test('Decode rejects incomplete packet sets', () {
      final data = Uint8List.fromList(utf8.encode('This payload is broken due to missing chunk'));
      final messages = SmsCodec.encode(data, transferId: 'txMissing', chunkSize: 16);
      expect(messages.length, greaterThan(2));

      // Drop middle packet
      final incomplete = [messages.first, messages.last];
      expect(
        () => SmsCodec.decode(incomplete),
        throwsA(isA<SmsCodecException>()),
      );
    });

    test('Decode rejects tampered checksum or corrupted payload', () {
      final data = Uint8List.fromList(utf8.encode('Secure Payload Integrity Check'));
      final messages = SmsCodec.encode(data, transferId: 'txCorrupt', chunkSize: 20);

      // Corrupt the base64 character of the first message
      final corrupted = List<String>.from(messages);
      final lastChar = corrupted[0].endsWith('A') ? 'B' : 'A';
      corrupted[0] = corrupted[0].substring(0, corrupted[0].length - 1) + lastChar;

      expect(
        () => SmsCodec.decode(corrupted),
        throwsA(isA<SmsCodecException>()),
      );
    });
  });

  group('Text-Art Converter', () {
    test('Generates silhouette text-art from generated image with alpha channel', () {
      // Create a 64x64 transparent image with a dark circle in the center
      final image = img.Image(width: 64, height: 64, numChannels: 4);
      img.fill(image, color: img.ColorRgba8(0, 0, 0, 0)); // transparent background

      // Draw solid filled circle in center
      img.fillCircle(image, x: 32, y: 32, radius: 20, color: img.ColorRgba8(20, 20, 20, 255));

      final textArt = TextArtConverter.convertImage(
        image,
        const TextArtOptions(targetWidth: 20, palette: TextArtPalette.ascii),
      );

      expect(textArt, isNotEmpty);
      expect(textArt.contains('@') || textArt.contains('%') || textArt.contains('#'), isTrue);
      // Verify lines are bounded
      final lines = textArt.split('\n');
      expect(lines.length, greaterThanOrEqualTo(4));
      for (final line in lines) {
        expect(line.length, lessThanOrEqualTo(20));
      }
    });

    test('Unicode block palette renders correctly', () {
      final image = img.Image(width: 32, height: 32, numChannels: 4);
      img.fill(image, color: img.ColorRgba8(10, 10, 10, 255));

      final textArt = TextArtConverter.convertImage(
        image,
        const TextArtOptions(targetWidth: 14, palette: TextArtPalette.unicodeBlocks),
      );

      expect(textArt, isNotEmpty);
      expect(textArt.contains('█') || textArt.contains('▓'), isTrue);
    });
  });

  group('Delivery State Machine', () {
    test('Default capabilities recommend direct rich content when confirmed', () {
      const caps = DeliveryCapabilities(richContentInsertion: CapabilityStatus.confirmed);
      expect(caps.recommendedTransport, equals(DeliveryTransport.richContent));
    });

    test('Fallback order cascades when rich content is unsupported', () {
      const caps = DeliveryCapabilities(
        richContentInsertion: CapabilityStatus.unsupported,
        shareSheet: CapabilityStatus.availableThroughHandoff,
      );
      expect(caps.recommendedTransport, equals(DeliveryTransport.intentShare));
    });

    test('DeliveryRecord serializes and deserializes consistently', () {
      final now = DateTime.now();
      final record = DeliveryRecord(
        id: 'rec_123',
        stickerId: 'stk_456',
        transport: DeliveryTransport.cloudflareLink,
        status: DeliveryStatus.sentToMessagingApp,
        targetPackage: 'com.google.android.apps.messaging',
        details: 'Sent via SMS link handoff',
        timestamp: now,
      );

      final map = record.toMap();
      final revived = DeliveryRecord.fromMap(map);

      expect(revived.id, equals(record.id));
      expect(revived.transport, equals(record.transport));
      expect(revived.status, equals(record.status));
      expect(revived.targetPackage, equals(record.targetPackage));
      expect(revived.details, equals(record.details));
    });
  });
}
