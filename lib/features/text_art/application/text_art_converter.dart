import 'dart:typed_data';
import 'package:image/image.dart' as img;

/// Palette style for text-art rendering.
enum TextArtPalette {
  /// Conservative ASCII characters rendering predictably in monospaced and proportional fonts.
  ascii,

  /// Unicode block characters (looks more solid, but requires Unicode SMS encoding).
  unicodeBlocks,

  /// High contrast minimal dots & hashes.
  minimal,
}

/// Options configuring the text-art conversion process.
class TextArtOptions {
  final int targetWidth;
  final int? targetHeight;
  final TextArtPalette palette;
  final int alphaThreshold;
  final bool invert;

  const TextArtOptions({
    this.targetWidth = 26,
    this.targetHeight,
    this.palette = TextArtPalette.ascii,
    this.alphaThreshold = 64,
    this.invert = false,
  });
}

/// Converts sticker bitmaps into recognizable silhouette text-art.
class TextArtConverter {
  // Ordered from darkest (densest) to lightest
  static const String _asciiPalette = '@%#*+=-:. ';
  static const String _unicodePalette = '█▓▒░ ';
  static const String _minimalPalette = '#*-. ';

  /// Converts image bytes (WebP, PNG, JPEG) into formatted text-art.
  static String convertBytes(Uint8List imageBytes, [TextArtOptions options = const TextArtOptions()]) {
    final image = img.decodeImage(imageBytes);
    if (image == null) {
      throw ArgumentError('Unable to decode image bytes for text-art conversion');
    }
    return convertImage(image, options);
  }

  /// Converts an `img.Image` into formatted text-art string.
  static String convertImage(img.Image original, [TextArtOptions options = const TextArtOptions()]) {
    final targetW = options.targetWidth;
    // Standard terminal/chat character aspect ratio is ~ 1:2 (tall characters)
    // So targetHeight defaults to (original.height / original.width) * targetW * 0.55
    final calculatedHeight = options.targetHeight ??
        ((original.height / original.width) * targetW * 0.55).round().clamp(4, 40);

    // Resize image with antialiasing
    final resized = img.copyResize(
      original,
      width: targetW,
      height: calculatedHeight,
      interpolation: img.Interpolation.linear,
    );

    final paletteStr = switch (options.palette) {
      TextArtPalette.ascii => _asciiPalette,
      TextArtPalette.unicodeBlocks => _unicodePalette,
      TextArtPalette.minimal => _minimalPalette,
    };

    final paletteLen = paletteStr.length;
    final lines = <String>[];

    for (var y = 0; y < resized.height; y++) {
      final lineBuffer = StringBuffer();

      for (var x = 0; x < resized.width; x++) {
        final pixel = resized.getPixel(x, y);

        // Alpha channel handling: transparent background becomes whitespace
        final alpha = pixel.a;
        if (alpha < options.alphaThreshold) {
          lineBuffer.write(' ');
          continue;
        }

        // Relative luminance calculation: 0.299 R + 0.587 G + 0.114 B
        final r = pixel.r;
        final g = pixel.g;
        final b = pixel.b;
        var lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;

        if (options.invert) {
          lum = 1.0 - lum;
        }

        // Map luminance [0.0, 1.0] to palette index (0 = dense, last = whitespace)
        final index = (lum * (paletteLen - 1)).clamp(0, paletteLen - 1).toInt();
        lineBuffer.write(paletteStr[index]);
      }

      // Trim trailing spaces to save SMS/message characters
      final trimmed = lineBuffer.toString().replaceAll(RegExp(r'\s+$'), '');
      if (trimmed.isNotEmpty || lines.isNotEmpty) {
        lines.add(trimmed);
      }
    }

    // Remove any trailing empty lines
    while (lines.isNotEmpty && lines.last.isEmpty) {
      lines.removeLast();
    }

    return lines.join('\n');
  }
}
