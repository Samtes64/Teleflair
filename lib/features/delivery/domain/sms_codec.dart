import 'dart:convert';
import 'dart:typed_data';

/// Exception thrown when SMS encoding or decoding encounters invalid data.
class SmsCodecException implements Exception {
  final String message;
  const SmsCodecException(this.message);

  @override
  String toString() => 'SmsCodecException: $message';
}

/// A parsed SMS packet part.
class SmsPacketPart {
  final String version;
  final String transferId;
  final int partIndex;
  final int totalParts;
  final String checksum;
  final String payload;

  const SmsPacketPart({
    required this.version,
    required this.transferId,
    required this.partIndex,
    required this.totalParts,
    required this.checksum,
    required this.payload,
  });

  /// Serializes into the wire format: `TB1:<id>:<idx>:<total>:<crc>:<payload>`
  String toWireFormat() =>
      '$version:$transferId:$partIndex:$totalParts:$checksum:$payload';

  /// Parses an incoming SMS text. Returns null if not a Teleboard packet.
  static SmsPacketPart? tryParse(String text) {
    if (!text.startsWith('TB1:')) return null;
    final parts = text.split(':');
    if (parts.length < 6) return null;

    final version = parts[0];
    final transferId = parts[1];
    final partIndex = int.tryParse(parts[2]);
    final totalParts = int.tryParse(parts[3]);
    final checksum = parts[4];
    // Rejoin any remaining parts in case payload contained colons (though base64url doesn't)
    final payload = parts.sublist(5).join(':');

    if (partIndex == null || totalParts == null || partIndex < 1 || partIndex > totalParts) {
      return null;
    }

    return SmsPacketPart(
      version: version,
      transferId: transferId,
      partIndex: partIndex,
      totalParts: totalParts,
      checksum: checksum,
      payload: payload,
    );
  }
}

/// Codec for transmitting binary sticker data over concatenated SMS messages.
class SmsCodec {
  static const String protocolVersion = 'TB1';
  static const int defaultChunkSize = 120; // safe payload size for 160 GSM-7 / Unicode limits

  /// Computes standard CRC32 checksum formatted as 8-character hex string.
  static String calculateCrc32(Uint8List bytes) {
    var crc = 0xFFFFFFFF;
    for (var i = 0; i < bytes.length; i++) {
      final byte = bytes[i];
      crc ^= byte;
      for (var j = 0; j < 8; j++) {
        final mask = -(crc & 1);
        crc = (crc >> 1) ^ (0xEDB88320 & mask);
      }
    }
    final result = ~crc & 0xFFFFFFFF;
    return result.toRadixString(16).padLeft(8, '0').toUpperCase();
  }

  /// Encodes raw image bytes into a sequence of SMS text segments.
  static List<String> encode(
    Uint8List data, {
    String? transferId,
    int chunkSize = defaultChunkSize,
  }) {
    if (data.isEmpty) {
      throw const SmsCodecException('Cannot encode empty payload');
    }
    if (chunkSize <= 0) {
      throw const SmsCodecException('Chunk size must be positive');
    }

    final id = transferId ?? DateTime.now().millisecondsSinceEpoch.toRadixString(36);
    final checksum = calculateCrc32(data);
    final base64Payload = base64UrlEncode(data);

    final chunks = <String>[];
    for (var i = 0; i < base64Payload.length; i += chunkSize) {
      final end = (i + chunkSize < base64Payload.length) ? i + chunkSize : base64Payload.length;
      chunks.add(base64Payload.substring(i, end));
    }

    final totalParts = chunks.length;
    final messages = <String>[];

    for (var i = 0; i < totalParts; i++) {
      final part = SmsPacketPart(
        version: protocolVersion,
        transferId: id,
        partIndex: i + 1,
        totalParts: totalParts,
        checksum: checksum,
        payload: chunks[i],
      );
      messages.add(part.toWireFormat());
    }

    return messages;
  }

  /// Decodes a set of SMS text segments back into the original raw bytes.
  static Uint8List decode(List<String> messages) {
    if (messages.isEmpty) {
      throw const SmsCodecException('No messages to decode');
    }

    final parsedParts = <SmsPacketPart>[];
    for (final msg in messages) {
      final part = SmsPacketPart.tryParse(msg);
      if (part == null) {
        throw SmsCodecException('Malformed or unsupported message format: $msg');
      }
      parsedParts.add(part);
    }

    final transferId = parsedParts.first.transferId;
    final totalParts = parsedParts.first.totalParts;
    final expectedChecksum = parsedParts.first.checksum;

    // Verify all parts belong to the same transfer
    final partsMap = <int, SmsPacketPart>{};
    for (final part in parsedParts) {
      if (part.transferId != transferId) {
        throw SmsCodecException(
          'Mismatched transfer IDs: expected $transferId, got ${part.transferId}',
        );
      }
      if (part.totalParts != totalParts) {
        throw SmsCodecException(
          'Mismatched total parts count in transfer $transferId',
        );
      }
      if (part.checksum != expectedChecksum) {
        throw SmsCodecException(
          'Mismatched checksums in transfer $transferId',
        );
      }
      partsMap[part.partIndex] = part;
    }

    // Check completeness
    for (var i = 1; i <= totalParts; i++) {
      if (!partsMap.containsKey(i)) {
        throw SmsCodecException('Missing packet part $i of $totalParts for transfer $transferId');
      }
    }

    // Assemble payload in sequential order
    final buffer = StringBuffer();
    for (var i = 1; i <= totalParts; i++) {
      buffer.write(partsMap[i]!.payload);
    }

    final fullBase64 = buffer.toString();
    Uint8List decodedBytes;
    try {
      decodedBytes = base64Url.decode(fullBase64);
    } catch (e) {
      throw SmsCodecException('Base64url decoding failed: $e');
    }

    // Verify CRC32
    final calculatedChecksum = calculateCrc32(decodedBytes);
    if (calculatedChecksum != expectedChecksum) {
      throw SmsCodecException(
        'Checksum verification failed! Expected $expectedChecksum, computed $calculatedChecksum',
      );
    }

    return decodedBytes;
  }
}
